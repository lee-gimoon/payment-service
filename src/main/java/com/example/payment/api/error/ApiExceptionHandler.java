/* 파일 역할: Controller 처리 중 발생한 업무·입력·DB 예외를 공통 JSON 오류 응답으로 바꾼다. */
package com.example.payment.api.error;

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

/** 여러 API에서 발생하는 예외의 HTTP 상태와 오류 본문을 일관되게 정하는 전역 예외 처리기다. */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** 클라이언트가 오류 종류를 구분하고 안내를 표시하는 데 사용하는 공통 응답 DTO다. */
    public record ErrorResponse(String code, String message) {}

    /** 업무 코드가 지정한 HTTP 상태·오류 코드·메시지를 그대로 API 응답에 담는다. */
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> business(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ErrorResponse(exception.code(), exception.getMessage()));
    }

    /** 잘못된 JSON, 요청 필드, 경로 값 검증 오류를 INVALID_REQUEST와 HTTP 400으로 통일한다. */
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class})
    ResponseEntity<ErrorResponse> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST", "요청 형식과 값을 확인해주세요."));
    }

    /** 여기까지 전달된 DB 무결성 위반을 결제 충돌로 응답한다. PG 호출 후 저장 오류는 서비스가 별도 변환한다. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> conflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(409).body(new ErrorResponse("PAYMENT_CONFLICT", "이미 다른 주문에 연결된 결제이거나 중복 요청입니다. 주문 결과를 조회해주세요."));
    }

    /** DB 접근·트랜잭션 장애를 503으로 응답하고, 결제 재시작 대신 기존 결과 확인을 안내한다. */
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<ErrorResponse> databaseUnavailable(Exception exception) {
        // JDBC 오류 본문에는 paymentKey 등 요청 값이 들어갈 수 있으므로 기록하지 않는다.
        log.error("Payment storage unavailable: {}", exception.getClass().getSimpleName());
        return ResponseEntity.status(503).body(new ErrorResponse("STORAGE_UNAVAILABLE",
                "저장 결과를 확인할 수 없습니다. 결제를 다시 시작하지 말고 주문 조회 후 PG 결과 재확인을 이용해주세요."));
    }
}
