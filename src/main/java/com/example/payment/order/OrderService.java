package com.example.payment.order;

import com.example.payment.api.error.ApiException;
import com.example.payment.payment.Payment;
import com.example.payment.payment.PaymentRepository;
import com.example.payment.product.Product;
import com.example.payment.product.ProductCatalog;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 주문 생성과 결과 조회를 담당한다. 결제 승인은 PaymentService가 담당한다. */
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductCatalog productCatalog;

    public OrderService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                        ProductCatalog productCatalog) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.productCatalog = productCatalog;
    }

    /** 서버 상품 가격으로 장바구니 금액을 확정하고 옵션·수량을 함께 저장한다. */
    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty() || request.items().size() > 20) {
            throw invalidCart();
        }
        List<OrderItem> lines = new ArrayList<>();
        Set<String> selectedOptions = new HashSet<>();
        int totalQuantity = 0;
        for (CreateOrderRequest.Item item : request.items()) {
            if (item == null || item.productId() == null || item.size() == null
                    || !Set.of("S", "M", "L", "XL").contains(item.size())
                    || item.quantity() < 1 || item.quantity() > 10
                    || !selectedOptions.add(item.productId() + ":" + item.size())) {
                throw invalidCart();
            }
            Product product = productCatalog.forOrder(item.productId());
            totalQuantity += item.quantity();
            if (totalQuantity > 100) {
                throw invalidCart();
            }
            lines.add(new OrderItem(product, item.size(), item.quantity()));
        }
        PurchaseOrder order = orderRepository.save(new PurchaseOrder(lines));
        return OrderResponse.of(order, null);
    }

    private ApiException invalidCart() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CART", "상품과 사이즈, 수량을 확인해주세요.");
    }

    /** 주문과 연결된 결제를 조회하여 프론트에 함께 전달한다. */
    @Transactional(readOnly = true)
    public OrderResponse get(String orderId) {
        PurchaseOrder order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."));
        Payment payment = paymentRepository.findById(orderId).orElse(null);
        return OrderResponse.of(order, payment);
    }
}
