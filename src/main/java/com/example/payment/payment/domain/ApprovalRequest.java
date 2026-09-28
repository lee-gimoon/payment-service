package com.example.payment.payment.domain;

/** PG 승인·조회에 보내는 값. 모두 서버에 저장된 값이며 시도 ID는 멱등키로 쓴다. */
public record ApprovalRequest(String attemptId, String orderId, String paymentKey, long amount, String currency) {}
