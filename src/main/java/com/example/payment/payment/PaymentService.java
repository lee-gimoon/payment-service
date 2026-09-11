package com.example.payment.payment;

import com.example.payment.api.ApiException;
import com.example.payment.gateway.PaymentGateway;
import com.example.payment.gateway.PaymentGateway.PaymentCommand;
import com.example.payment.order.OrderResponse;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final PaymentTransactions transactions;
    private final PaymentGateway gateway;

    public PaymentService(PaymentTransactions transactions, PaymentGateway gateway) {
        this.transactions = transactions;
        this.gateway = gateway;
    }

    public OrderResponse confirm(ConfirmPaymentRequest request) {
        return execute(transactions.claimConfirmation(request), gateway::confirm);
    }

    public OrderResponse reconcile(String orderId) {
        return execute(transactions.claimReconciliation(orderId), gateway::lookup);
    }

    private OrderResponse execute(PaymentTransactions.Claim claim, Function<PaymentCommand, PaymentOutcome> operation) {
        if (claim.command() == null) {
            return claim.response();
        }
        // claim의 트랜잭션 커밋 후에만 PG를 호출한다. 네트워크 호출 중 DB 잠금을 잡지 않는다.
        PaymentOutcome outcome = operation.apply(claim.command());
        try {
            OrderResponse response = transactions.finish(claim.command(), outcome);
            log.info("Payment result orderId={} attemptId={} status={}", claim.command().orderId(),
                    claim.command().attemptId(), response.payment().status());
            return response;
        } catch (RuntimeException exception) {
            log.error("Payment result persistence failed; reconcile orderId={} attemptId={}",
                    claim.command().orderId(), claim.command().attemptId());
            // PG 호출 후 발생한 무결성 오류도 일반적인 중복 요청(409)으로 내보내면 안 된다.
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE",
                    "승인 결과를 저장하지 못했습니다. 결제를 다시 시작하지 말고 주문 조회 후 PG 결과 재확인을 이용해주세요.");
        }
    }
}
