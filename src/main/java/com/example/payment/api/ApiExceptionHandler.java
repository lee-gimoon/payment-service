package com.example.payment.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record ErrorResponse(String code, String message) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> business(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ErrorResponse(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class})
    ResponseEntity<ErrorResponse> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST", "요청 형식과 값을 확인해주세요."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> conflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(409).body(new ErrorResponse("PAYMENT_CONFLICT", "이미 다른 주문에 연결된 결제이거나 중복 요청입니다. 주문 결과를 조회해주세요."));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<ErrorResponse> databaseUnavailable(Exception exception) {
        // JDBC 오류 본문에는 paymentKey 등 요청 값이 들어갈 수 있으므로 기록하지 않는다.
        log.error("Payment storage unavailable: {}", exception.getClass().getSimpleName());
        return ResponseEntity.status(503).body(new ErrorResponse("STORAGE_UNAVAILABLE",
                "저장 결과를 확인할 수 없습니다. 결제를 다시 시작하지 말고 주문 조회 후 PG 결과 재확인을 이용해주세요."));
    }
}
