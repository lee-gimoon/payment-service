# 승인 결과 재조회와 조건부 취소

토스 승인 응답만으로 결과를 확정할 수 없을 때, 서버는 **같은 승인 요청 안에서** 결제를 한 번 재조회합니다. 같은 거래의 승인 금액·통화가 주문과 다르면 취소 의도를 DB에 먼저 저장하고 즉시 토스 취소 API를 호출합니다. 주기적 DB 조회나 브라우저의 반복 조회는 사용하지 않습니다.

## 처리 순서

1. `PaymentService.confirm()`이 브라우저 금액을 DB의 주문 금액과 비교합니다. 다르면 토스에 승인 요청을 보내지 않습니다.
2. 주문번호와 `paymentKey`, `PROCESSING` 상태를 DB에 저장한 뒤 토스 승인 API를 호출합니다.
3. 승인 응답이 같은 주문·키의 `DONE`이며 금액·통화가 맞으면 `SUCCEEDED`를 저장합니다. 명시적인 거절은 `FAILED`로 저장합니다.
4. 응답이 없거나 불일치해 결과를 확정할 수 없으면 `UNKNOWN`을 저장하고 바로 `GET /v1/payments/{paymentKey}`로 재조회합니다.
5. 조회 결과가 같은 주문번호·결제키의 정상 승인이고 금액·통화도 맞으면 `SUCCEEDED`로 저장합니다. 식별자가 다르면 취소하지 않고 `REVIEW_REQUIRED`로 남깁니다.
6. **같은 거래의 승인**인데 금액·통화가 다르면 실제 승인 금액·통화, `CANCEL_PENDING`, 취소 멱등키를 **취소 호출 전에 DB에 저장**합니다.
7. 이어서 `POST /v1/payments/{paymentKey}/cancel`을 호출합니다. `cancelAmount`를 생략해 전액 취소합니다. 토스의 `CANCELED`, 잔액 0, 성공한 취소 이력을 확인한 뒤 `CANCELED`와 취소 시각을 저장합니다.
8. 취소 응답을 받지 못하면 같은 요청에서 GET을 한 번 더 호출해 이미 취소됐는지 확인합니다. 그래도 결과를 확정할 수 없으면 취소 의도와 멱등키를 보존하고 `REVIEW_REQUIRED`와 오류 로그를 남깁니다.

`TossPaymentClient.readConfirmationResult()`는 승인 POST 응답을, `readLookupResult()`는 GET 재조회 응답을 해석합니다. `PaymentService`가 HTTP 호출과 DB 저장 순서를 관리합니다. 조회에서 `ABORTED`·`EXPIRED`가 확인되면 `FAILED`로 저장합니다. 부분 취소, 지원하지 않는 결제수단, 식별자 불일치는 자동 취소하지 않습니다.

## 처리하지 못한 경우

조회 자체가 실패하거나 취소 후에도 최종 상태가 확인되지 않으면 성공·실패로 단정하지 않고 `REVIEW_REQUIRED`로 기록합니다. 주문 화면은 저장된 결과만 읽으며 토스를 재호출하지 않습니다. 운영자는 주문번호와 토스 결제 내역을 대조해야 합니다.

서버가 승인·취소 호출 중 종료되거나 DB 저장에 실패하면 `PROCESSING`, `UNKNOWN`, `CANCEL_PENDING` 행이 남을 수 있습니다. **주기적 복구 작업이 없으므로 서버 재시작만으로 이 행이 자동 해결되지는 않습니다.** 실제 서비스에서는 운영 알림과 별도의 대조 절차가 필요합니다. 이 학습 프로젝트는 미확정 상태를 성공으로 표시하거나 결제를 다시 승인하지 않습니다.

이전 버전의 자동 복구용 컬럼과 일정은 V4 마이그레이션에서 제거합니다. 마이그레이션 시 이미 남아 있던 미확정 행은 `REVIEW_REQUIRED`로 바꾸고 주문·키·취소 의도는 보존합니다.

## 화면과 검증

`POST /payments/confirm`은 완료·취소 완료 시 200, 운영 확인 필요 시 202, 확정 실패 시 422를 반환합니다. `GET /orders/{orderId}`는 저장된 주문·결제·취소 내역을 한 번 읽습니다. 결과 화면에는 주문 내역 새로고침 버튼이 있으며, 사용자 조회가 토스 재조회나 취소를 실행하지 않습니다.

PostgreSQL 통합 테스트는 승인 요청 안의 재조회, 취소 의도 선저장, 취소 응답 유실 뒤 한 번의 확인 조회, 미확정 결과의 운영 확인 상태를 검증합니다. 모의 HTTP 테스트는 토스 승인·조회·취소 요청과 응답 검증을 확인합니다.

공식 명세: [취소 API](https://docs.tosspayments.com/guides/v2/cancel-payment), [멱등키](https://docs.tosspayments.com/reference/using-api/authorization), [결제 상태와 조회](https://docs.tosspayments.com/reference).
