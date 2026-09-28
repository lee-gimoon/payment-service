# API

이 문서는 클라이언트가 사용하는 호출 순서, 입력 검증, 결제 결과 처리 계약을 설명한다. 서버 기본 주소는 `http://127.0.0.1:8080`이며, 로컬 프런트엔드는 Vite 프록시를 통해 같은 API를 호출한다.

- 본문이 있는 요청은 `Content-Type: application/json`을 사용한다. 응답도 JSON이다.
- 상품 가격·주문 금액은 원화(`KRW`) 정수다. 승인 요청에는 서버가 반환한 주문 금액을 그대로 전달한다.
- 현재 API에는 사용자 인증과 주문 접근 권한 검사가 없다. 주문번호만으로 주문을 조회할 수 있다.
- 실행 중인 서버의 [Swagger UI](http://127.0.0.1:8080/swagger-ui.html)에서 스키마와 호출을 확인할 수 있다. 서비스 내부의 장바구니 검증과 결제 상태별 제약은 이 문서를 함께 참고한다.

## 엔드포인트

| Method | Path | 정상 응답 | 주요 오류 | 동작 |
| --- | --- | --- | --- | --- |
| GET | `/products` | `200` 상품 배열 | — | 판매 중인 상품을 표시 순서대로 반환. |
| GET | `/products/{id}` | `200` 상품 | `404 PRODUCT_NOT_FOUND` | 판매 중인 상품 상세. 판매 중지 상품도 없는 것으로 본다. |
| POST | `/orders` | `201` 주문 | `400 INVALID_CART`·`PRODUCT_NOT_FOUND` | 서버 가격으로 주문 생성. `Location: /orders/{orderId}` 포함. |
| GET | `/orders/{orderId}` | `200` 주문 | `404 ORDER_NOT_FOUND` | 주문 항목, 최근 시도, 가장 최근에 승인을 요청한 시도의 저장된 결과. PG 호출 없음. |
| POST | `/orders/{orderId}/payment-attempts` | `201` 시도 | `404 ORDER_NOT_FOUND`, `409 ORDER_NOT_PAYABLE` | 결제창을 열기 전 시도 생성. 요청 본문 없음. |
| POST | `/payment-attempts/{attemptId}/authentication-result` | `200` 시도 | `400 INVALID_ATTEMPT_STATUS`, `404 ATTEMPT_NOT_FOUND` | 인증 취소·실패 이벤트 기록. |
| GET | `/payment-config` | `200` 공개 설정 | — | 키 설정 여부, 클라이언트 키, UI variant 키. 시크릿 키는 반환하지 않음. |
| POST | `/payments/confirm` | `200` / `202` / `422` 주문 | `400 AMOUNT_MISMATCH`, `404 ORDER_NOT_FOUND`·`ATTEMPT_NOT_FOUND`, `409 PAYMENT_CONFLICT`·`ATTEMPT_CONFLICT`, `503 PAYMENT_NOT_CONFIGURED` | 인증 후 최종 승인. 불확실한 결과는 같은 요청에서 한 번 재조회. |

요청 본문 형식이 잘못되면 `400 INVALID_REQUEST`, 같은 행을 동시에 수정하거나 DB 제약에 걸리면 `409 PAYMENT_CONFLICT`, DB에 접근하지 못하면 `503 STORAGE_UNAVAILABLE`을 반환할 수 있다.

호출 순서는 상품·설정 조회 → 주문 생성 → 시도 생성 → 결제창 인증 → 서버 승인이다. 인증 성공 URL로 돌아온 것만으로 결제가 완료된 것은 아니다. 최종 성공 여부는 서버가 반환한 `payment.status`로 판단한다.

## 상품과 공개 설정

`GET /products`는 판매 중인 상품의 배열을 반환하며 페이지 구분은 없다. `GET /products/{id}`는 같은 구조의 상품 하나를 반환한다. 상품 필드는 `id`, `name`, `subtitle`, `category`, `price`, `color`, `stage`, `artwork`, `badge`다. `price`는 숫자이며 나머지는 문자열이다.

`GET /payment-config` 응답 예시(키 미설정):

```json
{
  "enabled": false,
  "clientKey": "",
  "paymentMethodVariantKey": "",
  "agreementVariantKey": ""
}
```

`enabled`는 서버에 클라이언트·시크릿 키가 모두 설정되어 있는지를 나타내며, 토스 연결 성공이나 키의 실제 유효성을 확인한 값은 아니다. 두 키를 생략하면 상품·주문·시도 생성은 가능하지만 신규 승인은 `503 PAYMENT_NOT_CONFIGURED`로 거부한다. 빈 variant 키는 프런트엔드에서 SDK에 전달하지 않는다. 허용하는 테스트 키 형식과 실행 설정은 [설정 문서](configuration.md)를 참고한다.

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
- 상품명·단가·옵션은 주문 시점의 값으로 저장한다. 이후 상품 가격이나 판매 여부가 바뀌어도 기존 주문 내역은 유지된다.

호출할 때마다 새 주문번호를 만든다. 같은 장바구니를 다시 보내도 기존 주문을 반환하지 않으므로, 응답으로 받은 `orderId`를 보관해 이후 조회와 결제에 사용한다. 중복 승인 방지는 동일 주문번호 안에서 적용된다.

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

시도 생성은 `PENDING_PAYMENT` 주문에만 허용한다. 호출할 때마다 새 시도 ID를 만들며, 시도 생성 자체로 주문이 `PAYMENT_IN_PROGRESS`가 되지는 않는다. 주문 상태는 서버 승인 요청이 관문을 통과할 때 바뀐다.

응답의 `id`를 결제창 성공·실패 복귀 URL의 `attemptId`와 승인 요청에 전달한다. 인증 취소·실패 또는 명확한 승인 실패 후 다시 결제창을 열 때에는 같은 주문에 새 시도를 생성한다.

`POST /payment-attempts/{attemptId}/authentication-result`

```json
{
  "status": "AUTH_CANCELED",
  "errorCode": "WINDOW_CLOSED"
}
```

허용 상태는 `AUTH_CANCELED`와 `AUTH_FAILED`뿐이다. `status` 누락·알 수 없는 상태 문자열은 `INVALID_REQUEST`, 정의된 다른 상태 값은 `INVALID_ATTEMPT_STATUS`로 거부한다. `errorCode`는 선택 사항이며 최대 80자다. 응답 구조는 시도 생성 응답과 같다. 이미 `STARTED`를 벗어난 시도에 유효한 취소·실패 이벤트가 도착하면 상태를 바꾸지 않고 현재 시도를 반환한다.

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

같은 주문·키·시도·금액으로 재요청하면 저장된 현재 주문 결과를 반환하고 토스 승인을 다시 호출하지 않는다. `APPROVING`·`UNKNOWN` 시도의 마지막 확인 시각(아직 확인하지 않았다면 승인 요청 시각)에서 1분이 지났을 때만 토스 조회를 한 번 실행한다. 결과가 불확실하더라도 다른 결제 키나 새 시도로 승인을 우회할 수 없다.

재요청 응답은 해당 주문의 현재 상태이므로 첫 응답과 같다고 보장하지 않는다. 예를 들어 이전 시도가 실패한 뒤 새 시도로 성공했다면, 이전 승인 요청을 다시 보내도 현재 성공한 주문 결과를 받는다.

| HTTP | `payment.status` | 응답 본문 |
| --- | --- | --- |
| `200` | `SUCCEEDED` | 주문 |
| `202` | `APPROVING`, `UNKNOWN`, `REVIEW_REQUIRED` | 주문 |
| `422` | `FAILED` | 주문 |

`202`는 결제 성공을 뜻하지 않는다. `APPROVING`·`UNKNOWN`은 서버의 복구 작업이 활성화되어 있으면 토스 조회로 확인을 이어간다. 클라이언트는 `GET /orders/{orderId}`로 저장된 결과를 다시 조회한다. 주문 조회 자체는 PG를 호출하지 않는다.

`REVIEW_REQUIRED`는 자동 복구 대상에서 제외되므로 반복 조회만으로 해결된다고 보장할 수 없다. 운영자가 토스 결제 내역과 저장값을 대조해야 하며 현재 수동 확정 API는 없다. 확인 중에는 새 결제를 시작하지 않는다. 승인을 요청한 뒤에는 명확한 `FAILED`일 때만 같은 주문의 새 결제를 허용한다.

## 주문 응답과 시각

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

주문 생성·조회·승인 응답은 위 주문 구조를 공통으로 사용한다.

| 필드 | 의미 |
| --- | --- |
| `productName`, `quantity`, `amount` | 결제창에 쓰는 주문 요약명, 총수량, 서버가 계산한 총액. 상품별 내역은 `items`에 있다. |
| `latestAttempt` | 가장 최근에 **생성한** 시도. 인증 취소·실패도 포함하며, 시도가 없으면 `null`. |
| `payment` | 가장 최근에 **승인을 요청한** 시도의 상태와 PG 응답 정보. 성공한 결제만 담는 DB `payments` 행을 직접 반환하는 필드가 아니다. |
| `payment.paidAmount`, `paidCurrency`, `pgStatus`, `approvedAt` | PG 응답에서 수신한 금액·통화·상태·승인 시각. 불일치나 미확정 상태에서도 값이 있을 수 있으므로 값의 존재만으로 결제 완료를 판단하지 않는다. |
| `payment.checkedAt` | 서버가 PG 승인·조회 결과를 마지막으로 기록한 시각. 주문 조회 시각이 아니다. |

예를 들어 승인 실패 후 새 결제창을 열고 닫으면 `latestAttempt.status`는 `AUTH_CANCELED`, `payment.status`는 이전 승인의 `FAILED`일 수 있다. 두 필드는 서로 다른 시도를 가리킬 수 있다. 결제 성공 여부는 `payment.status = SUCCEEDED`로 판단한다.

시도 생성 응답에는 `orderId`가 있지만 주문의 `latestAttempt`에는 포함되지 않는다. `paymentKey`와 전체 시도 이력은 주문 응답에 노출하지 않는다.

시각은 `Instant`를 ISO 8601 UTC 문자열(예: `2026-09-28T01:00:00Z`)로 반환한다. `latestAttempt.finishedAt`은 인증 취소·실패 또는 승인 성공·실패가 기록된 시각이며, 진행 중·미확정 상태에서는 `null`이다. 현재 프런트엔드는 한국어 형식(`ko-KR`)으로 **브라우저의 시간대**에 맞춰 표시하며 `Asia/Seoul`로 고정하지 않는다. DB 세션 시간대 변경은 API 시각의 의미를 바꾸지 않는다.

## 상태 값

| 필드 | 값 | 클라이언트 처리 |
| --- | --- | --- |
| 주문 `status` | `PENDING_PAYMENT`, `PAYMENT_IN_PROGRESS`, `PAID` | `PENDING_PAYMENT`일 때만 결제 버튼을 활성화한다. |
| `payment.status` | `READY`, `APPROVING`, `SUCCEEDED`, `FAILED`, `UNKNOWN`, `REVIEW_REQUIRED` | `SUCCEEDED`일 때만 결제 완료로 표시한다. `APPROVING`·`UNKNOWN`에서는 결과 조회, `REVIEW_REQUIRED`에서는 운영자 확인을 안내한다. |
| `latestAttempt.status` | `STARTED`, `AUTH_CANCELED`, `AUTH_FAILED`, `APPROVING`, `UNKNOWN`, `REVIEW_REQUIRED`, `SUCCEEDED`, `FAILED` | 결제창 닫기·인증 실패 안내에 사용한다. |

`payment.status`의 `READY`는 승인을 요청한 시도가 없다는 응답 값이다. 각 상태의 의미와 전이는 [아키텍처](architecture.md#상태)를 참고한다.

## 오류 응답

애플리케이션이 처리하는 입력·조회·충돌·저장 오류는 다음 형식이다. 클라이언트는 `code`로 오류를 구분하고 `message`를 안내에 사용한다. 승인 결과의 `422`는 위 주문 본문을 반환하므로 별도로 처리해야 한다.

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

승인 요청 중 저장 장애나 응답 유실이 발생하면 실제 승인 여부를 HTTP 오류만으로 판단할 수 없다. 새 결제를 시작하지 말고 같은 주문번호로 결과를 조회한다. 미확정 승인은 복구 작업이 토스 조회로 확인하며, 확정하지 못한 경우 운영자 확인이 필요하다. 처리 경계는 [아키텍처](architecture.md#실패와-복구)를 참고한다.
