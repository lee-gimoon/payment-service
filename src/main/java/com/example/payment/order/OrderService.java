package com.example.payment.order;

import com.example.payment.api.error.ApiException;
import com.example.payment.payment.persistence.PaymentAttemptRepository;
import com.example.payment.product.Product;
import com.example.payment.product.ProductCatalog;
import com.example.payment.product.ProductInventory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                        PaymentAttemptRepository paymentAttemptRepository,
                        ProductCatalog productCatalog, ProductInventory inventory) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.productCatalog = productCatalog;
        this.inventory = inventory;
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
        // 재고는 승인 직전에 가져간다. 여기서는 품절 상품으로 주문을 만들지 않도록 확인만 한다.
        inventory.requireAvailable(stockLines);
        PurchaseOrder order = orderRepository.save(new PurchaseOrder(customerId,
                products.getFirst().getName(), productIds.size(), totalQuantity, totalAmount));
        List<OrderItem> lines = new ArrayList<>();
        for (int lineNumber = 0; lineNumber < request.items().size(); lineNumber++) {
            CreateOrderRequest.Item item = request.items().get(lineNumber);
            lines.add(new OrderItem(order, products.get(lineNumber), item.size(), item.quantity(), lineNumber));
        }
        orderItemRepository.saveAll(lines);
        return OrderResponse.of(order, lines, null, null);
    }

    private ApiException invalidCart() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CART", "상품과 사이즈, 수량을 확인해주세요.");
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
                paymentAttemptRepository.findFirstByOrderIdOrderByStartedAtDesc(orderId).orElse(null));
    }
}
