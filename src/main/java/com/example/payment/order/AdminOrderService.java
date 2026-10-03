package com.example.payment.order;

import com.example.payment.api.error.ApiException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 주문 관리. 결제 완료 주문에 송장을 등록해 배송 중으로, 이어서 배송 완료로 바꾼다.
 * 배송 쓰기도 결제처럼 주문 행을 먼저 잠가 같은 주문의 요청을 한 줄로 처리한다.
 */
@Service
public class AdminOrderService {
    static final int LIST_LIMIT = 100;

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final ShipmentRepository shipments;

    public AdminOrderService(OrderRepository orders, OrderItemRepository orderItems, ShipmentRepository shipments) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.shipments = shipments;
    }

    /**
     * 결제 완료 주문 목록. 상품 준비 중은 먼저 결제된 주문부터, 나머지는 최근 순서로 최대 100건이다.
     *
     * @param delivery null이면 결제 완료 주문 전체
     */
    @Transactional(readOnly = true)
    public List<AdminOrderSummary> list(DeliveryStatus delivery) {
        Pageable first = PageRequest.of(0, LIST_LIMIT);
        List<PurchaseOrder> found = delivery == null ? orders.findPaid(first)
                : delivery == DeliveryStatus.PREPARING ? orders.findPaidAwaitingShipment(first)
                : orders.findByShipmentStatus(ShipmentStatus.valueOf(delivery.name()), first);
        Map<String, Shipment> byOrder = shipments.findByOrderIdIn(found.stream().map(PurchaseOrder::getId).toList())
                .stream().collect(Collectors.toMap(Shipment::getOrderId, Function.identity()));
        return found.stream().map(order -> AdminOrderSummary.of(order, byOrder.get(order.getId()))).toList();
    }

    /** 관리자 홈에 보여줄 처리할 일의 수. */
    @Transactional(readOnly = true)
    public AdminOrderCounts counts() {
        return new AdminOrderCounts(orders.countPaidAwaitingShipment(), shipments.countByStatus(ShipmentStatus.SHIPPED));
    }

    @Transactional(readOnly = true)
    public AdminOrderResponse get(String orderId) {
        PurchaseOrder order = orders.findById(orderId).orElseThrow(AdminOrderService::orderNotFound);
        return detail(order, shipments.findByOrderId(orderId).orElse(null));
    }

    /** 송장을 등록해 배송 중으로 바꾼다. 배송 중이면 잘못 넣은 송장을 고친다. */
    @Transactional
    public AdminOrderResponse ship(String orderId, ShipRequest request) {
        PurchaseOrder order = orders.findByIdForUpdate(orderId).orElseThrow(AdminOrderService::orderNotFound);
        if (order.getStatus() != OrderStatus.PAID) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_PAID", "결제가 완료된 주문만 배송할 수 있습니다.");
        }
        if (order.getShipping() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "SHIPPING_ADDRESS_REQUIRED",
                    "배송지 없이 만든 이전 주문이라 송장을 등록할 수 없습니다.");
        }
        Shipment shipment = shipments.findByOrderId(orderId).orElse(null);
        if (shipment == null) {
            shipment = shipments.save(new Shipment(orderId, request.carrier(), request.trackingNumber()));
        } else if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
            throw new ApiException(HttpStatus.CONFLICT, "DELIVERY_COMPLETED", "배송 완료된 주문은 송장을 바꿀 수 없습니다.");
        } else {
            shipment.correct(request.carrier(), request.trackingNumber());
        }
        return detail(order, shipment);
    }

    /** 이미 배송 완료면 그대로 돌려준다. */
    @Transactional
    public AdminOrderResponse markDelivered(String orderId) {
        PurchaseOrder order = orders.findByIdForUpdate(orderId).orElseThrow(AdminOrderService::orderNotFound);
        Shipment shipment = shipments.findByOrderId(orderId).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                "SHIPMENT_NOT_REGISTERED", "송장을 등록한 뒤에 배송 완료로 바꿀 수 있습니다."));
        shipment.markDelivered();
        return detail(order, shipment);
    }

    private AdminOrderResponse detail(PurchaseOrder order, Shipment shipment) {
        return AdminOrderResponse.of(order, orderItems.findByOrderId(order.getId()), shipment);
    }

    private static ApiException orderNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다.");
    }
}
