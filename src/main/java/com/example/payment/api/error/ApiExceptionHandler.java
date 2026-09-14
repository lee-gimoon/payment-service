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

/** 서비스에서 발생한 오류를 React가 읽을 수 있는 {code, message} JSON으로 바꾸는 한 곳이다. */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record ErrorResponse(String code, String message) {}

    /** 주문 없음, 금액 불일치처럼 서비스가 직접 설명한 오류다. */
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

    /** DB의 중복 금지 또는 버전 검사가 요청 충돌을 발견하면 저장된 결과를 다시 보도록 안내한다. */
    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> conflict(Exception exception) {
        return ResponseEntity.status(409)
                .body(new ErrorResponse("PAYMENT_CONFLICT", "요청이 중복되거나 결과가 먼저 변경되었습니다. 저장된 주문 결과를 조회해주세요."));
    }

    /** DB 오류가 나도 토스 승인은 끝났을 수 있으므로 결제 실패로 단정하지 않는다. */
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ErrorResponse> databaseUnavailable(Exception exception) {
        log.error("Payment storage unavailable: {}", exception.getClass().getSimpleName());
        return ResponseEntity.status(503)
                .body(new ErrorResponse("STORAGE_UNAVAILABLE", "결과를 저장하지 못했습니다. 주문 조회 후 PG 결과 재확인을 이용해주세요."));
    }
}
