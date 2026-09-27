# API

요청·응답은 JSON이며 주문·승인 요청 금액은 KRW 정수다. 현재 API에는 인증과 주문 접근 권한 검사가 없다. 필드별 스키마는 실행 중인 서버의 [Swagger UI](http://127.0.0.1:8080/swagger-ui.html)를 기준으로 확인한다. 이 문서는 호출 순서와 코드에서 적용하는 검증·상태 계약을 설명한다.

## 엔드포인트

| Method | Path | 정상 응답 | 동작 |
| --- | --- | --- | --- |
| GET | `/products` | `200` 상품 배열 | 판매 중인 상품을 표시 순서대로 반환. |
| GET | `/products/{id}` | `200` 상품 | 판매 중인 상품 상세. 없거나 판매 중지면 `404`. |
| POST | `/orders` | `201` 주문 | 서버 가격으로 주문 생성. `Location: /orders/{orderId}` 포함. |
| GET | `/orders/{orderId}` | `200` 주문 | 주문 항목, 최근 시도, 최근 거래의 저장된 결과. PG 호출 없음. |
| POST | `/orders/{orderId}/payment-attempts` | `201` 시도 | 결제창을 열기 전 시도 생성. 요청 본문 없음. |
| POST | `/payment-attempts/{attemptId}/authentication-result` | `200` 시도 | 인증 취소·실패 이벤트 기록. |
| GET | `/payment-config` | `200` 공개 설정 | 결제 사용 가능 여부, 클라이언트 키, UI variant 키. 시크릿 키는 반환하지 않음. |
| POST | `/payments/confirm` | `200` / `202` / `422` 주문 | 인증 후 최종 승인. 불확실한 결과는 같은 요청에서 한 번 재조회. |

호출 순서는 주문 생성 → 시도 생성 → 결제창 인증 → 서버 승인이다. 시도 생성은 `PENDING_PAYMENT` 주문에 `FAILED`가 아닌 거래가 없을 때 허용한다. 테스트 키가 없으면 공개 설정의 `enabled`는 `false`이며 신규 승인은 `503`으로 거부한다.

## 주문 생성

`POST /orders`

```json
{
  "items": [
    { "productId": "tee-01", "size": "M", "quantity": 1 }
  ]
}
```

- 항목은 1~20개, 사이즈는 `S`·`M`·`L`·`XL`, 항목당 수량은 1~10, 총수량은 최대 100이다.
- 같은 상품 ID와 사이즈 조합을 중복할 수 없다. 모든 상품은 현재 판매 중이어야 한다.
- 상품 가격은 요청으로 받지 않는다. 서버가 확정한 `amount`를 이후 결제창과 승인 요청에 사용한다.

응답은 아래 승인 성공 예시와 같은 주문 구조다. 생성 직후에는 `status = PENDING_PAYMENT`, `latestAttempt = null`, `payment.status = READY`이며 PG 정보·승인 및 확인 시각·오류 코드는 `null`이다.

## 시도 생성과 인증 이벤트

`POST /orders/{orderId}/payment-attempts` 응답 예시:

```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "orderId": "f96d4e32-3b74-4c77-b1ef-6d03ea521870",
  "status": "STARTED"
}
```

응답의 `id`를 결제창 성공·실패 복귀 URL의 `attemptId`와 승인 요청에 전달한다. 명확한 승인 실패 후 다시 결제창을 열 때에는 새 시도를 생성한다.

`POST /payment-attempts/{attemptId}/authentication-result`

```json
{
  "status": "AUTH_CANCELED",
  "errorCode": "WINDOW_CLOSED"
}
```

허용 상태는 `AUTH_CANCELED`와 `AUTH_FAILED`뿐이다. `errorCode`는 선택 사항이며 최대 80자다. 응답 구조는 시도 생성 응답과 같다. 이미 `STARTED`를 벗어난 시도에 늦은 이벤트가 도착하면 상태를 바꾸지 않고 현재 시도를 반환한다.

이 엔드포인트는 결제창 닫기·인증 실패를 기록한다. 승인된 결제를 취소하는 API는 제공하지 않는다.

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

예시의 결제 키는 형식 설명용이다. 실제 호출에는 인증 성공 복귀 URL에서 받은 키를 사용한다.

| 필드 | 검증 |
| --- | --- |
| `orderId` | 필수. 영문·숫자·`_`·`-`로 구성된 6~64자. 존재하는 주문이어야 한다. |
| `paymentKey` | 필수. 공백뿐인 값은 거부하며 최대 200자. |
| `amount` | 필수 정수. `1`~`999999999999`. DB 주문 금액과 정확히 같아야 한다. |
| `attemptId` | 필수. 16진수 `8-4-4-4-12` UUID 형식. 서버가 생성한 해당 주문의 시도 ID이며, 신규 승인 시 `STARTED`여야 한다. |

같은 주문·키·시도의 재요청에는 저장된 현재 주문 결과를 반환하고 토스 승인을 다시 호출하지 않는다. 결과가 불확실하더라도 다른 결제 키나 새 시도로 승인을 우회할 수 없다. 이후 시도가 있다면 응답의 최근 시도·거래는 그 현재 이력을 반영한다.

| HTTP | `payment.status` | 응답 본문 |
| --- | --- | --- |
| `200` | `SUCCEEDED` | 주문 |
| `202` | `PROCESSING`, `UNKNOWN`, `REVIEW_REQUIRED` | 주문 |
| `422` | `FAILED` | 주문 |

`202`는 결제 성공을 뜻하지 않는다. `GET /orders/{orderId}`로 저장된 결과를 확인할 수 있지만, 조회 자체가 PG 재조회를 수행하지는 않는다. 승인 거래가 생성된 뒤에는 명확한 `FAILED`일 때만 같은 주문의 새 결제를 허용한다. 거래 생성 전 인증 취소·실패는 새 시도로 다시 진행할 수 있다.

승인 성공 `200` 응답 예시:

```json
{
  "orderId": "f96d4e32-3b74-4c77-b1ef-6d03ea521870",
  "productName": "선데이 크루 티",
  "quantity": 1,
  "amount": 19000,
  "currency": "KRW",
  "items": [
    {
      "productId": "tee-01",
      "productName": "선데이 크루 티",
      "size": "M",
      "unitPrice": 19000,
      "quantity": 1
    }
  ],
  "createdAt": "2026-09-28T01:00:00Z",
  "status": "CONFIRMED",
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

시도 생성 응답에는 `orderId`가 있지만 주문의 `latestAttempt`에는 포함되지 않는다. `paymentKey`는 주문 응답에 노출하지 않는다. `paidAmount`·`paidCurrency`·`pgStatus`는 PG에서 확인한 정보이며, 결과 판단은 서비스의 `payment.status`를 사용한다.

## 오류 응답

입력·조회·충돌·저장 오류는 다음 형식이다. 승인 결과의 `422`는 이 오류 형식이 아니라 위 주문 본문을 반환한다.

```json
{
  "code": "AMOUNT_MISMATCH",
  "message": "주문 금액과 결제 금액이 다릅니다."
}
```

| HTTP | 주요 코드 | 의미 |
| --- | --- | --- |
| `400` | `INVALID_REQUEST`, `INVALID_CART`, `PRODUCT_NOT_FOUND`, `AMOUNT_MISMATCH`, `INVALID_ATTEMPT_STATUS` | 요청 형식·값 또는 주문 항목 검증 실패. |
| `404` | `PRODUCT_NOT_FOUND`, `ORDER_NOT_FOUND`, `ATTEMPT_NOT_FOUND` | 요청한 리소스가 없음. 상품 상세는 판매 중지 상품도 제외. |
| `409` | `ORDER_NOT_PAYABLE`, `PAYMENT_CONFLICT`, `ATTEMPT_CONFLICT` | 중복·동시 처리 충돌 또는 진행할 수 없는 주문·시도. |
| `503` | `PAYMENT_NOT_CONFIGURED`, `STORAGE_UNAVAILABLE` | 키 미설정 또는 DB 접근·트랜잭션 장애. |

저장 장애와 응답 유실은 실제 승인 실패를 보장하지 않는다. 주문 결과와 PG 기록을 먼저 확인해야 하며, 미확정 거래의 자동 복구 기능은 없다. 자세한 처리 경계는 [아키텍처](architecture.md)를 참고한다.
