package com.example.payment.payment.api;

import com.example.payment.payment.application.PaymentAttemptService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentAttemptController {
    private final PaymentAttemptService attempts;

    public PaymentAttemptController(PaymentAttemptService attempts) { this.attempts = attempts; }

    @PostMapping("/orders/{orderId}/payment-attempts")
    public ResponseEntity<PaymentAttemptService.AttemptResponse> start(@AuthenticationPrincipal Jwt customer,
                                                                       @PathVariable String orderId) {
        return ResponseEntity.status(201).body(attempts.start(orderId, customer.getSubject()));
    }

    @PostMapping("/payment-attempts/{attemptId}/authentication-result")
    public PaymentAttemptService.AttemptResponse authenticationResult(@AuthenticationPrincipal Jwt customer,
            @PathVariable String attemptId, @Valid @RequestBody PaymentAttemptService.AuthenticationResult request) {
        return attempts.authenticationResult(attemptId, customer.getSubject(), request);
    }
}
