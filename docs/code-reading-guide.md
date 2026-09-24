# 처음 읽는 주문·결제 코드

현재 코드는 주문 생성 → 카드·간편결제 인증 → 결제 승인 → 필요할 때 즉시 재조회·조건부 취소 → 내역 조회를 구현합니다. 흐름의 의미는 [기본 결제 흐름](payment-domain.md), 실행 방법은 [README](../README.md)에 있습니다.

## 1. 주문을 만든다

`GET /products` → `ProductController.all()` → `ProductCatalog.all()` → `ProductRepository`가 DB의 `products` 테이블에서 상품을 읽습니다. React는 상품 ID·사이즈·수량을 장바구니에 담습니다.

`POST /orders` → `OrderController.create()` → `OrderService.create(CreateOrderRequest)`

```java
List<Product> products = new ArrayList<>();
Set<String> productIds = new HashSet<>();
int totalQuantity = 0;
long totalAmount = 0;
for (CreateOrderRequest.Item item : request.items()) {
    // 실제 코드는 여기서 옵션·수량도 검사합니다.
    Product product = productCatalog.forOrder(item.productId());
    products.add(product);
    productIds.add(product.getId());
    totalQuantity += item.quantity();
    totalAmount = Math.addExact(totalAmount, Math.multiplyExact(product.getPrice(), item.quantity()));
}
PurchaseOrder order = orderRepository.save(new PurchaseOrder(
        products.getFirst().getName(), productIds.size(), totalQuantity, totalAmount));
List<OrderItem> lines = new ArrayList<>();
for (int lineNumber = 0; lineNumber < request.items().size(); lineNumber++) {
    CreateOrderRequest.Item selected = request.items().get(lineNumber);
    lines.add(new OrderItem(order, products.get(lineNumber), selected.size(), selected.quantity(), lineNumber));
}
orderItemRepository.saveAll(lines);
return OrderResponse.of(order, lines, null, null);
```

- `ProductCatalog.forOrder(...)`: 클라이언트가 보낸 ID로 판매 중인 `Product`를 DB에서 조회합니다.
- `Product`와 요청의 상품 ID·사이즈·수량으로 총액을 계산합니다. 중간에 별도 엔티티를 만들지 않습니다.
- `new PurchaseOrder(...)`: 계산한 총수량·금액으로 주문 객체를 만듭니다.
- `new OrderItem(order, ...)`: 앞에서 만든 주문과 상품을 참조하는 **엔티티 객체**를 메모리에 만듭니다. `new`만으로는 DB 행이 생기지 않습니다.
- `save(order)`와 `saveAll(lines)`: 한 트랜잭션에서 `purchase_orders`의 주문 행과 `purchase_order_items`의 항목 행을 저장합니다. 항목 행마다 `order_id`와 `product_id`가 들어갑니다. SQL 실행은 JPA가 플러시할 때까지 미뤄질 수 있습니다.
- `OrderResponse.of(order, lines, null, null)`: 아직 결제 거래가 없으므로 `READY`를 반환합니다.
- 클라이언트는 상품 ID·사이즈·수량을 보내지만 가격은 보내지 않습니다. 주문 금액은 서버 상품 가격으로 정합니다.

## 2. 결제수단을 인증한다

React는 먼저 `POST /orders/{orderId}/payment-attempts`로 시도 행을 만든 뒤 [tossPayments.ts](../frontend/src/payments/tossPayments.ts)에서 공식 `@tosspayments/tosspayments-sdk` 패키지로 토스 결제창을 엽니다.

여기서 React와 토스 JS SDK의 역할은 구분됩니다. React는 버튼과 주문 상태를 관리하고, 브라우저 안에서 실행되는 SDK는 React가 넘긴 값으로 토스 결제창 요청을 만듭니다. SDK는 별도 서버가 아니며 시크릿 키도 사용하지 않습니다.

```typescript
const tossPayments = await loadTossPayments(clientKey);
const widgets = tossPayments.widgets({ customerKey: ANONYMOUS });
await widgets.setAmount({ currency: "KRW", value: order.amount });
const paymentWindow = await widgets.renderPaymentWindow();
paymentWindow.on("paymentRequest", async ({ paymentMethod }) => {
  // 실제 코드는 paymentMethod.code로 카드·국내 간편결제인지 먼저 확인한다.
  await widgets.requestPayment({ /* 주문번호·successUrl·failUrl */ });
});
```

`ANONYMOUS`는 비회원 구매자이고, `orderId`는 주문번호입니다. 결제창형 UI의 결제수단과 약관은 토스 어드민에서 설정합니다. [paymentWindow.ts](../frontend/src/payments/paymentWindow.ts)는 선택 이벤트의 지원 수단을 확인하고, 중복 요청을 막으며 닫기를 시도 상태로 기록합니다. 인증에 성공하면 토스가 브라우저를 `successUrl`로 이동시키며 `paymentKey`, `orderId`, `amount`를 쿼리 파라미터로 전달합니다. 실패하면 `failUrl`로 이동시키며 `code`, `message`를 전달합니다. 두 URL에 우리 `attemptId`를 넣어 시도를 식별합니다.

인증은 토스 결제창에서 진행합니다. 이 단계가 끝났다고 서버의 결제 승인까지 완료된 것은 아닙니다. [paymentRedirect.ts](../frontend/src/payments/paymentRedirect.ts)가 복귀 URL을 읽고, [PaymentResultPage.tsx](../frontend/src/pages/PaymentResultPage.tsx)가 승인 요청을 보냅니다. 인증 실패 시에는 URL의 오류 코드·메시지를 표시하며 승인을 요청하지 않습니다.

## 3. 서버에 결제 승인을 요청한다

React가 주문번호·결제 키·금액·시도 ID를 JSON으로 `POST /payments/confirm`에 보냅니다.

`PaymentController.confirm()` → **`PaymentService.confirm()`**

이 메서드를 먼저 읽으세요. 번호가 붙은 주석대로 다음 작업을 합니다.

1. 주문번호로 서버의 주문을 찾습니다.
2. 브라우저가 보낸 금액과 DB의 주문 금액을 비교합니다.
3. 이미 같은 결제 요청이 저장돼 있으면 그 결과를 반환합니다.
4. `PaymentPreparationService`가 주문을 잠그고 거래를 `PROCESSING` 상태로 저장하며 해당 시도를 연결합니다.
5. `TossPaymentClient.confirm()`으로 토스에 최종 승인을 요청합니다.
6. `PaymentSettlementService`가 거래·시도·주문 상태를 함께 저장합니다. 결과가 불확실하면 같은 요청에서 재조회·조건부 취소를 이어서 처리합니다.

`TossPaymentClient`는 토스 HTTP 통신만 담당합니다. 내부의 `client.post().uri(...).body(...)`가 외부 API 요청입니다. `PaymentResult`는 그 응답을 우리 서비스의 상태로 바꾼 DTO입니다.

승인 응답의 주문번호·결제 키·금액·통화가 요청과 일치하고, `status`가 `DONE`이며 승인 시각과 지원하는 결제수단(카드·간편결제)이 있어야 성공입니다. 간편결제는 계좌·포인트를 사용할 수도 있어 `card` 객체를 필수로 검사하지 않습니다.

## 4. 승인 요청 안에서 결과를 재조회하고 필요한 경우 취소한다

`PaymentService.confirm()` → `verifyAndCancelIfNeeded()`

승인 응답이 `UNKNOWN`일 때만 이 경로로 들어갑니다. 사용자의 승인 요청을 처리하는 동안 다음 단계를 연속으로 실행합니다.

1. `UNKNOWN`과 결제키를 DB에 저장하고 `TossPaymentClient.lookup()`으로 토스 결제를 한 번 조회합니다.
2. 같은 주문·키의 정상 승인은 `SUCCEEDED`로 저장합니다. 식별자가 다르면 취소하지 않고 `REVIEW_REQUIRED`로 남깁니다.
3. 같은 주문·키의 승인 금액·통화가 다르면 `CANCEL_PENDING`, 실제 승인 정보와 취소 멱등키를 DB에 저장합니다.
4. 이어서 `TossPaymentClient.cancel()`로 전액 취소합니다. 취소 상태·잔액·이력을 확인한 뒤 `CANCELED`와 취소 시각을 저장합니다.
5. 취소 응답이 불분명하면 GET으로 한 번 더 확인합니다. 그래도 확정할 수 없으면 취소 의도를 보존하고 `REVIEW_REQUIRED`로 기록합니다.

`readConfirmationResult()`는 승인 POST 응답을, `readLookupResult()`는 GET 재조회 응답을 해석합니다. `PaymentService`가 호출·저장 순서를 결정하며, 주기적인 DB 조회 작업은 없습니다. [상세 처리 순서와 한계](payment-recovery.md)

## 5. 저장된 주문·결제·취소 내역을 조회한다

`GET /orders/{orderId}` → `OrderService.get()`

서버는 주문과 결제를 DB에서 읽어 `OrderResponse`로 반환합니다. 주문 금액과 실제 승인 금액, 승인 시각과 취소 시각을 함께 담습니다. 주문번호를 `Payment`의 기본 키로도 쓰기 때문에 같은 번호로 두 테이블을 조회할 수 있습니다.

`StorePage`와 `PaymentResultPage`는 주문을 열거나 새로고침할 때 `getOrder()`로 저장된 결과를 읽습니다. 이 조회는 토스 재조회나 취소를 시작하지 않습니다.

## 먼저 볼 파일

| 순서 | 파일 | 읽을 부분 |
| --- | --- | --- |
| 1 | [OrderController](../src/main/java/com/example/payment/order/OrderController.java) | 요청 URL과 서비스 호출 |
| 2 | [OrderService](../src/main/java/com/example/payment/order/OrderService.java) | 주문 객체 생성과 저장 |
| 3 | [PaymentController](../src/main/java/com/example/payment/payment/api/PaymentController.java) | 인증 성공 후 승인 요청 JSON 받기 |
| 4 | [PaymentService](../src/main/java/com/example/payment/payment/application/PaymentService.java) | confirm()과 verifyAndCancelIfNeeded()의 호출·저장 순서 |
| 5 | [TossPaymentClient](../src/main/java/com/example/payment/payment/infrastructure/toss/TossPaymentClient.java) | confirm()·lookup()·cancel()과 응답별 해석 메서드 |
| 6 | [PurchaseOrder](../src/main/java/com/example/payment/order/PurchaseOrder.java), [Payment](../src/main/java/com/example/payment/payment/domain/Payment.java) | 저장 필드와 applyResult() |
| 7 | [OrderResponse](../src/main/java/com/example/payment/order/OrderResponse.java) | 주문·승인·취소 내역 응답 구성 |
| 8 | [PaymentResultPage](../frontend/src/pages/PaymentResultPage.tsx) | 승인 요청과 주문 내역 표시 |

프록시나 팩토리의 내부 원리를 몰라도 이 순서로 읽을 수 있습니다. 이전에 정리한 JPA 개념은 [별도 참고 문서](java-jpa-notes.md)에 있습니다.

## 지금 DB에 남긴 컬럼

`purchase_orders`의 주요 컬럼은 다음과 같습니다.

| 컬럼 | 의미 |
| --- | --- |
| id | 주문번호 |
| product_name | 화면에 표시할 주문 요약명. 실제 구입 상품은 주문 항목에 있음 |
| quantity | 수량 |
| amount | 주문 총금액 |
| currency | 주문 당시 통화. 현재는 KRW |
| created_at | 주문 시각 |
| status | PENDING_PAYMENT·CONFIRMED·CANCELED |
| version | JPA가 새 주문 저장과 동시 수정 충돌을 구분하는 번호 |

`payment_attempts`는 결제창을 연 한 번의 시도다. `id`, `order_id`, `status`, `started_at`, `finished_at`, `error_code`, `version`을 보관한다. 인증 전에 닫거나 실패하면 결제 거래 없이 시도 결과만 남는다.

`payments`는 인증 후의 거래와 취소 기록을 보관합니다.

| 컬럼 | 의미 |
| --- | --- |
| id | 거래 자체의 기본 키 |
| order_id | 결제할 주문번호. 같은 주문의 명확한 실패 이력을 여러 행으로 보존 |
| attempt_id | 거래를 만든 결제 시도 ID |
| payment_key | 토스 승인·조회에 쓰는 키. 다른 주문과 중복 금지 |
| requested_amount·requested_currency | 이 거래에서 토스에 요청한 금액·통화 |
| status | 승인·확인 중·취소 중·취소 완료·운영자 확인 상태 |
| approved_at | 토스에서 결제가 승인된 시각 |
| checked_at | 토스 결과를 마지막으로 확인한 시각 |
| pg_status | 토스의 원본 상태. 예: DONE |
| error_code | 화면에 전달할 오류 원인 |
| version | JPA가 동시 저장 충돌을 검사하는 번호 |
| pg_amount | 토스에서 확인한 실제 승인 금액 |
| pg_currency | 실제 승인에 사용된 통화 |
| canceled_at | 토스에서 확인한 전액 취소 시각 |
| cancel_idempotency_key | 같은 취소 재시도에 유지할 멱등키 |
| cancel_requested_at | 최초 취소 의도를 기록한 시각 |

기대 금액은 주문 테이블에, 실제 승인 금액은 결제 테이블에 보관합니다. 주문은 원화이며 불일치한 PG 금액·통화도 취소 검증을 위해 보존합니다. 프론트는 `latestAttempt`로 결제창 닫기·인증 실패를 확인하고, `payment.status`로 PG 거래 결과를 확인합니다.

V5부터는 `purchase_order_items`에 주문 시점의 상품 ID·이름·사이즈·단가·수량을 함께 저장합니다. 예전 주문은 항목 행이 없어도 요약과 결제 결과를 조회할 수 있습니다.

V6부터 판매 상품은 `products` 테이블의 `Product` 엔티티입니다. 초기 티셔츠 10종은 마이그레이션이 한 번만 넣습니다. 상품 가격을 바꿔도 이미 저장된 `purchase_order_items.unit_price`와 과거 주문 금액은 바뀌지 않습니다.

V7부터 구입 내역 한 줄도 `OrderItem` 엔티티입니다. 각 항목은 고유 `id`를 갖고 `order_id`로 `PurchaseOrder`와 연결됩니다. 기존 주문 항목 행에는 V7 마이그레이션이 ID를 채워 넣습니다.

V8은 주문 버전, 상품 판매 여부, 주문 항목의 상품 외래 키와 사이즈 제약을 추가합니다. 판매 중지 상품은 새 주문에서 제외하지만 과거 주문 항목은 유지합니다. 주문 조회 시 `OrderItemRepository`가 `order_id`로 항목을 읽습니다.

V9는 주문 상태, 결제 시도 테이블과 거래 자체 ID를 추가합니다. 이전 결제 행은 삭제하지 않고 시도 행을 만들어 연결합니다. 진행 중이거나 성공·확인 필요인 거래가 남아 있으면 새 거래의 중복 승인을 DB 고유 인덱스로 막고, 명확히 실패한 거래만 재시도를 허용합니다.

V10은 주문 통화와 거래별 요청 금액·통화를 보존하고, 결제 거래와 결제 시도가 같은 주문에 속한다는 복합 외래 키를 추가합니다.

V1·V2는 기존 변경 이력입니다. [V3 마이그레이션](../src/main/resources/db/migration/V3__automatic_payment_recovery.sql)은 취소 기록을 추가했고, [V4 마이그레이션](../src/main/resources/db/migration/V4__remove_periodic_payment_recovery.sql)은 주기적 복구 컬럼을 제거하며 기존 미확정 행을 운영 확인 상태로 옮깁니다. [V5 마이그레이션](../src/main/resources/db/migration/V5__catalog_orders.sql)은 주문 항목 테이블과 가변 주문 금액 제약을 추가합니다. [V6 마이그레이션](../src/main/resources/db/migration/V6__create_products.sql)은 상품 테이블과 초기 10종을 추가합니다. [V7 마이그레이션](../src/main/resources/db/migration/V7__order_items_entity.sql)은 기존 주문 항목에 고유 ID를 부여합니다. [V8 마이그레이션](../src/main/resources/db/migration/V8__product_order_integrity.sql)은 상품과 주문 항목의 연결 및 판매 상태를 보강합니다.

## 설정과 오류 처리도 함께 읽기

- `ApiExceptionHandler`: 잘못된 입력과 저장 오류를 React용 JSON 메시지로 바꿉니다.
- `Payment.version`의 `@Version`: 두 요청이 동시에 결과를 저장하면, 오래된 결과의 덮어쓰기를 JPA가 거부합니다. 이 경우 화면에서 저장된 결과를 다시 조회합니다.
- `TossProperties`, `PaymentConfiguration`: 결제창형 테스트 키, UI의 variantKey, 토스 주소, 연결 3초·응답 60초 제한 시간을 설정합니다.

## MVP의 처리 규칙

승인 결과가 불확실하면 같은 요청 안에서 GET으로 재조회합니다. 금액 불일치 취소는 취소 의도와 멱등키를 먼저 저장하며, 취소 응답을 못 받으면 한 번 더 GET으로 확인합니다. 최종 결과가 불분명하면 `REVIEW_REQUIRED`와 오류 로그를 남깁니다.

토스 호출 전체에 `@Transactional`을 붙이지 않습니다. `PaymentPreparationService`는 PG 호출 전에 거래·시도를 원자적으로 저장하고, `PaymentSettlementService`는 PG 호출 후 거래·시도·주문 상태를 함께 저장합니다. 결제 시도 UUID를 토스 승인 요청의 `Idempotency-Key`로 사용합니다.

중복 요청이나 저장 충돌 시 한 요청은 409를 받을 수 있습니다. 화면은 주문을 열거나 새로고침할 때 저장된 결과만 읽습니다. 서버 중단이나 DB 저장 실패로 남은 미확정 행은 주기적으로 재처리되지 않으므로 운영 확인이 필요합니다.

고객 임의 취소·웹훅·오래 남은 결제 시도의 자동 만료는 별도 확장 범위입니다. 여러 시도와 명확히 실패한 거래의 재시도는 구현했습니다. 현재 자동 취소는 같은 주문·키로 확인한 카드·국내 간편결제의 금액·통화 불일치에 한정합니다. 인증과 주문 접근 권한이 없는 로컬 테스트 예제라는 범위는 동일합니다.

공식 근거: [결제창형 연동 절차](https://docs.tosspayments.com/guides/v2/payment-widget/integration-window), [결제창형 SDK](https://docs.tosspayments.com/sdk/v2/js/payment-window), [API 인증·멱등키](https://docs.tosspayments.com/reference/using-api/authorization), [간편결제 응답](https://docs.tosspayments.com/guides/v2/easypay-response).
