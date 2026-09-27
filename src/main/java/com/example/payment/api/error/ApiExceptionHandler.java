package com.example.payment.api.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record ErrorResponse(String code, String message) {}

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> business(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ErrorResponse(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_REQUEST", "요청 형식과 값을 확인해주세요."));
    }

    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> conflict(Exception exception) {
        return ResponseEntity.status(409)
                .body(new ErrorResponse("PAYMENT_CONFLICT", "요청이 중복되거나 결과가 먼저 변경되었습니다. 저장된 주문 결과를 조회해주세요."));
    }

    // PG 승인은 이미 완료됐을 수 있으므로 저장 장애를 결제 실패로 응답하지 않는다.
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ErrorResponse> databaseUnavailable(Exception exception) {
        log.error("Payment storage unavailable: {}", exception.getClass().getSimpleName());
        return ResponseEntity.status(503)
                .body(new ErrorResponse("STORAGE_UNAVAILABLE", "결제 결과를 저장하지 못했습니다. 다시 결제하지 말고 주문번호로 고객센터에 문의해주세요."));
    }
}
