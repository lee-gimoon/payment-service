/* 파일 역할: 주문 없음·금액 불일치 등 예상 가능한 업무 오류를 HTTP 응답 계층까지 전달한다. */
package com.example.payment.api.error;

import org.springframework.http.HttpStatus;

/** HTTP 상태, 기계가 구분할 오류 코드, 사용자 안내 메시지를 함께 가진 업무 예외다. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    /** 예외 처리기가 API 응답으로 변환할 상태·코드·메시지를 보관한다. */
    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /** 오류 응답에 사용할 HTTP 상태를 반환한다. */
    public HttpStatus status() { return status; }
    /** AMOUNT_MISMATCH 등 오류 종류를 구분하는 코드를 반환한다. */
    public String code() { return code; }
}
