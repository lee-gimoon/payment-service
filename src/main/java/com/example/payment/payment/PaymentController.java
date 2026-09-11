package com.example.payment.payment;

import com.example.payment.config.TossProperties;
import com.example.payment.order.OrderResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {
    private final PaymentService payments;
    private final TossProperties properties;

    public PaymentController(PaymentService payments, TossProperties properties) {
        this.payments = payments;
        this.properties = properties;
    }

    @GetMapping("/payment-config")
    PublicConfig config() {
        return new PublicConfig(properties.configured(), properties.clientKey());
    }

    @PostMapping("/payments/confirm")
    ResponseEntity<OrderResponse> confirm(@Valid @RequestBody ConfirmPaymentRequest request) {
        return response(payments.confirm(request));
    }

    @PostMapping("/payments/{orderId}/reconcile")
    ResponseEntity<OrderResponse> reconcile(@PathVariable @Pattern(regexp = "[a-zA-Z0-9_-]{6,64}") String orderId) {
        return response(payments.reconcile(orderId));
    }

    private ResponseEntity<OrderResponse> response(OrderResponse order) {
        return switch (order.payment().status()) {
            case PROCESSING, UNKNOWN -> ResponseEntity.accepted().body(order);
            case FAILED -> ResponseEntity.unprocessableContent().body(order);
            default -> ResponseEntity.ok(order);
        };
    }

    record PublicConfig(boolean enabled, String clientKey) {}
}
