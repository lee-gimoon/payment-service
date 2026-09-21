package com.example.payment.payment;

import com.example.payment.config.TossProperties;
import com.example.payment.order.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 브라우저가 보낸 결제수단 인증 결과를 받고 PaymentService에 최종 승인을 요청한다. */
@RestController
@Tag(name = "Payments")
public class PaymentController {
    private final PaymentService paymentService;
    private final TossProperties tossProperties;

    public PaymentController(PaymentService paymentService, TossProperties tossProperties) {
        this.paymentService = paymentService;
        this.tossProperties = tossProperties;
    }

    /** 브라우저는 clientKey로 토스 인증창을 연다. 서버의 secretKey는 응답에 넣지 않는다. */
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

    /** 기존 React의 PG 결과 재확인 버튼에서 사용하는 보조 API다. */
    @PostMapping("/payments/{orderId}/reconcile")
    @Operation(summary = "토스에서 결제 결과 재확인")
    public ResponseEntity<OrderResponse> reconcile(@PathVariable String orderId) {
        OrderResponse order = paymentService.reconcile(orderId);
        return response(order);
    }

    /** 프론트가 사용하는 HTTP 규칙: 성공 200, 처리 중·미확정 202, 결제 거절 422. */
    private ResponseEntity<OrderResponse> response(OrderResponse order) {
        return switch (order.payment().status()) {
            case PROCESSING, UNKNOWN -> ResponseEntity.accepted().body(order);
            case FAILED -> ResponseEntity.unprocessableContent().body(order);
            default -> ResponseEntity.ok(order);
        };
    }

    public record PublicConfig(boolean enabled, String clientKey,
                               String paymentMethodVariantKey, String agreementVariantKey) {}
}
