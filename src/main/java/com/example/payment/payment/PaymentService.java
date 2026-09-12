/* 파일 역할: 결제 승인·재확인을 DB 작업 선점 → PG 호출 → DB 결과 저장 순서로 연결한다. */
package com.example.payment.payment;

import com.example.payment.api.error.ApiException;
import com.example.payment.gateway.PaymentGateway;
import com.example.payment.gateway.PaymentGateway.PaymentCommand;
import com.example.payment.order.OrderResponse;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

/**
 * 결제 처리 순서를 조율하는 서비스다. DB 트랜잭션은 PaymentTransactions에 맡긴다.
 * 이 클래스의 흐름 전체를 트랜잭션으로 감싸지 않아 외부 PG 응답을 기다리는 동안 DB 잠금을 유지하지 않는다.
 */
@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final PaymentTransactions transactions;
    private final PaymentGateway gateway;

    /** DB 작업을 담당하는 서비스와 PG 연동 인터페이스를 주입받는다. */
    public PaymentService(PaymentTransactions transactions, PaymentGateway gateway) {
        this.transactions = transactions;
        this.gateway = gateway;
    }

    /** 승인 시도를 먼저 저장하고 작업을 확보했을 때만 PG 승인을 요청한다. 중복 요청에는 기존 결과를 쓴다. */
    public OrderResponse confirm(ConfirmPaymentRequest request) {
        return execute(transactions.claimConfirmation(request), gateway::confirm);
    }

    /** 미확정 결제의 조회 작업을 확보한 후 기존 paymentKey로 PG 결과를 조회한다. 새 승인은 요청하지 않는다. */
    public OrderResponse reconcile(String orderId) {
        return execute(transactions.claimReconciliation(orderId), gateway::lookup);
    }

    /**
     * 확보한 작업이 있으면 트랜잭션 밖에서 승인 또는 조회 함수를 실행한 뒤 별도 트랜잭션으로 결과를 저장한다.
     * PG 처리 후 저장에 실패하면 재확인이 필요하므로 일반 중복 오류 대신 503 오류를 전달한다.
     */
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
