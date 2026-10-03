package com.example.payment.order.application;

import com.example.payment.api.error.ApiException;
import com.example.payment.customer.AddressResponse;
import com.example.payment.customer.CustomerAddressService;
import com.example.payment.order.api.CreateOrderRequest;
import com.example.payment.order.api.OrderResponse;
import com.example.payment.order.api.OrderSummaryResponse;
import com.example.payment.order.domain.OrderItem;
import com.example.payment.order.domain.PurchaseOrder;
import com.example.payment.order.domain.Shipment;
import com.example.payment.order.domain.ShippingAddress;
import com.example.payment.order.persistence.OrderItemRepository;
import com.example.payment.order.persistence.OrderRepository;
import com.example.payment.order.persistence.ShipmentRepository;
import com.example.payment.payment.persistence.PaymentAttemptRepository;
import com.example.payment.product.Product;
import com.example.payment.product.ProductCatalog;
import com.example.payment.product.ProductInventory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ProductCatalog productCatalog;
    private final ProductInventory inventory;
    private final CustomerAddressService customerAddresses;
    private final ShipmentRepository shipments;

    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                        PaymentAttemptRepository paymentAttemptRepository,
                        ProductCatalog productCatalog, ProductInventory inventory,
                        CustomerAddressService customerAddresses, ShipmentRepository shipments) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.productCatalog = productCatalog;
        this.inventory = inventory;
        this.customerAddresses = customerAddresses;
        this.shipments = shipments;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, String customerId) {
        if (request == null || request.items() == null || request.items().isEmpty() || request.items().size() > 20) {
            throw invalidCart();
        }
        List<Product> products = new ArrayList<>();
        List<ProductInventory.Line> stockLines = new ArrayList<>();
        Set<String> selectedOptions = new HashSet<>();
        Set<String> productIds = new HashSet<>();
        int totalQuantity = 0;
        long totalAmount = 0;
        for (CreateOrderRequest.Item item : request.items()) {
            if (item == null || item.productId() == null || item.size() == null
                    || !Product.SIZES.contains(item.size())
                    || item.quantity() < 1 || item.quantity() > 10
                    || !selectedOptions.add(item.productId() + ":" + item.size())) {
                throw invalidCart();
            }
            Product product = productCatalog.forOrder(item.productId());
            totalQuantity += item.quantity();
            if (totalQuantity > 100) {
                throw invalidCart();
            }
            totalAmount = Math.addExact(totalAmount, Math.multiplyExact(product.getPrice(), item.quantity()));
            products.add(product);
            productIds.add(product.getId());
            stockLines.add(new ProductInventory.Line(product.getId(), product.getName(), item.size(), item.quantity()));
        }
        ShippingAddress shipping = shippingAddress(request, customerId);
        // 재고는 승인 직전에 가져간다. 여기서는 품절 상품으로 주문을 만들지 않도록 확인만 한다.
        inventory.requireAvailable(stockLines);
        PurchaseOrder order = orderRepository.save(new PurchaseOrder(customerId,
                products.getFirst().getName(), productIds.size(), totalQuantity, totalAmount, shipping));
        List<OrderItem> lines = new ArrayList<>();
        for (int lineNumber = 0; lineNumber < request.items().size(); lineNumber++) {
            CreateOrderRequest.Item item = request.items().get(lineNumber);
            lines.add(new OrderItem(order, products.get(lineNumber), item.size(), item.quantity(), lineNumber));
        }
        orderItemRepository.saveAll(lines);
        return OrderResponse.of(order, lines, null, null, null);
    }

    /** 내 배송지를 주문에 복사한다. 이후 마이페이지에서 배송지를 고쳐도 이 주문의 배송지는 그대로다. */
    private ShippingAddress shippingAddress(CreateOrderRequest request, String customerId) {
        if (request.addressId() == null || request.addressId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADDRESS_REQUIRED", "배송지를 골라주세요.");
        }
        String memo = request.deliveryMemo() == null || request.deliveryMemo().isBlank()
                ? null : request.deliveryMemo().strip();
        if (memo != null && memo.length() > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "배송 메모는 50자까지 쓸 수 있습니다.");
        }
        AddressResponse address = customerAddresses.forOrder(customerId, request.addressId());
        return new ShippingAddress(address.recipientName(), address.phone(), address.postalCode(),
                address.address(), address.addressDetail(), memo);
    }

    private ApiException invalidCart() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CART", "상품과 사이즈, 수량을 확인해주세요.");
    }

    /** 마이페이지 주문 내역. 최근 주문부터 최대 50건이다. */
    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> listMine(String customerId) {
        List<PurchaseOrder> mine = orderRepository.findTop50ByCustomerIdOrderByCreatedAtDesc(customerId);
        Map<String, Shipment> byOrder = shipments.findByOrderIdIn(mine.stream().map(PurchaseOrder::getId).toList())
                .stream().collect(Collectors.toMap(Shipment::getOrderId, Function.identity()));
        return mine.stream().map(order -> OrderSummaryResponse.of(order, byOrder.get(order.getId()))).toList();
    }

    /** 다른 회원의 주문은 존재 여부를 알리지 않고 없는 주문처럼 응답한다. */
    @Transactional(readOnly = true)
    public OrderResponse get(String orderId, String customerId) {
        PurchaseOrder order = orderRepository.findById(orderId)
                .filter(found -> found.isOwnedBy(customerId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
        return OrderResponse.of(order, orderItemRepository.findByOrderId(orderId),
                paymentAttemptRepository.findFirstByOrderIdAndApprovalRequestedAtNotNullOrderByApprovalRequestedAtDesc(orderId)
                        .orElse(null),
                paymentAttemptRepository.findFirstByOrderIdOrderByStartedAtDesc(orderId).orElse(null),
                shipments.findByOrderId(orderId).orElse(null));
    }
}
