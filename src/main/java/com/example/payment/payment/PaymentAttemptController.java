package com.example.payment.payment;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentAttemptController {
    private final PaymentAttemptService attempts;

    public PaymentAttemptController(PaymentAttemptService attempts) { this.attempts = attempts; }

    @PostMapping("/orders/{orderId}/payment-attempts")
    public ResponseEntity<PaymentAttemptService.AttemptResponse> start(@PathVariable String orderId) {
        return ResponseEntity.status(201).body(attempts.start(orderId));
    }

    @PostMapping("/payment-attempts/{attemptId}/authentication-result")
    public PaymentAttemptService.AttemptResponse authenticationResult(
            @PathVariable String attemptId, @Valid @RequestBody PaymentAttemptService.AuthenticationResult request) {
        return attempts.authenticationResult(attemptId, request);
    }
}
