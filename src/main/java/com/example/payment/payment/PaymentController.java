/* 파일 역할: 공개 결제 설정, 최종 승인, 결과 재확인을 위한 HTTP API의 진입점을 제공한다. */
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

/** 결제 요청을 검증하여 서비스에 전달하고, 처리 결과에 맞는 HTTP 상태와 주문 응답을 반환한다. */
@RestController
@Tag(name = "Payments", description = "결제 설정, 승인 및 결과 재확인")
public class PaymentController {
    private final PaymentService payments;
    private final TossProperties properties;

    /** 결제 서비스와 공개 설정 응답에 필요한 토스 설정을 주입받는다. */
    public PaymentController(PaymentService payments, TossProperties properties) {
        this.payments = payments;
        this.properties = properties;
    }

    /** GET /payment-config: 브라우저에 결제 사용 여부와 클라이언트 키만 공개한다. */
    @GetMapping("/payment-config")
    @Operation(summary = "브라우저용 결제 설정 조회", description = "결제 가능 여부와 클라이언트 키만 반환하며 시크릿 키는 노출하지 않습니다.")
    PublicConfig config() {
        return new PublicConfig(properties.configured(), properties.clientKey());
    }

    /** POST /payments/confirm: JSON 입력 검증 후 서버의 주문 확인·PG 승인·결과 저장 흐름을 실행한다. */
    @PostMapping("/payments/confirm")
    @Operation(summary = "결제 승인", description = "결제창 인증 결과를 검증하고 PG사에 결제 승인을 요청한 뒤 결과를 저장합니다.")
    ResponseEntity<OrderResponse> confirm(@Valid @RequestBody ConfirmPaymentRequest request) {
        return response(payments.confirm(request));
    }

    /** POST /payments/{orderId}/reconcile: 결과 확인이 필요한 기존 결제를 PG 조회로 재확인한다. */
    @PostMapping("/payments/{orderId}/reconcile")
    @Operation(summary = "결제 결과 재확인", description = "결과가 불명확하거나 처리 제한 시간이 지난 결제를 PG사에서 다시 조회해 저장합니다.")
    ResponseEntity<OrderResponse> reconcile(@PathVariable @Pattern(regexp = "[a-zA-Z0-9_-]{6,64}") String orderId) {
        return response(payments.reconcile(orderId));
    }

    /** 처리 중·미확정은 202, 확정 실패는 422, 나머지는 200으로 주문 응답을 감싼다. */
    private ResponseEntity<OrderResponse> response(OrderResponse order) {
        return switch (order.payment().status()) {
            case PROCESSING, UNKNOWN -> ResponseEntity.accepted().body(order);
            case FAILED -> ResponseEntity.unprocessableContent().body(order);
            default -> ResponseEntity.ok(order);
        };
    }

    /** 브라우저에 전달할 수 있는 결제 설정만 담는 DTO로 서버 시크릿 키는 제외한다. */
    record PublicConfig(boolean enabled, String clientKey) {}
}
