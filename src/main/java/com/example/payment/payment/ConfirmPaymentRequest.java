package com.example.payment.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ConfirmPaymentRequest(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{6,64}") String orderId,
        @NotBlank @Size(max = 200) String paymentKey,
        @NotNull @DecimalMin("1") @Digits(integer = 12, fraction = 0) BigDecimal amount) {}
