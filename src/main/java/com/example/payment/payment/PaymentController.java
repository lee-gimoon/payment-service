package com.example.payment.payment;

import com.example.payment.config.TossProperties;
import com.example.payment.order.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Payments", description = "결제 설정, 승인 및 결과 재확인")
public class PaymentController {
    private final PaymentService payments;
    private final TossProperties properties;

    public PaymentController(PaymentService payments, TossProperties properties) {
        this.payments = payments;
        this.properties = properties;
    }

    @GetMapping("/payment-config")
    @Operation(summary = "브라우저용 결제 설정 조회", description = "결제 가능 여부와 클라이언트 키만 반환하며 시크릿 키는 노출하지 않습니다.")
    PublicConfig config() {
        return new PublicConfig(properties.configured(), properties.clientKey());
    }

    @PostMapping("/payments/confirm")
    @Operation(summary = "결제 승인", description = "결제창 인증 결과를 검증하고 PG사에 결제 승인을 요청한 뒤 결과를 저장합니다.")
    ResponseEntity<OrderResponse> confirm(@Valid @RequestBody ConfirmPaymentRequest request) {
        return response(payments.confirm(request));
    }

    @PostMapping("/payments/{orderId}/reconcile")
    @Operation(summary = "결제 결과 재확인", description = "결과가 불명확하거나 처리 제한 시간이 지난 결제를 PG사에서 다시 조회해 저장합니다.")
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
