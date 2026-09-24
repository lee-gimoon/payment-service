/* 파일 역할: POST /payments/confirm의 JSON 요청 형식과 입력값 검증 조건을 정의한다. */
package com.example.payment.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 결제수단 인증 후 클라이언트가 보내는 주문번호·paymentKey·승인 금액을 받는 불변 DTO다.
 * 어노테이션은 형식과 범위를 검사하며, 저장된 주문과의 금액 일치 여부는 PaymentService에서 검사한다.
 */
public record ConfirmPaymentRequest(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{6,64}") String orderId,
        @NotBlank @Size(max = 200) String paymentKey,
        @NotNull @DecimalMin("1") @Digits(integer = 12, fraction = 0) BigDecimal amount,
        @Pattern(regexp = "[a-fA-F0-9-]{36}") String attemptId) {
    public ConfirmPaymentRequest(String orderId, String paymentKey, BigDecimal amount) {
        this(orderId, paymentKey, amount, null);
    }
}
