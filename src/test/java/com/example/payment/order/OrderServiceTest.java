package com.example.payment.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.payment.api.error.ApiException;
import com.example.payment.payment.PaymentRepository;
import com.example.payment.product.Product;
import com.example.payment.product.ProductCatalog;
import com.example.payment.product.ProductRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderServiceTest {
    private OrderService service;

    @BeforeEach
    void setUp() {
        OrderRepository orders = mock(OrderRepository.class);
        when(orders.save(any(PurchaseOrder.class))).thenAnswer(call -> call.getArgument(0));
        ProductRepository products = mock(ProductRepository.class);
        when(products.findByIdAndActiveTrue("tee-01")).thenReturn(Optional.of(new Product(
                "tee-01", "선데이 크루 티", "IVORY / REGULAR", "베이식", 19_000,
                "#fff5de", "#f9e9d9", "sun", "BEST", 1)));
        when(products.findByIdAndActiveTrue("tee-04")).thenReturn(Optional.of(new Product(
                "tee-04", "블루 스타 티", "BLUE / BOXY", "그래픽", 27_000,
                "#4e78d7", "#dbe9fa", "star", "", 4)));
        service = new OrderService(orders, mock(PaymentRepository.class), new ProductCatalog(products));
    }

    @Test
    void cartTotalComesFromCatalogAndKeepsProductOptions() {
        OrderResponse order = service.create(new CreateOrderRequest(List.of(
                new CreateOrderRequest.Item("tee-01", "M", 2),
                new CreateOrderRequest.Item("tee-04", "L", 1))));

        assertThat(order.amount()).isEqualTo(65_000);
        assertThat(order.quantity()).isEqualTo(3);
        assertThat(order.items()).extracting(OrderResponse.ItemResponse::size).containsExactly("M", "L");
        assertThat(order.items()).extracting(OrderResponse.ItemResponse::unitPrice).containsExactly(19_000L, 27_000L);
    }

    @Test
    void duplicateOptionAndOutOfRangeQuantityAreRejected() {
        assertThatThrownBy(() -> service.create(new CreateOrderRequest(List.of(
                new CreateOrderRequest.Item("tee-01", "M", 1),
                new CreateOrderRequest.Item("tee-01", "M", 1)))))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_CART");
        assertThatThrownBy(() -> service.create(new CreateOrderRequest(List.of(
                new CreateOrderRequest.Item("tee-01", "M", 11)))))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_CART");
    }
}
