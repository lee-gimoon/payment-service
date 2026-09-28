package com.example.payment.payment.api;

import com.example.payment.order.OrderResponse;
import com.example.payment.payment.application.PaymentService;
import com.example.payment.payment.infrastructure.toss.TossProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Payments")
public class PaymentController {
    private final PaymentService paymentService;
    private final TossProperties tossProperties;

    public PaymentController(PaymentService paymentService, TossProperties tossProperties) {
        this.paymentService = paymentService;
        this.tossProperties = tossProperties;
    }

    @GetMapping("/payment-config")
    @Operation(summary = "브라우저용 결제 설정")
    public PublicConfig config() {
        return new PublicConfig(tossProperties.configured(), tossProperties.clientKey(),
                tossProperties.paymentMethodVariantKey(), tossProperties.agreementVariantKey());
    }

    @PostMapping("/payments/confirm")
    @Operation(summary = "결제수단 인증 후 결제 승인")
    public ResponseEntity<OrderResponse> confirm(@Valid @RequestBody ConfirmPaymentRequest request) {
        OrderResponse order = paymentService.confirm(request);
        return response(order);
    }

    private ResponseEntity<OrderResponse> response(OrderResponse order) {
        return switch (order.payment().status()) {
            case APPROVING, UNKNOWN, REVIEW_REQUIRED -> ResponseEntity.accepted().body(order);
            case FAILED -> ResponseEntity.unprocessableContent().body(order);
            default -> ResponseEntity.ok(order);
        };
    }

    public record PublicConfig(boolean enabled, String clientKey,
                               String paymentMethodVariantKey, String agreementVariantKey) {}
}
