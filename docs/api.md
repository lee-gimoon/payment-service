# API

기본 주소는 `http://127.0.0.1:8080`이다. 로컬 프런트엔드는 Vite 프록시로 같은 API를 호출한다.

- 요청·응답은 JSON이며 금액은 원화(`KRW`) 정수다.
- 주문·결제·상담 API는 Keycloak이 발급한 access token을 `Authorization: Bearer <token>` 헤더로 보내야 한다. 주문은 주문한 회원만 조회·결제할 수 있다. 로그인 흐름은 [로그인과 회원](authentication.md)을 참고한다.
- 필드 스키마는 실행 중인 서버의 [Swagger UI](http://127.0.0.1:8080/swagger-ui.html)에서 확인한다.

## 엔드포인트

| Method | Path | 로그인 | 정상 응답 | 주요 오류 | 동작 |
| --- | --- | --- | --- | --- | --- |
| GET | `/products` | — | `200` 상품 배열 | — | 판매 중인 상품 목록과 사이즈별 품절 여부 |
| GET | `/products/{id}` | — | `200` 상품 | `404 PRODUCT_NOT_FOUND` | 판매 중인 상품 상세와 사이즈별 품절 여부 |
| POST | `/orders` | 필요 | `201` 주문 | `400 INVALID_CART`·`PRODUCT_NOT_FOUND`, `409 OUT_OF_STOCK` | 서버 가격으로 주문 생성. 재고는 확인만 함 |
| GET | `/orders/{orderId}` | 필요 | `200` 주문 | `404 ORDER_NOT_FOUND` | 저장된 주문·결제 결과 조회. PG 호출 없음 |
| POST | `/orders/{orderId}/payment-attempts` | 필요 | `201` 시도 | `404 ORDER_NOT_FOUND`, `409 ORDER_NOT_PAYABLE`·`OUT_OF_STOCK` | 결제창을 열기 전 시도 생성. 재고는 확인만 함 |
| POST | `/payment-attempts/{attemptId}/authentication-result` | 필요 | `200` 시도 | `400 INVALID_ATTEMPT_STATUS`, `404 ATTEMPT_NOT_FOUND` | 인증 취소·실패 기록 |
| GET | `/payment-config` | — | `200` 공개 설정 | — | 키 설정 여부, 클라이언트 키, UI variant 키 |
| POST | `/payments/confirm` | 필요 | `200` / `202` / `422` 주문 | `400 AMOUNT_MISMATCH`, `404 ORDER_NOT_FOUND`·`ATTEMPT_NOT_FOUND`, `409 PAYMENT_CONFLICT`·`ATTEMPT_CONFLICT`·`OUT_OF_STOCK`, `503 PAYMENT_NOT_CONFIGURED` | 인증 후 재고 차감과 최종 승인 |
| GET | `/chat/messages` | 필요 | `200` 대화 | `403 FORBIDDEN` | 내 상담 대화. 보낸 적 없으면 빈 목록 |
| POST | `/chat/messages` | 필요 | `201` 메시지, 재전송 `200` | `403 FORBIDDEN`, `409 CHAT_MESSAGE_CONFLICT` | 상담 메시지 보내기. 첫 메시지면 상담방 생성 |
| POST | `/chat/read` | 필요 | `204` | `403 FORBIDDEN` | 관리자 답변 읽음 표시 |
| GET | `/admin/chat/rooms` | 관리자 | `200` 상담방 배열 | `403 FORBIDDEN` | 상담 목록 |
| GET | `/admin/chat/rooms/{roomId}/messages` | 관리자 | `200` 대화 | `403 FORBIDDEN`, `404 CHAT_ROOM_NOT_FOUND` | 한 고객과의 대화 |
| POST | `/admin/chat/rooms/{roomId}/messages` | 관리자 | `201` 메시지, 재전송 `200` | `403 FORBIDDEN`, `404 CHAT_ROOM_NOT_FOUND`, `409 CHAT_MESSAGE_CONFLICT` | 답장 |
| POST | `/admin/chat/rooms/{roomId}/read` | 관리자 | `204` | `403 FORBIDDEN`, `404 CHAT_ROOM_NOT_FOUND` | 고객 메시지 읽음 표시 |

- 로그인이 필요한 API에 토큰이 없거나, 서명·발급자·만료·대상(`aud`)이 맞지 않으면 `401 UNAUTHORIZED`다.
- 다른 회원의 주문과 시도는 존재 여부를 알리지 않고 `404 ORDER_NOT_FOUND`·`ATTEMPT_NOT_FOUND`로 응답한다.
- `/admin/**` 경로는 쇼핑몰 관리자 전용이다. 토큰의 `realm_access.roles`에 `shop-admin`이 없으면 `403 FORBIDDEN`이다.
- 고객 상담 API는 상담방 ID를 받지 않는다. 서버가 토큰의 회원 ID로 그 회원의 상담방을 찾는다.
- 고객 상담 API(`/chat/**`)는 쇼핑몰 관리자가 호출하면 `403 FORBIDDEN`이다. 관리자는 `/admin/chat/**`로 답한다.

모든 요청에서 본문 형식 오류는 `400 INVALID_REQUEST`, 동시 수정·DB 제약 충돌은 `409 PAYMENT_CONFLICT`, DB 장애는 `503 STORAGE_UNAVAILABLE`이 될 수 있다.

**호출 순서:** 주문 생성 → 시도 생성 → 결제창 인증 → 서버 승인. 인증 성공 URL로 돌아온 것만으로는 결제 완료가 아니며, 결과는 `payment.status`로 판단한다.

## 공개 설정

```json
{ "enabled": false, "clientKey": "", "paymentMethodVariantKey": "", "agreementVariantKey": "" }
```

- `enabled`는 클라이언트·시크릿 키가 모두 설정됐는지만 나타낸다. 키의 실제 유효성은 확인하지 않는다.
- 키가 없으면 상품·주문·시도 API는 동작하지만 승인은 `503 PAYMENT_NOT_CONFIGURED`로 거부한다.

## 상품과 재고

```json
{
  "id": "tee-01",
  "name": "선데이 크루 티",
  "subtitle": "IVORY / REGULAR",
  "category": "베이식",
  "price": 19000,
  "color": "#fff5de",
  "stage": "#f9e9d9",
  "artwork": "sun",
  "badge": "BEST",
  "sizes": [
    { "size": "S", "soldOut": false },
    { "size": "M", "soldOut": false },
    { "size": "L", "soldOut": true },
    { "size": "XL", "soldOut": false }
  ]
}
```

- `sizes`는 `S`·`M`·`L`·`XL` 순서다. 남은 수량은 알려주지 않고 품절 여부만 알려준다.
- 재고는 주문 생성과 시도 생성에서 확인만 하고, 승인 요청(`POST /payments/confirm`) 때 가져간다. 승인 실패가 확인되면 돌려준다.
- 그래서 주문할 때 재고가 있었어도 승인 전에 다른 주문이 먼저 가져가면 승인 요청이 `409 OUT_OF_STOCK`이 된다. 이때는 토스 승인을 호출하지 않으므로 결제 금액이 청구되지 않는다.

## 주문 생성

```json
{ "items": [{ "productId": "tee-01", "size": "M", "quantity": 1 }] }
```

- 항목 1~20개, 사이즈 `S`·`M`·`L`·`XL`, 항목당 수량 1~10, 총수량 최대 100.
- 같은 상품·사이즈 조합은 중복할 수 없고, 판매 중인 상품만 주문할 수 있다.
- 가격은 요청으로 받지 않는다. 서버가 계산한 `amount`를 결제창과 승인 요청에 쓴다.
- 호출할 때마다 새 주문이 생긴다. 중복 결제 방지는 같은 주문번호 안에서만 적용된다.
- 주문에는 토큰의 회원 ID(`sub`)가 저장된다. 이후 그 주문의 조회·시도·승인은 같은 회원만 할 수 있다.
- 품절 사이즈나 남은 수량보다 많은 수량을 담으면 `409 OUT_OF_STOCK`이다. 주문을 만들어도 재고는 아직 가져가지 않는다.

## 시도 생성과 인증 결과

`POST /orders/{orderId}/payment-attempts` → `{ "id": "...", "orderId": "...", "status": "STARTED" }`

- `PENDING_PAYMENT` 주문에만 만들 수 있고, 호출할 때마다 새 시도가 생긴다.
- 응답의 `id`를 결제창 복귀 URL과 승인 요청의 `attemptId`로 쓴다.
- 주문 항목 중 남은 수량이 부족한 사이즈가 있으면 결제창을 열기 전에 `409 OUT_OF_STOCK`으로 거부한다.

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

- 새 승인이면 승인 슬롯을 잡기 전에 주문 항목의 재고를 가져간다. 한 항목이라도 부족하면 `409 OUT_OF_STOCK`으로 거부하고 토스를 호출하지 않는다. 시도는 `STARTED`로 남고, 재고가 채워지면 같은 주문으로 다시 결제할 수 있다.
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

## 상담

고객과 쇼핑몰 관리자의 1:1 상담이다. 고객당 상담방 하나에 대화가 이어서 쌓인다. 메시지는 아래 HTTP API로 보내고, 저장된 새 메시지는 [실시간 알림](#실시간-알림-websocket)으로 받는다. 저장 규칙은 [1:1 상담](chat.md)을 참고한다.

### 메시지 보내기

`POST /chat/messages`(고객), `POST /admin/chat/rooms/{roomId}/messages`(관리자)

```json
{ "clientMessageId": "8f14e45f-ceea-467f-a0e6-5f7b2d1c9a10", "content": "M이랑 L 중 뭐가 커요?" }
```

| 필드 | 검증 |
| --- | --- |
| `clientMessageId` | UUID 형식. 브라우저가 메시지마다 새로 만들고, 재전송할 때는 같은 값을 쓴다 |
| `content` | 공백만으로는 안 되고 최대 1000자. 앞뒤 공백은 지우고 저장한다 |

- 새로 저장하면 `201`로 메시지를 반환한다. 같은 `clientMessageId`와 내용으로 다시 보내면 새로 저장하지 않고 저장된 메시지를 `200`으로 반환한다.
- 같은 `clientMessageId`로 다른 내용을 보내면 `409 CHAT_MESSAGE_CONFLICT`다.
- 고객의 첫 메시지가 상담방을 만든다. 관리자는 있는 상담방에만 답할 수 있다.

### 대화 조회

`GET /chat/messages?before={id}`(고객), `GET /admin/chat/rooms/{roomId}/messages?before={id}`(관리자). `before` 대신 `after={id}`를 쓸 수 있다.

```json
{
  "messages": [
    {
      "id": 1,
      "clientMessageId": "8f14e45f-ceea-467f-a0e6-5f7b2d1c9a10",
      "sender": "CUSTOMER",
      "content": "M이랑 L 중 뭐가 커요?",
      "createdAt": "2026-10-01T01:00:00Z"
    },
    {
      "id": 2,
      "clientMessageId": "c9f0f895-fb98-4b91-8f2e-3a6d5f0b7c21",
      "sender": "ADMIN",
      "content": "L이 가슴단면 3cm 더 커요.",
      "createdAt": "2026-10-01T01:03:00Z"
    }
  ],
  "hasMore": false,
  "unreadCount": 1
}
```

| 필드 | 의미 |
| --- | --- |
| `messages` | 오래된 순, 최대 50개. 아무것도 주지 않으면 최근 메시지, `before`를 주면 그 번호보다 이전, `after`를 주면 그 번호보다 이후 메시지 |
| `hasMore` | `before`·기본 조회면 더 이전 메시지가, `after` 조회면 더 이후 메시지가 있는지 |
| `unreadCount` | 조회한 쪽이 아직 읽지 않은 상대 메시지 수 |
| `sender` | `CUSTOMER` 또는 `ADMIN`. 고객 화면은 `ADMIN`을 쇼핑몰 이름으로 표시한다. 답한 관리자의 ID는 응답에 없다 |

- `before`와 `after`를 함께 보내면 `400 INVALID_REQUEST`다.
- 이전 대화 더 보기는 받은 첫 메시지의 `id`를 `before`로, 다시 연결한 뒤 놓친 메시지 채우기는 **대화 조회로 확인한 마지막 메시지**의 `id`를 `after`로 보낸다. 전송 응답이나 실시간 알림의 더 큰 번호로 복구 위치를 건너뛰지 않는다. `hasMore`가 `true`면 같은 방향으로 이어서 조회한다.

### 읽음 표시

`POST /chat/read`(고객), `POST /admin/chat/rooms/{roomId}/read`(관리자) → `204`

```json
{ "lastReadMessageId": 2 }
```

- 화면에 보여 준 마지막 메시지 번호를 보낸다. 이 번호까지 상대 메시지를 읽은 것으로 기록한다.
- 읽음 위치는 앞으로만 움직인다. 없는 번호를 보내도 마지막 메시지까지만 읽은 것으로 기록한다.
- 메시지를 보내면 보낸 사람은 자기 메시지까지 읽은 것으로 기록된다.

### 상담 목록 (관리자)

`GET /admin/chat/rooms?waiting=true`

```json
[
  {
    "roomId": "0b9f2c1e-7d4a-4f3b-9a8e-2c5d6e7f8a90",
    "customerName": "김모도",
    "customerEmail": "buyer@modo.test",
    "lastMessage": {
      "id": 3,
      "clientMessageId": "45c48cce-2e2d-4fbd-a1b2-c3d4e5f6a7b8",
      "sender": "CUSTOMER",
      "content": "결제했는데 대기로 나와요",
      "createdAt": "2026-10-01T02:00:00Z"
    },
    "waiting": true,
    "unreadCount": 1
  }
]
```

- 마지막 메시지가 최근인 순으로 최대 100개다.
- `waiting`은 마지막 메시지를 고객이 보내 답변을 기다리는지다. `waiting=true`로 조회하면 이런 방을 먼저 고른 뒤 최대 100개를 준다. 전체 목록의 최근 100개를 브라우저에서 필터링하면 오래된 미답변 상담이 누락되므로, 답변 대기 목록은 이 매개변수로 조회한다.
- `unreadCount`는 관리자가 아직 읽지 않은 고객 메시지 수다.

### 실시간 알림 (WebSocket)

저장된 새 메시지를 STOMP over WebSocket으로 받는다. 받기 전용이며, 메시지 보내기는 위 HTTP API를 쓴다.

| 항목 | 값 |
| --- | --- |
| 연결 주소 | `/ws` (예: `ws://127.0.0.1:8080/ws`) |
| `CONNECT` 헤더 | `Authorization: Bearer <access token>`. 없거나 검증에 실패하면 `ERROR` 프레임 후 연결 종료 |
| 고객 구독 | `/user/queue/chat` → 본문은 [대화 조회](#대화-조회)의 메시지 하나. 쇼핑몰 관리자는 구독할 수 없음 |
| 관리자 구독 | `/topic/admin/chat` → 본문은 `{ "roomId": "...", "message": { ...메시지 } }`. `shop-admin` 역할 필요 |
| `SEND` 프레임 | 받지 않는다. 보내면 `ERROR` 프레임 후 연결 종료 |

- 알림은 메시지 저장이 커밋된 뒤에 보낸다. 재전송(`200`)은 다시 알리지 않는다.
- 연결이 끊긴 동안의 메시지는 알림으로 다시 오지 않는다. 다시 연결하면 `after`로 조회해 채운다.
- 보낸 사람도 자기 메시지 알림을 받는다. 같은 회원이 여러 탭을 열어도 모든 탭이 맞춰진다.

## 오류 응답

```json
{ "code": "AMOUNT_MISMATCH", "message": "주문 금액과 결제 금액이 다릅니다." }
```

| HTTP | 코드 |
| --- | --- |
| `400` | `INVALID_REQUEST`, `INVALID_CART`, `PRODUCT_NOT_FOUND`, `AMOUNT_MISMATCH`, `INVALID_ATTEMPT_STATUS` |
| `401` | `UNAUTHORIZED` |
| `403` | `FORBIDDEN` |
| `404` | `PRODUCT_NOT_FOUND`, `ORDER_NOT_FOUND`, `ATTEMPT_NOT_FOUND`, `CHAT_ROOM_NOT_FOUND` |
| `409` | `ORDER_NOT_PAYABLE`, `PAYMENT_CONFLICT`, `ATTEMPT_CONFLICT`, `OUT_OF_STOCK`, `CHAT_MESSAGE_CONFLICT` |
| `503` | `PAYMENT_NOT_CONFIGURED`, `STORAGE_UNAVAILABLE` |

- 승인 결과의 `422`는 이 형식이 아니라 주문 본문을 반환한다.
- 승인 중 저장 장애나 응답 유실이 생기면 HTTP 오류만으로 실패를 판단할 수 없다. 새 결제를 시작하지 말고 주문을 다시 조회한다.
