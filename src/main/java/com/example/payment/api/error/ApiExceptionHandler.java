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

/**
 * Controller 밖으로 전파된 예외를 한곳에서 받아 HTTP 오류 응답으로 변환한다.
 * {@code @RestControllerAdvice}는 이 클래스를 모든 REST Controller에 적용되는
 * 전역 예외 처리기로 Spring에 등록한다.
 * 각 {@code @ExceptionHandler} 메서드가 반환한 ResponseEntity의 상태 코드는
 * HTTP 응답 상태로 사용되고, body 객체는 JSON 응답 본문으로 변환된다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    /** 서버 로그에 예외 정보를 기록할 때 사용한다. */
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** React에 전달할 오류 응답의 JSON 형태인 {"code": "...", "message": "..."}를 나타낸다. */
    public record ErrorResponse(String code, String message) {}

    /**
     * {@code @ExceptionHandler(ApiException.class)}는 Controller 실행 중 ApiException이
     * 밖으로 전파되면 Spring이 이 메서드를 호출하도록 지정한다.
     * 서비스가 예외에 담은 HTTP 상태·오류 코드·메시지를 그대로 응답으로 변환한다.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> business(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ErrorResponse(exception.code(), exception.getMessage()));
    }

    /**
     * 요청 값 검증에 실패하거나 읽을 수 없는 JSON이 들어오면 Spring이 이 메서드를 호출한다.
     * 두 예외를 같은 잘못된 요청(400 Bad Request) 응답으로 변환한다.
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_REQUEST", "요청 형식과 값을 확인해주세요."));
    }

    /**
     * DB의 중복 금지 제약이나 낙관적 잠금의 버전 검사가 충돌을 발견하면 호출된다.
     * 클라이언트가 저장된 최신 결과를 다시 조회하도록 409 Conflict를 반환한다.
     */
    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> conflict(Exception exception) {
        return ResponseEntity.status(409)
                .body(new ErrorResponse("PAYMENT_CONFLICT", "요청이 중복되거나 결과가 먼저 변경되었습니다. 저장된 주문 결과를 조회해주세요."));
    }

    /**
     * DB 접근 또는 트랜잭션 처리 중 오류가 밖으로 전파되면 호출된다.
     * 토스 승인은 이미 끝났을 수 있으므로 결제 실패로 단정하지 않고 503을 반환한다.
     */
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ErrorResponse> databaseUnavailable(Exception exception) {
        log.error("Payment storage unavailable: {}", exception.getClass().getSimpleName());
        return ResponseEntity.status(503)
                .body(new ErrorResponse("STORAGE_UNAVAILABLE", "결제 결과를 저장하지 못했습니다. 다시 결제하지 말고 주문번호로 고객센터에 문의해주세요."));
    }
}
