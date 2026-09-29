# API

기본 주소는 `http://127.0.0.1:8080`이다. 로컬 프런트엔드는 Vite 프록시로 같은 API를 호출한다.

- 요청·응답은 JSON이며 금액은 원화(`KRW`) 정수다.
- 주문·결제 API는 Keycloak이 발급한 access token을 `Authorization: Bearer <token>` 헤더로 보내야 한다. 주문은 주문한 회원만 조회·결제할 수 있다. 로그인 흐름은 [로그인과 회원](authentication.md)을 참고한다.
- 필드 스키마는 실행 중인 서버의 [Swagger UI](http://127.0.0.1:8080/swagger-ui.html)에서 확인한다.

## 엔드포인트

| Method | Path | 로그인 | 정상 응답 | 주요 오류 | 동작 |
| --- | --- | --- | --- | --- | --- |
| GET | `/products` | — | `200` 상품 배열 | — | 판매 중인 상품 목록 |
| GET | `/products/{id}` | — | `200` 상품 | `404 PRODUCT_NOT_FOUND` | 판매 중인 상품 상세 |
| POST | `/orders` | 필요 | `201` 주문 | `400 INVALID_CART`·`PRODUCT_NOT_FOUND` | 서버 가격으로 주문 생성 |
| GET | `/orders/{orderId}` | 필요 | `200` 주문 | `404 ORDER_NOT_FOUND` | 저장된 주문·결제 결과 조회. PG 호출 없음 |
| POST | `/orders/{orderId}/payment-attempts` | 필요 | `201` 시도 | `404 ORDER_NOT_FOUND`, `409 ORDER_NOT_PAYABLE` | 결제창을 열기 전 시도 생성 |
| POST | `/payment-attempts/{attemptId}/authentication-result` | 필요 | `200` 시도 | `400 INVALID_ATTEMPT_STATUS`, `404 ATTEMPT_NOT_FOUND` | 인증 취소·실패 기록 |
| GET | `/payment-config` | — | `200` 공개 설정 | — | 키 설정 여부, 클라이언트 키, UI variant 키 |
| POST | `/payments/confirm` | 필요 | `200` / `202` / `422` 주문 | `400 AMOUNT_MISMATCH`, `404 ORDER_NOT_FOUND`·`ATTEMPT_NOT_FOUND`, `409 PAYMENT_CONFLICT`·`ATTEMPT_CONFLICT`, `503 PAYMENT_NOT_CONFIGURED` | 인증 후 최종 승인 |

- 로그인이 필요한 API에 토큰이 없거나, 서명·발급자·만료·대상(`aud`)이 맞지 않으면 `401 UNAUTHORIZED`다.
- 다른 회원의 주문과 시도는 존재 여부를 알리지 않고 `404 ORDER_NOT_FOUND`·`ATTEMPT_NOT_FOUND`로 응답한다.

모든 요청에서 본문 형식 오류는 `400 INVALID_REQUEST`, 동시 수정·DB 제약 충돌은 `409 PAYMENT_CONFLICT`, DB 장애는 `503 STORAGE_UNAVAILABLE`이 될 수 있다.

**호출 순서:** 주문 생성 → 시도 생성 → 결제창 인증 → 서버 승인. 인증 성공 URL로 돌아온 것만으로는 결제 완료가 아니며, 결과는 `payment.status`로 판단한다.

## 공개 설정

```json
{ "enabled": false, "clientKey": "", "paymentMethodVariantKey": "", "agreementVariantKey": "" }
```

- `enabled`는 클라이언트·시크릿 키가 모두 설정됐는지만 나타낸다. 키의 실제 유효성은 확인하지 않는다.
- 키가 없으면 상품·주문·시도 API는 동작하지만 승인은 `503 PAYMENT_NOT_CONFIGURED`로 거부한다.

## 주문 생성

```json
{ "items": [{ "productId": "tee-01", "size": "M", "quantity": 1 }] }
```

- 항목 1~20개, 사이즈 `S`·`M`·`L`·`XL`, 항목당 수량 1~10, 총수량 최대 100.
- 같은 상품·사이즈 조합은 중복할 수 없고, 판매 중인 상품만 주문할 수 있다.
- 가격은 요청으로 받지 않는다. 서버가 계산한 `amount`를 결제창과 승인 요청에 쓴다.
- 호출할 때마다 새 주문이 생긴다. 중복 결제 방지는 같은 주문번호 안에서만 적용된다.
- 주문에는 토큰의 회원 ID(`sub`)가 저장된다. 이후 그 주문의 조회·시도·승인은 같은 회원만 할 수 있다.

## 시도 생성과 인증 결과

`POST /orders/{orderId}/payment-attempts` → `{ "id": "...", "orderId": "...", "status": "STARTED" }`

- `PENDING_PAYMENT` 주문에만 만들 수 있고, 호출할 때마다 새 시도가 생긴다.
- 응답의 `id`를 결제창 복귀 URL과 승인 요청의 `attemptId`로 쓴다.

`POST /payment-attempts/{attemptId}/authentication-result`

```json
{ "status": "AUTH_CANCELED", "errorCode": "WINDOW_CLOSED" }
```

- `status`는 `AUTH_CANCELED`·`AUTH_FAILED`만 허용한다. `errorCode`는 선택이며 최대 80자다.
- `STARTED`가 아닌 시도에 온 알림은 무시하고 현재 시도를 반환한다.

## 결제 승인

`POST /payments/confirm`

```json
{
  "orderId": "f96d4e32-3b74-4c77-b1ef-6d03ea521870",
  "paymentKey": "payment-key-from-success-url",
  "amount": 19000,
  "attemptId": "123e4567-e89b-12d3-a456-426614174000"
}
```

| 필드 | 검증 |
| --- | --- |
| `orderId` | 영문·숫자·`_`·`-` 6~64자, 로그인한 회원의 주문 |
| `paymentKey` | 공백 불가, 최대 200자 |
| `amount` | 1 이상 12자리 이하 정수, DB 주문 금액과 같아야 함 |
| `attemptId` | UUID 형식, 해당 주문의 시도이며 새 승인이면 `STARTED` |

| HTTP | `payment.status` | 의미 |
| --- | --- | --- |
| `200` | `SUCCEEDED` | 결제 완료 |
| `202` | `APPROVING`, `UNKNOWN`, `REVIEW_REQUIRED` | 아직 확정되지 않음. 결제 성공이 아니다. |
| `422` | `FAILED` | 승인 실패. 새 시도로 다시 결제할 수 있다. |

- 같은 주문·결제 키·시도·금액으로 다시 보내면 토스를 다시 호출하지 않고 **현재** 주문 결과를 반환한다. 시도가 1분 넘게 `APPROVING`·`UNKNOWN`이면 그때만 토스 조회를 한 번 한다.
- `APPROVING`·`UNKNOWN`은 서버의 복구 작업이 확정한다. 클라이언트는 새 결제를 시작하지 말고 `GET /orders/{orderId}`로 다시 조회한다.
- `REVIEW_REQUIRED`는 사람이 확인해야 하며 조회를 반복해도 풀리지 않을 수 있다.

## 주문 응답

주문 생성·조회·승인 응답은 같은 구조다.

```json
{
  "orderId": "f96d4e32-3b74-4c77-b1ef-6d03ea521870",
  "productName": "선데이 크루 티",
  "quantity": 1,
  "amount": 19000,
  "currency": "KRW",
  "items": [
    { "productId": "tee-01", "productName": "선데이 크루 티", "size": "M", "unitPrice": 19000, "quantity": 1 }
  ],
  "createdAt": "2026-09-28T01:00:00Z",
  "status": "PAID",
  "latestAttempt": {
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "status": "SUCCEEDED",
    "startedAt": "2026-09-28T01:00:01Z",
    "finishedAt": "2026-09-28T01:00:06Z",
    "errorCode": null
  },
  "payment": {
    "status": "SUCCEEDED",
    "pgStatus": "DONE",
    "approvedAt": "2026-09-28T01:00:05Z",
    "checkedAt": "2026-09-28T01:00:06Z",
    "errorCode": null,
    "message": "결제가 완료되었습니다.",
    "paidAmount": 19000,
    "paidCurrency": "KRW"
  }
}
```

| 필드 | 의미 |
| --- | --- |
| `latestAttempt` | 가장 최근에 **만든** 시도. 인증 취소·실패도 포함. 없으면 `null` |
| `payment` | 가장 최근에 **승인을 요청한** 시도의 상태와 PG 응답 정보 |
| `payment.paidAmount`·`pgStatus`·`approvedAt` | PG가 알려준 값. 값이 있어도 결제 완료라는 뜻은 아니다. |
| `payment.checkedAt` | 서버가 PG 결과를 마지막으로 기록한 시각 |

- 두 필드는 서로 다른 시도를 가리킬 수 있다. 예를 들어 승인 실패 뒤 결제창을 닫으면 `latestAttempt`는 `AUTH_CANCELED`, `payment`는 `FAILED`다.
- 시각은 ISO 8601 UTC 문자열이다. `finishedAt`은 종료 상태에서만 값이 있다.
- `paymentKey`와 전체 시도 이력은 응답에 넣지 않는다.

## 상태 값

| 필드 | 값 | 클라이언트 처리 |
| --- | --- | --- |
| 주문 `status` | `PENDING_PAYMENT`, `PAYMENT_IN_PROGRESS`, `PAID` | `PENDING_PAYMENT`일 때만 결제 버튼을 활성화한다. |
| `payment.status` | `READY`, `APPROVING`, `SUCCEEDED`, `FAILED`, `UNKNOWN`, `REVIEW_REQUIRED` | `SUCCEEDED`일 때만 결제 완료로 표시한다. 미확정이면 새 결제 대신 조회를 안내한다. |
| `latestAttempt.status` | `STARTED`, `AUTH_CANCELED`, `AUTH_FAILED`, `APPROVING`, `UNKNOWN`, `REVIEW_REQUIRED`, `SUCCEEDED`, `FAILED` | 결제창 닫기·인증 실패 안내에 쓴다. |

`READY`는 승인을 요청한 시도가 없다는 응답 값이다. 상태별 의미는 [아키텍처](architecture.md#상태)를 참고한다.

## 오류 응답

```json
{ "code": "AMOUNT_MISMATCH", "message": "주문 금액과 결제 금액이 다릅니다." }
```

| HTTP | 코드 |
| --- | --- |
| `400` | `INVALID_REQUEST`, `INVALID_CART`, `PRODUCT_NOT_FOUND`, `AMOUNT_MISMATCH`, `INVALID_ATTEMPT_STATUS` |
| `401` | `UNAUTHORIZED` |
| `404` | `PRODUCT_NOT_FOUND`, `ORDER_NOT_FOUND`, `ATTEMPT_NOT_FOUND` |
| `409` | `ORDER_NOT_PAYABLE`, `PAYMENT_CONFLICT`, `ATTEMPT_CONFLICT` |
| `503` | `PAYMENT_NOT_CONFIGURED`, `STORAGE_UNAVAILABLE` |

- 승인 결과의 `422`는 이 형식이 아니라 주문 본문을 반환한다.
- 승인 중 저장 장애나 응답 유실이 생기면 HTTP 오류만으로 실패를 판단할 수 없다. 새 결제를 시작하지 말고 주문을 다시 조회한다.
