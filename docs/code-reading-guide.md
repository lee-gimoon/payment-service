# 처음 읽는 주문·결제 코드

현재 자바 코드는 주문 생성 → 카드 인증 → 결제 승인 → 결과 조회를 이해하기 위한 학습용 구조입니다. React 코드는 기존 그대로 사용합니다.

## 1. 주문을 만든다

`POST /orders` → `OrderController.create()` → `OrderService.create()`

```java
PurchaseOrder order = new PurchaseOrder("티셔츠", 1, 10_000);
order = orderRepository.save(order);
return OrderResponse.of(order, null);
```

- `new PurchaseOrder(...)`: 메모리에 주문 객체를 만듭니다.
- `save(order)`: DB의 `purchase_orders`에 저장합니다.
- `OrderResponse.of(order, null)`: 아직 결제가 없으므로 `READY`를 반환합니다.
- 클라이언트가 보내는 상품·금액은 읽지 않습니다. 주문 금액은 서버에서 정합니다.

## 2. 카드 인증을 한다

React가 클라이언트 키와 주문번호·금액으로 토스 결제창을 엽니다. 사용자가 카드 인증을 끝내면 토스가 브라우저에 `paymentKey`, `orderId`, `amount`를 돌려줍니다.

카드 인증은 토스 결제창에서 진행합니다. 이 단계가 끝났다고 서버의 결제 승인까지 완료된 것은 아닙니다.

## 3. 서버에 결제 승인을 요청한다

React가 세 값을 JSON으로 `POST /payments/confirm`에 보냅니다.

`PaymentController.confirm()` → **`PaymentService.confirm()`**

이 메서드를 먼저 읽으세요. 번호가 붙은 주석대로 다음 작업을 합니다.

1. 주문번호로 서버의 주문을 찾습니다.
2. 브라우저가 보낸 금액과 DB의 주문 금액을 비교합니다.
3. 이미 같은 결제 요청이 저장돼 있으면 그 결과를 반환합니다.
4. 결제 키를 `PROCESSING` 상태로 먼저 저장합니다.
5. `TossPaymentClient.confirm()`으로 토스에 최종 승인을 요청합니다.
6. 토스 결과를 결제에 반영하고 저장합니다.

`TossPaymentClient`는 토스 HTTP 통신만 담당합니다. 내부의 `client.post().uri(...).body(...)`가 외부 API 요청입니다. `PaymentResult`는 그 응답을 우리 서비스의 상태로 바꾼 DTO입니다.

## 4. 결과를 조회한다

`GET /orders/{orderId}` → `OrderService.get()`

서버는 주문과 결제를 DB에서 읽어 `OrderResponse`로 반환합니다. 주문번호를 `Payment`의 기본 키로도 쓰기 때문에 같은 번호로 두 테이블을 조회할 수 있습니다.

## 먼저 볼 파일

| 순서 | 파일 | 읽을 부분 |
| --- | --- | --- |
| 1 | [OrderController](../src/main/java/com/example/payment/order/OrderController.java) | 요청 URL과 서비스 호출 |
| 2 | [OrderService](../src/main/java/com/example/payment/order/OrderService.java) | 주문 객체 생성과 저장 |
| 3 | [PaymentController](../src/main/java/com/example/payment/payment/PaymentController.java) | 인증 결과 JSON 받기 |
| 4 | [PaymentService](../src/main/java/com/example/payment/payment/PaymentService.java) | confirm()의 1~6번 |
| 5 | [TossPaymentClient](../src/main/java/com/example/payment/gateway/TossPaymentClient.java) | confirm()의 HTTP 요청 |
| 6 | [PurchaseOrder](../src/main/java/com/example/payment/order/PurchaseOrder.java), [Payment](../src/main/java/com/example/payment/payment/Payment.java) | DB에 저장하는 필드 |
| 7 | [OrderResponse](../src/main/java/com/example/payment/order/OrderResponse.java) | 프론트에 반환하는 JSON 구성 |

프록시나 팩토리의 내부 원리를 몰라도 이 순서로 읽을 수 있습니다. 이전에 정리한 JPA 개념은 [별도 참고 문서](java-jpa-notes.md)에 있습니다.

## 지금 DB에 남긴 컬럼

`purchase_orders`는 5개 컬럼입니다.

| 컬럼 | 의미 |
| --- | --- |
| id | 주문번호 |
| product_name | 상품명 |
| quantity | 수량 |
| amount | 주문 총금액 |
| created_at | 주문 시각 |

`payments`는 8개 컬럼입니다.

| 컬럼 | 의미 |
| --- | --- |
| order_id | 결제할 주문번호. 기본 키이므로 주문당 한 행 |
| payment_key | 토스 승인·조회에 쓰는 키. 다른 주문과 중복 금지 |
| status | PROCESSING / SUCCEEDED / FAILED / UNKNOWN |
| approved_at | 토스에서 결제가 승인된 시각 |
| checked_at | 토스 결과를 마지막으로 확인한 시각 |
| pg_status | 토스의 원본 상태. 예: DONE |
| error_code | 화면에 전달할 오류 원인 |
| version | JPA가 동시 저장 충돌을 검사하는 번호 |

금액은 주문 테이블 한 곳에서만 관리합니다. 원화 전용이므로 응답의 `currency`는 항상 `KRW`입니다. 기존 프론트는 `attemptId`가 있으면 임시 승인 정보를 지우므로, 결제가 있으면 여기에 주문번호를 반환합니다. 별도 컬럼은 저장하지 않습니다.

기존 DB는 [V2 마이그레이션](../src/main/resources/db/migration/V2__simplify_payment_processing.sql)으로 바뀝니다. 주문·결제 행과 승인 결과는 유지하고, 쓰지 않는 컬럼만 제거합니다. V1은 이전 DB를 만들었던 변경 이력이므로 다시 수정하지 않습니다.

## 정상 흐름을 이해한 뒤 볼 보조 기능

- `reconcile()`: PROCESSING 또는 UNKNOWN 결제를 토스에서 다시 조회합니다. 새 승인은 요청하지 않습니다.
- `ApiExceptionHandler`: 잘못된 입력과 저장 오류를 React용 JSON 메시지로 바꿉니다.
- `Payment.version`의 `@Version`: 두 요청이 동시에 결과를 저장하면, 오래된 결과의 덮어쓰기를 JPA가 거부합니다. 이 경우 화면에서 저장된 결과를 다시 조회합니다.
- `TossProperties`, `PaymentConfiguration`: 테스트 키, 토스 주소, HTTP 제한 시간을 설정합니다.

## 간단하게 바꾼 부분과 남긴 규칙

별도 트랜잭션 서비스, 주문 행의 비관적 잠금, 작업 ID, 30초 처리 기한, 공통 실행 함수는 제거했습니다. `PROCESSING` 상태는 시간이 지났다는 이유로 자동 변경하지 않으며, 바로 수동 재확인이 가능합니다.

저장소 메서드 호출별로 트랜잭션을 끝내므로 토스 호출 전체에 `@Transactional`을 붙이지 않습니다. 토스 호출 전에 결제 정보를 저장해 중복 승인을 막고, 저장 실패 뒤에도 같은 키로 결과를 조회할 수 있습니다. UUID 주문번호를 토스의 `Idempotency-Key`로 사용합니다.

중복 요청이 최초 저장 시점에 정확히 겹치거나 결과 저장이 충돌하면 한 요청은 409를 받을 수 있습니다. 프론트의 저장된 결과 조회로 확인합니다. 조회한 토스 결과조차 아직 불명확하면 UNKNOWN을 유지합니다.

취소·환불·웹훅·자동 복구 배치·여러 결제 시도를 다루는 확장은 이후 학습 단계입니다. 인증과 주문 접근 권한이 없는 로컬 테스트 예제라는 범위도 동일합니다.
