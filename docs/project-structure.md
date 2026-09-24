# 프로젝트 디렉토리 구조와 폴더·파일별 역할

이 문서는 `payment-service` 프로젝트의 폴더별 역할을 설명합니다. 현재 상품·주문 처리 순서는 [코드 읽는 순서](code-reading-guide.md)를 함께 확인하세요.

직접 관리하는 소스·설정·문서는 파일별로 설명하고, 설치·빌드 과정에서 만들어지는 라이브러리와 캐시는 폴더 단위로 설명합니다. 결제 업무 흐름은 [기본 결제 흐름](payment-domain.md), 불확실한 승인 결과의 처리는 [재조회·조건부 취소](payment-recovery.md), 실행 방법은 [README](../README.md)를 함께 참고하세요.

## 1. 전체 구조 먼저 보기

```text
payment-service/
├─ src/
│  ├─ main/
│  │  ├─ java/com/example/payment/
│  │  │  ├─ PaymentServiceApplication.java
│  │  │  ├─ api/error/
│  │  │  │  ├─ ApiException.java
│  │  │  │  └─ ApiExceptionHandler.java
│  │  │  ├─ config/
│  │  │  │  └─ OpenApiConfiguration.java
│  │  │  ├─ order/
│  │  │  │  ├─ OrderController.java
│  │  │  │  ├─ OrderService.java
│  │  │  │  ├─ OrderRepository.java
│  │  │  │  ├─ OrderItemRepository.java
│  │  │  │  ├─ CreateOrderRequest.java
│  │  │  │  ├─ PurchaseOrder.java
│  │  │  │  ├─ OrderItem.java
│  │  │  │  └─ OrderResponse.java                주문·승인·취소 내역 응답
│  │  │  ├─ product/
│  │  │  │  ├─ Product.java
│  │  │  │  ├─ ProductCatalog.java
│  │  │  │  ├─ ProductController.java
│  │  │  │  ├─ ProductRepository.java
│  │  │  │  └─ ProductResponse.java
│  │  │  └─ payment/
│  │  │     ├─ api/                              결제 HTTP 요청·응답
│  │  │     │  ├─ PaymentController.java
│  │  │     │  ├─ PaymentAttemptController.java
│  │  │     │  └─ ConfirmPaymentRequest.java
│  │  │     ├─ application/                      결제 처리 순서
│  │  │     │  ├─ PaymentService.java
│  │  │     │  ├─ PaymentAttemptService.java
│  │  │     │  ├─ PaymentPreparationService.java
│  │  │     │  └─ PaymentSettlementService.java
│  │  │     ├─ domain/                           결제 상태·규칙
│  │  │     │  ├─ Payment.java
│  │  │     │  ├─ PaymentAttempt.java
│  │  │     │  ├─ PaymentStatus.java
│  │  │     │  ├─ PaymentAttemptStatus.java
│  │  │     │  └─ PaymentResult.java
│  │  │     ├─ persistence/                      JPA 저장소
│  │  │     │  ├─ PaymentRepository.java
│  │  │     │  └─ PaymentAttemptRepository.java
│  │  │     └─ infrastructure/toss/             토스 설정·HTTP 통신
│  │  │        ├─ PaymentConfiguration.java
│  │  │        ├─ TossProperties.java
│  │  │        └─ TossPaymentClient.java
│  │  └─ resources/
│  │     ├─ application.yml
│  │     └─ db/migration/
│  │        ├─ V1__create_orders_and_payments.sql
│  │        ├─ V2__simplify_payment_processing.sql
│  │        ├─ V3__automatic_payment_recovery.sql
│  │        ├─ V4__remove_periodic_payment_recovery.sql
│  │        ├─ V5__catalog_orders.sql
│  │        ├─ V6__create_products.sql
│  │        ├─ V7__order_items_entity.sql
│  │        ├─ V8__product_order_integrity.sql
│  │        └─ V9__order_payment_lifecycle.sql
│  └─ test/java/com/example/payment/
│     ├─ PaymentIntegrationTest.java
│     ├─ order/OrderServiceTest.java
│     └─ payment/infrastructure/toss/
│        ├─ TossPropertiesTest.java
│        └─ TossPaymentClientTest.java
├─ frontend/
│  ├─ src/
│  │  ├─ main.tsx
│  │  ├─ styles.css
│  │  ├─ vite-env.d.ts
│  │  ├─ api/paymentApi.ts                      Spring API 호출
│  │  ├─ components/
│  │  │  ├─ AppShell.tsx
│  │  │  ├─ ProductCard.tsx
│  │  │  ├─ CheckoutCard.tsx
│  │  │  ├─ OrderLookup.tsx
│  │  │  └─ OrderResultCard.tsx
│  │  ├─ lib/
│  │  │  ├─ formatters.ts
│  │  │  └─ storage.ts
│  │  ├─ pages/
│  │  │  ├─ StorePage.tsx
│  │  │  └─ PaymentResultPage.tsx
│  │  ├─ payments/
│  │  │  ├─ tossPayments.ts
│  │  │  ├─ paymentWindow.ts
│  │  │  └─ paymentRedirect.ts
│  │  └─ types/payment.ts
│  ├─ tests/
│  │  ├─ paymentRedirect.test.mjs
│  │  └─ paymentWindow.test.mjs
│  ├─ index.html
│  ├─ package.json / package-lock.json
│  ├─ tsconfig.json / vite.config.ts
│  └─ .gitignore
├─ docker/pgadmin/                              로컬 DB 접속 설정
├─ gradle/wrapper/                              Gradle 실행 도구
├─ docs/
│  ├─ project-structure.md
│  ├─ payment-domain.md
│  ├─ payment-recovery.md                       즉시 재조회·조건부 취소
│  ├─ code-reading-guide.md
│  ├─ environment-configuration.md
│  ├─ environment-variables.md
│  ├─ java-jpa-notes.md
│  └─ jpa-database-pipeline.md
├─ build.gradle / settings.gradle
├─ gradlew / gradlew.bat
├─ compose.yaml
├─ README.md
├─ .gitignore
└─ .gitattributes
```

`java/com/example/payment/`는 Java 패키지 경로입니다. 예를 들어 `order/OrderService.java`의 패키지 이름은 `com.example.payment.order`입니다. 중간의 `com`, `example`은 각각 별도 업무 기능을 뜻하는 폴더가 아닙니다.

## 2. 백엔드: `src/main/java/com/example/payment/`

Spring Boot 서버에서 실행하는 Java 코드입니다. 승인 응답이 불확실하면 같은 요청을 처리하는 동안 토스 결제를 재조회하고, 확인된 금액·통화 불일치를 취소합니다.

이 프로젝트는 `controller/`, `service/`, `repository/`를 각각 최상위 폴더로 만들지 않고, **주문은 `order/`, 결제는 `payment/`처럼 기능별로 모아 놓은 구조**입니다. 한 기능의 요청 처리·업무 처리·저장 코드를 같은 폴더에서 찾을 수 있습니다.

### 2.1. 기본 패키지의 시작 파일

| 파일 | 역할 |
| --- | --- |
| [PaymentServiceApplication.java](../src/main/java/com/example/payment/PaymentServiceApplication.java) | 서버 실행의 시작점입니다. `main()`에서 Spring Boot를 시작하고, 이 패키지 아래의 Controller·Service·설정 등을 찾아 등록하도록 합니다. |

### 2.2. `api/`: 공통 API 처리

현재 이 폴더에는 `error/`만 있습니다. 여러 API에서 공통으로 사용하는 오류 표현과 오류 응답 처리를 모아 둡니다. 주문·결제 요청을 받는 Controller는 각각 `order/`, `payment/`에 있습니다.

#### `api/error/`: 예외를 HTTP 오류 응답으로 바꾸기

| 파일 | 역할 |
| --- | --- |
| [ApiException.java](../src/main/java/com/example/payment/api/error/ApiException.java) | 주문 없음, 금액 불일치처럼 서비스가 예상하고 설명할 수 있는 업무 오류를 담습니다. HTTP 상태, 오류 코드, 안내 메시지를 함께 전달합니다. |
| [ApiExceptionHandler.java](../src/main/java/com/example/payment/api/error/ApiExceptionHandler.java) | Controller 처리 중 전파된 예외를 공통 `{code, message}` JSON 응답으로 바꿉니다. 입력 오류는 400, 저장 충돌은 409, DB 장애는 503 등으로 응답합니다. 내부 `ErrorResponse` record가 오류 응답의 모양을 정의합니다. |

예를 들어 `PaymentService`가 금액 불일치로 `ApiException`을 던지면, `ApiExceptionHandler`가 이를 프론트에서 읽을 수 있는 HTTP 오류 응답으로 변환합니다.

### 2.3. `config/`: 공통 API 문서 설정

서버 전체에 적용하는 OpenAPI 문서 설정을 둡니다. 토스 전용 설정은 `payment/infrastructure/toss/`에 있습니다.

| 파일 | 역할 |
| --- | --- |
| [OpenApiConfiguration.java](../src/main/java/com/example/payment/config/OpenApiConfiguration.java) | Swagger UI·OpenAPI 문서에 표시할 API 제목, 버전, 설명을 설정합니다. 개별 API 설명은 각 Controller에 있습니다. |
### 2.4. `payment/infrastructure/toss/`: 토스 설정과 외부 통신

토스 설정값, 전용 HTTP 클라이언트와 승인·조회·취소 요청 코드를 결제 기능 안에 모읍니다.

| 파일 | 역할 |
| --- | --- |
| [PaymentConfiguration.java](../src/main/java/com/example/payment/payment/infrastructure/toss/PaymentConfiguration.java) | 토스 전용 `RestClient`를 만듭니다. 토스 서버 주소, 시크릿 키를 사용하는 Basic 인증, 연결·응답 대기 시간을 설정합니다. |
| [TossProperties.java](../src/main/java/com/example/payment/payment/infrastructure/toss/TossProperties.java) | `application.yml`의 `payment.toss` 값을 Java 객체로 받습니다. 클라이언트 키·시크릿 키·두 UI variantKey를 보관하고 테스트 키 쌍의 형식을 검사합니다. |
| [TossPaymentClient.java](../src/main/java/com/example/payment/payment/infrastructure/toss/TossPaymentClient.java) | `confirm()`은 승인 POST, `lookup()`은 결제 GET 조회, `cancel()`은 `/v1/payments/{paymentKey}/cancel`에 전액 취소 POST를 보냅니다. 승인·조회 응답은 `readConfirmationResult()`·`readLookupResult()`가 각각 검사하며, 별도 GET에서 같은 거래의 승인 금액·통화 불일치를 확인한 경우 `CANCEL_PENDING`을 반환합니다. 취소 완료는 `CANCELED`·잔액 0·성공한 취소 이력까지 확인합니다. |

`TossPaymentClient` 안의 `TossPaymentResponse`는 결제 응답, `TossCancellation`은 취소 이력, `TossErrorResponse`는 오류 응답을 읽는 내부 record입니다. 호출 순서와 DB 저장은 `payment/application/`의 서비스가 담당합니다.

### 2.5. `product/`: 판매 상품 조회

서버가 저장한 상품 정보와 판매 가격을 읽습니다. 브라우저에서 보낸 가격으로 주문 금액을 계산하지 않습니다.

| 파일 | 역할 |
| --- | --- |
| [Product.java](../src/main/java/com/example/payment/product/Product.java) | `products` 테이블의 Entity입니다. 현재 상품명·가격·판매 여부를 보관합니다. |
| [ProductRepository.java](../src/main/java/com/example/payment/product/ProductRepository.java) | 판매 중인 상품을 DB에서 조회합니다. |
| [ProductCatalog.java](../src/main/java/com/example/payment/product/ProductCatalog.java) | 주문 요청의 상품 ID로 판매 중인 `Product`를 조회합니다. |
| [ProductController.java](../src/main/java/com/example/payment/product/ProductController.java) | 상품 목록과 상세 조회 API를 제공합니다. |
| [ProductResponse.java](../src/main/java/com/example/payment/product/ProductResponse.java) | 화면에 보여 줄 상품 데이터를 담는 응답 DTO입니다. |

### 2.6. `order/`: 주문 생성과 조회

무엇을 얼마에 주문했는지를 관리합니다. 서버가 판매 중인 상품을 조회하고, 그 상품의 현재 단가와 요청 수량으로 주문 금액을 계산합니다. 현재 구조는 [상품·주문·결제 도메인](shop-domain.md)을 참고하세요.

| 파일 | 역할 |
| --- | --- |
| [OrderController.java](../src/main/java/com/example/payment/order/OrderController.java) | `POST /orders`, `GET /orders/{orderId}` 요청을 받습니다. `OrderService`를 호출하고 주문 응답을 반환합니다. |
| [OrderService.java](../src/main/java/com/example/payment/order/OrderService.java) | 주문을 만들고 저장하거나, 기존 주문과 연결된 결제 결과를 조회합니다. 주문 생성과 조회의 트랜잭션 범위를 지정합니다. |
| [CreateOrderRequest.java](../src/main/java/com/example/payment/order/CreateOrderRequest.java) | 클라이언트가 보낸 상품 ID·사이즈·수량을 받습니다. 가격은 받지 않습니다. |
| [OrderRepository.java](../src/main/java/com/example/payment/order/OrderRepository.java) | `purchase_orders`의 주문 한 건을 저장하고 조회합니다. |
| [OrderItemRepository.java](../src/main/java/com/example/payment/order/OrderItemRepository.java) | `purchase_order_items`를 저장하고 `order_id`로 주문 항목을 조회합니다. |
| [PurchaseOrder.java](../src/main/java/com/example/payment/order/PurchaseOrder.java) | `purchase_orders` 테이블의 Entity입니다. 서버에서 계산한 총수량·금액과 화면용 요약을 저장합니다. |
| [OrderItem.java](../src/main/java/com/example/payment/order/OrderItem.java) | `purchase_order_items` 테이블의 Entity입니다. 주문·상품 외래 키와 구입 당시 상품명·단가·사이즈·수량을 보관합니다. |
| [OrderResponse.java](../src/main/java/com/example/payment/order/OrderResponse.java) | 프론트에 반환할 주문·결제 응답 DTO입니다. 상태별 안내와 실제 승인 금액·통화, 승인·취소 시각을 담습니다. 결제 행이 없으면 `READY`로 표현합니다. |

### 2.7. `payment/`: 승인·즉시 재조회·취소 결과 관리

결제수단 인증이 끝난 주문을 승인하고, 결과가 불확실하면 같은 요청에서 재조회·조건부 취소와 결과 저장을 이어서 처리합니다. `api/`는 HTTP 요청, `application/`은 처리 순서, `domain/`은 상태·규칙, `persistence/`는 DB 저장소를 맡습니다. 토스 연결은 위의 `infrastructure/toss/`에 있습니다.

| 파일 | 역할 |
| --- | --- |
| [PaymentController.java](../src/main/java/com/example/payment/payment/api/PaymentController.java) | `/payment-config`, `/payments/confirm` 요청을 받습니다. 공개 설정과 승인 결과를 반환하며 수동 PG 재확인 API는 제공하지 않습니다. |
| [PaymentAttemptController.java](../src/main/java/com/example/payment/payment/api/PaymentAttemptController.java) | 결제 시도 시작과 인증 취소·실패 기록 요청을 받습니다. |
| [PaymentService.java](../src/main/java/com/example/payment/payment/application/PaymentService.java) | 주문·금액 검증 → 결제 키 저장 → 토스 승인 순서를 관리합니다. 결과가 불확실하면 같은 요청에서 GET 재조회 후 필요할 때 취소 의도 저장 → 토스 취소 → 최종 결과 저장을 이어서 수행합니다. |
| [PaymentAttemptService.java](../src/main/java/com/example/payment/payment/application/PaymentAttemptService.java) | 주문에 연결된 결제 시도를 만들고 인증 취소·실패를 기록합니다. |
| [PaymentPreparationService.java](../src/main/java/com/example/payment/payment/application/PaymentPreparationService.java) | 토스 호출 전에 거래를 만들고 시도를 `PROCESSING`으로 바꿉니다. |
| [PaymentSettlementService.java](../src/main/java/com/example/payment/payment/application/PaymentSettlementService.java) | 승인 결과를 결제·시도·주문에 한 트랜잭션으로 반영합니다. |
| [PaymentRepository.java](../src/main/java/com/example/payment/payment/persistence/PaymentRepository.java) | 결제 ID·결제 키·주문번호로 거래를 조회하고 저장하는 Spring Data JPA 인터페이스입니다. |
| [PaymentAttemptRepository.java](../src/main/java/com/example/payment/payment/persistence/PaymentAttemptRepository.java) | 주문의 결제 시도 이력을 조회·저장합니다. |
| [Payment.java](../src/main/java/com/example/payment/payment/domain/Payment.java) | 결제 상태, 실제 승인 금액·통화, 승인·취소 시각, 취소 멱등키를 보관합니다. `applyResult()`로 결과를 반영하며, `@Version`이 늦은 저장으로 다른 결과를 덮어쓰지 못하게 합니다. |
| [PaymentAttempt.java](../src/main/java/com/example/payment/payment/domain/PaymentAttempt.java) | 결제창을 연 한 번의 시도와 인증·승인 결과를 보관합니다. |
| [ConfirmPaymentRequest.java](../src/main/java/com/example/payment/payment/api/ConfirmPaymentRequest.java) | 프론트가 승인 요청에 보내는 `orderId`, `paymentKey`, `amount`를 받는 DTO입니다. 빈 값·길이·숫자 범위 같은 입력 형식을 검사합니다. DB 금액과의 비교는 `PaymentService`가 합니다. |
| [PaymentResult.java](../src/main/java/com/example/payment/payment/domain/PaymentResult.java) | 해석한 토스 응답을 두 결제 서비스에 전달합니다. 서비스 상태·PG 상태·오류 코드·승인 시각·실제 승인 금액·통화·취소 시각을 담습니다. `unknown()`, `failed()`, `reviewRequired()`는 해당 결과 객체를 만드는 정적 메서드입니다. |
| [PaymentStatus.java](../src/main/java/com/example/payment/payment/domain/PaymentStatus.java) | `READY`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `UNKNOWN`, `CANCEL_PENDING`, `CANCELED`, `REVIEW_REQUIRED`를 정의합니다. `READY`는 결제 행이 없는 주문을 응답에서 표현하는 상태입니다. |
| [PaymentAttemptStatus.java](../src/main/java/com/example/payment/payment/domain/PaymentAttemptStatus.java) | 결제 시도의 시작·인증 취소·인증 실패·승인 결과 상태를 정의합니다. |

자동 취소는 카드·국내 간편결제에서 **주문번호와 결제키가 모두 일치하는 거래의 승인 금액·통화 불일치**를 확인했을 때 수행합니다. 식별자 불일치, 부분 취소 상태, 미지원 결제수단은 운영자 확인 대상으로 남깁니다. 취소 응답을 잃으면 같은 요청에서 GET으로 한 번 더 확인합니다.

### 2.8. 파일 이름에서 자주 보는 역할

| 이름·종류 | 이 프로젝트에서 하는 일 | 예 |
| --- | --- | --- |
| Controller | HTTP 요청을 받아 Service에 전달하고 응답을 돌려줍니다. | `OrderController` |
| Service | 업무 규칙과 작업 순서를 처리합니다. | `PaymentService` |
| Repository | Entity의 DB 저장·조회를 맡습니다. | `PaymentRepository` |
| Entity | DB 테이블과 연결되는 객체입니다. | `Product`, `PurchaseOrder`, `OrderItem`, `Payment` |
| Request·Response DTO | 요청·응답으로 전달할 데이터 모양입니다. | `ConfirmPaymentRequest`, `OrderResponse` |
| Client | 외부 서버로 HTTP 요청을 보냅니다. | `TossPaymentClient` |
| Configuration·Properties | 공통 도구와 설정값을 준비합니다. | `PaymentConfiguration`, `TossProperties` |

`PaymentResult`처럼 서버 내부 전달에 쓰는 DTO도 있습니다. `record`는 이런 데이터 객체를 간단히 작성하는 Java 문법이며, 이름이 `record`라고 해서 DB에 저장되는 것은 아닙니다.

## 3. 백엔드 리소스: `src/main/resources/`

Java 코드와 함께 서버 실행에 사용되는 설정과 SQL을 둡니다.

| 파일 | 역할 |
| --- | --- |
| [application.yml](../src/main/resources/application.yml) | DB 접속, JPA·Flyway, 토스 키와 UI, 서버 주소·포트, Swagger UI를 설정합니다. `${환경변수:기본값}` 형식으로 실행 환경의 값을 가져옵니다. |

### `db/migration/`: DB 구조의 변경 이력

Flyway가 서버 시작 시 아직 적용하지 않은 SQL 파일을 버전 순서대로 실행합니다. 새 DB도 V1부터 V4까지 적용된 상태가 현재 구조입니다.

| 파일 | 역할 |
| --- | --- |
| [V1__create_orders_and_payments.sql](../src/main/resources/db/migration/V1__create_orders_and_payments.sql) | 최초 주문·결제 테이블과 제약조건을 생성한 이력입니다. 이후 V2에서 제거한 예전 컬럼도 이 파일에는 남아 있습니다. |
| [V2__simplify_payment_processing.sql](../src/main/resources/db/migration/V2__simplify_payment_processing.sql) | 기본 승인 학습 단계의 구조 변경 이력입니다. 사용하지 않던 컬럼을 제거하고 `version`을 추가했습니다. |
| [V3__automatic_payment_recovery.sql](../src/main/resources/db/migration/V3__automatic_payment_recovery.sql) | 기존 데이터를 보존하며 자동 재조회·취소 일정, 멱등키, 실제 승인 금액·통화, 취소 시각과 상태 제약을 추가합니다. |
| [V4__remove_periodic_payment_recovery.sql](../src/main/resources/db/migration/V4__remove_periodic_payment_recovery.sql) | 주기적 복구 일정·시도 횟수 컬럼을 제거하고 이전 버전의 미확정 행을 운영 확인 대상으로 옮깁니다. |

이미 적용한 마이그레이션은 변경 이력이므로, 이후 DB 구조를 수정할 때는 새 버전 SQL을 추가하는 방식으로 관리합니다.

## 4. 백엔드 테스트: `src/test/java/com/example/payment/`

서버의 실제 동작이 기대한 규칙을 지키는지 확인하는 코드입니다. 서버 실행 코드와 별도로 테스트할 때 실행됩니다.

| 폴더 | 목적 |
| --- | --- |
| 기본 테스트 패키지 | HTTP 요청부터 업무 처리·DB 저장까지 연결한 통합 테스트를 둡니다. |
| `payment/infrastructure/toss/` | 토스 설정 검증과 외부 HTTP 요청·응답 해석을 확인합니다. |

| 파일 | 역할 |
| --- | --- |
| [PaymentIntegrationTest.java](../src/test/java/com/example/payment/PaymentIntegrationTest.java) | 별도 PostgreSQL에서 승인 요청 안의 즉시 재조회, 취소 의도 선저장, 취소 응답 유실 뒤 확인 조회, 운영 확인 상태와 마이그레이션을 검증합니다. 토스 호출은 모의 객체로 바꿉니다. |
| [TossPropertiesTest.java](../src/test/java/com/example/payment/payment/infrastructure/toss/TossPropertiesTest.java) | 신규 제품용 테스트 키의 허용, 기존 제품·운영·불완전한 키의 거부, UI 설정값 처리, 키 원문이 문자열 출력에 노출되지 않는지를 확인합니다. |
| [TossPaymentClientTest.java](../src/test/java/com/example/payment/payment/infrastructure/toss/TossPaymentClientTest.java) | 승인·조회·취소 URL, 인증·멱등키·요청 본문과 응답 해석을 모의 HTTP로 검증합니다. 재조회 후 취소 판단, 다른 거래의 취소 차단, 취소 상태·잔액·이력 검증, 응답 유실도 다룹니다. |

## 5. 프론트엔드: `frontend/`

브라우저에서 실행하는 React 화면과 개발·빌드 설정입니다. 사용자 입력을 받고 우리 서버 API를 호출하며, 토스 JS SDK로 결제수단 인증을 시작합니다.

### 5.1. `frontend/` 바로 아래의 파일

| 파일 | 역할 |
| --- | --- |
| [index.html](../frontend/index.html) | 브라우저가 처음 읽는 HTML입니다. React를 붙일 `root` 요소와 `main.tsx` 진입 경로, 기본 메타 정보를 제공합니다. |
| [package.json](../frontend/package.json) | React·토스 SDK 등의 의존성과 `dev`, `build`, `test`, `typecheck`, `preview` 명령을 정의합니다. |
| [package-lock.json](../frontend/package-lock.json) | 설치되는 의존성의 구체적인 버전과 의존 관계를 기록합니다. `npm ci`가 이 기록에 맞춰 설치합니다. |
| [tsconfig.json](../frontend/tsconfig.json) | TypeScript 검사 규칙, JSX 처리 방식, 검사 대상 파일 등을 정합니다. |
| [vite.config.ts](../frontend/vite.config.ts) | React 빌드 플러그인과 개발 서버를 설정합니다. 개발 중 `/orders`, `/payments`, `/payment-config` 요청을 Spring Boot로 전달하는 프록시도 설정합니다. |
| [.gitignore](../frontend/.gitignore) | 프론트의 `node_modules/`, `dist/`, `*.local` 파일을 Git 관리에서 제외합니다. |

### 5.2. `frontend/src/`: 화면 코드의 시작과 공통 설정

| 파일 | 역할 |
| --- | --- |
| [main.tsx](../frontend/src/main.tsx) | React를 시작하고 URL과 페이지를 연결합니다. `/`는 `StorePage`, `/payment/result`는 `PaymentResultPage`를 표시합니다. |
| [styles.css](../frontend/src/styles.css) | 스토어와 결과 화면의 배치, 색상, 글자, 버튼, 카드, 화면 크기별 스타일을 정의합니다. |
| [vite-env.d.ts](../frontend/src/vite-env.d.ts) | Vite가 제공하는 클라이언트 환경과 정적 파일 import에 대한 타입 선언을 가져옵니다. 화면을 그리는 실행 코드는 아닙니다. |

### 5.3. `frontend/src/api/`: 우리 서버 API 호출

React 화면에서 Spring Boot로 보내는 HTTP 요청을 모읍니다. 화면마다 URL과 오류 처리를 반복해서 작성하지 않도록 합니다.

| 파일 | 역할 |
| --- | --- |
| [paymentApi.ts](../frontend/src/api/paymentApi.ts) | 공개 설정, 주문 생성·조회, 승인 요청을 제공합니다. 프론트에는 PG 재확인 함수가 없습니다. 결제 결과를 담은 HTTP 422도 주문 응답으로 받습니다. |

서버의 `api/error/`는 **들어온 요청의 오류 응답을 만드는 곳**이고, 프론트의 `api/`는 **서버로 요청을 보내는 곳**입니다. 같은 `api`라는 이름이지만 역할이 다릅니다.

### 5.4. `frontend/src/components/`: 화면을 구성하는 UI 조각

상품 카드, 주문 요약, 조회 입력란 같은 화면 요소를 모읍니다. 표시할 데이터와 버튼 동작을 부모 페이지에서 전달받아 사용합니다.

| 파일 | 역할 |
| --- | --- |
| [AppShell.tsx](../frontend/src/components/AppShell.tsx) | 스토어와 결과 페이지의 공통 머리글·본문·바닥글 레이아웃입니다. 전달받은 페이지 내용을 공통 틀 안에 배치합니다. |
| [ProductCard.tsx](../frontend/src/components/ProductCard.tsx) | 판매하는 티셔츠의 상품 소개와 시각적 상품 카드를 표시합니다. |
| [CheckoutCard.tsx](../frontend/src/components/CheckoutCard.tsx) | 주문 요약, 금액, 주문 만들기·테스트 결제 버튼, 결제 설정 안내를 표시합니다. 주문 상태와 작업 중 여부에 따라 버튼을 표시하거나 비활성화합니다. |
| [OrderLookup.tsx](../frontend/src/components/OrderLookup.tsx) | 주문번호 입력란과 조회 버튼을 표시합니다. 버튼 클릭이나 Enter 입력을 부모의 조회 동작에 연결합니다. |
| [OrderResultCard.tsx](../frontend/src/components/OrderResultCard.tsx) | 주문 금액과 실제 승인 금액·상태·승인 및 취소 시각을 표시합니다. |

### 5.5. `frontend/src/lib/`: 여러 화면에서 쓰는 도우미

특정 페이지에 종속되지 않는 표시 형식과 브라우저 저장소 처리를 모읍니다.

| 파일 | 역할 |
| --- | --- |
| [formatters.ts](../frontend/src/lib/formatters.ts) | 금액을 `10,000원`처럼 표시하고, 날짜·시각과 결제 상태를 사용자에게 읽기 쉬운 표현으로 바꿉니다. |
| [storage.ts](../frontend/src/lib/storage.ts) | `localStorage`, `sessionStorage` 읽기·쓰기·삭제를 감쌉니다. 브라우저 저장소 접근이 제한되어도 저장소 오류가 화면 흐름을 중단하지 않도록 처리합니다. |

### 5.6. `frontend/src/pages/`: 페이지 전체의 상태와 동작

URL별 화면을 구성하고, API 호출·결제 실행·결과 표시 순서를 관리합니다. `components/`가 화면의 부품이라면 `pages/`는 그 부품을 연결해 실제 동작을 만드는 곳입니다.

| 파일 | 역할 |
| --- | --- |
| [StorePage.tsx](../frontend/src/pages/StorePage.tsx) | `/`의 스토어 화면입니다. 결제 설정과 마지막 주문을 불러오고 주문 생성·조회·결제창 열기를 연결합니다. 화면 이탈 시 진행 중인 결제창 작업을 정리합니다. |
| [PaymentResultPage.tsx](../frontend/src/pages/PaymentResultPage.tsx) | 인증 복귀 정보를 바탕으로 승인을 요청하고 서버가 반환한 최종 결과를 표시합니다. 요청 오류가 나면 저장된 주문을 다시 읽을 수 있습니다. |

### 5.7. `frontend/src/payments/`: 결제창과 인증 복귀

브라우저의 결제창 실행과 인증 복귀 URL 처리를 모읍니다. HTTP 요청은 `api/paymentApi.ts`를 사용합니다. 토스 재조회·취소는 승인 요청을 처리하는 서버 코드에서 이어서 수행합니다.

| 파일 | 역할 |
| --- | --- |
| [tossPayments.ts](../frontend/src/payments/tossPayments.ts) | 공개 클라이언트 키로 공식 SDK를 불러옵니다. 비회원용 `ANONYMOUS`로 `widgets()`를 초기화하고 `openPaymentWindow()`에 처리를 넘깁니다. |
| [paymentWindow.ts](../frontend/src/payments/paymentWindow.ts) | 금액과 UI 설정을 적용해 결제창형 UI를 엽니다. `paymentRequest` 이벤트에서 카드·국내 간편결제인지 확인한 뒤 인증을 요청합니다. 중복 이벤트, 창 닫기, 오류, 화면 이탈을 처리합니다. |
| [paymentRedirect.ts](../frontend/src/payments/paymentRedirect.ts) | 복귀 URL에서 주문번호·결제 키·금액·실패 사유를 읽습니다. 승인 정보를 형식 검사하고 같은 탭의 새로고침에 대비해 임시 저장·복원합니다. 처리 후 URL에서 `paymentKey`를 제거합니다. 서버 승인을 직접 요청하지는 않습니다. |

### 5.8. `frontend/src/types/`: 주고받는 데이터의 타입

프론트 코드가 주문·설정·결제 정보를 같은 형식으로 사용하도록 TypeScript 타입을 모읍니다.

| 파일 | 역할 |
| --- | --- |
| [payment.ts](../frontend/src/types/payment.ts) | 주문 응답 `Order`, 실제 승인 금액·통화·취소 시각을 포함한 `PaymentDetails`, 8가지 `PaymentStatus`, 공개 설정과 승인 요청 타입을 정의합니다. 타입은 개발 중 검사에 사용하며 서버 입력 검증은 별도로 수행합니다. |

### 5.9. `frontend/tests/`: 브라우저 결제 처리의 자동 테스트

Node 내장 테스트 도구를 사용합니다. 브라우저 저장소·SDK·주문 조회를 모의 객체로 대체하여 처리 규칙을 확인합니다.

| 파일 | 역할 |
| --- | --- |
| [paymentRedirect.test.mjs](../frontend/tests/paymentRedirect.test.mjs) | 인증 성공·실패 URL, 주문번호 없는 취소, 잘못된 금액, 임시 승인 정보 복원, 저장소 사용 불가 상황을 확인합니다. |
| [paymentWindow.test.mjs](../frontend/tests/paymentWindow.test.mjs) | 금액 설정과 창 열기 순서, 선택 후 인증 요청, 중복 이벤트 방지, 닫기 후 재시도, 미지원 수단 차단, 오류·페이지 이탈 시 정리를 확인합니다. |

## 6. 개발 환경과 빌드 도구

### 6.1. 프로젝트 최상위의 설정 파일

| 파일 | 역할 |
| --- | --- |
| [build.gradle](../build.gradle) | 백엔드의 Java 버전, Spring Boot·JPA·DB 드라이버 등의 의존성, JUnit 테스트 실행을 설정합니다. |
| [settings.gradle](../settings.gradle) | Gradle 프로젝트 이름을 `payment-service`로 지정합니다. |
| [gradlew](../gradlew) | macOS·Linux 등에서 프로젝트에 지정된 Gradle을 실행하는 Wrapper 스크립트입니다. |
| [gradlew.bat](../gradlew.bat) | Windows에서 같은 Gradle Wrapper를 실행하는 스크립트입니다. |
| [compose.yaml](../compose.yaml) | 로컬 PostgreSQL과 pgAdmin 컨테이너의 이미지·포트·접속 설정·상태 확인·데이터 보존 볼륨을 정의합니다. Spring Boot와 React는 이 파일에서 실행하지 않습니다. |
| [README.md](../README.md) | 프로젝트 소개, 실행 순서, 결제 흐름, API와 테스트 방법, 상세 문서 링크를 모은 첫 안내서입니다. |
| [.gitignore](../.gitignore) | 백엔드 빌드 결과, Gradle 캐시, IDE 설정 등을 Git 관리에서 제외합니다. |
| [.gitattributes](../.gitattributes) | 텍스트 파일 자동 감지와 줄바꿈 정규화 등 Git의 파일 처리 규칙을 설정합니다. |

### 6.2. `gradle/wrapper/`: 지정된 Gradle 실행 준비

개발 환경마다 동일한 Gradle 버전으로 빌드할 수 있게 하는 도구입니다.

| 파일 | 역할 |
| --- | --- |
| [gradle-wrapper.properties](../gradle/wrapper/gradle-wrapper.properties) | 사용할 Gradle 배포본 주소와 다운로드·저장 설정을 기록합니다. |
| [gradle-wrapper.jar](../gradle/wrapper/gradle-wrapper.jar) | `gradlew`가 사용하는 Wrapper 실행 코드입니다. 필요한 Gradle을 준비하고 실행하는 바이너리 파일입니다. |

### 6.3. `docker/pgadmin/`: pgAdmin의 DB 연결 준비

`compose.yaml`이 pgAdmin 컨테이너에 연결해 주는 로컬 설정 파일을 둡니다.

| 파일 | 역할 |
| --- | --- |
| [servers.json](../docker/pgadmin/servers.json) | pgAdmin에 `Payment Service` DB 연결 항목을 미리 등록합니다. Docker 내부 PostgreSQL 주소, 포트, DB 이름, 사용자 등을 지정합니다. |
| [pgpass](../docker/pgadmin/pgpass) | pgAdmin이 로컬 PostgreSQL 접속에 사용할 비밀번호 파일입니다. 현재 로컬 개발용 DB 연결을 위해 사용합니다. |

## 7. `docs/`: 프로젝트 설명과 학습 문서

실행 코드를 보완하는 설명 자료입니다. 목적에 맞는 문서를 골라 읽을 수 있도록 나뉘어 있습니다.

| 파일 | 역할 |
| --- | --- |
| [project-structure.md](project-structure.md) | 현재 문서입니다. 폴더 계층과 파일별 책임을 찾아보는 구조 안내서입니다. |
| [payment-domain.md](payment-domain.md) | 주문·인증·승인·즉시 재조회·취소·내역 조회의 업무 흐름, 식별자, 사용하는 결제 제품과 구현 범위를 설명합니다. |
| [payment-recovery.md](payment-recovery.md) | 승인 요청 안의 재조회와 취소 판단, 취소 의도 선저장, 불확실한 응답과 서버 중단 시의 한계를 설명합니다. |
| [code-reading-guide.md](code-reading-guide.md) | 실제 코드에서 어떤 파일과 메서드를 어떤 순서로 읽으면 되는지 안내합니다. DB 컬럼과 처리 규칙도 설명합니다. |
| [environment-configuration.md](environment-configuration.md) | Spring Boot·React·PostgreSQL·pgAdmin의 실행 구성과 각 설정 파일의 관계를 설명합니다. |
| [environment-variables.md](environment-variables.md) | 환경변수의 의미, 프로젝트에서 사용하는 변수, 토스 키·UI 설정과 PowerShell 설정 방법을 설명합니다. |
| [java-jpa-notes.md](java-jpa-notes.md) | Repository, EntityManager와 현재 프로젝트의 트랜잭션에 관한 짧은 JPA 참고 메모입니다. |
| [jpa-database-pipeline.md](jpa-database-pipeline.md) | 애플리케이션 시작부터 Repository·Hibernate·JDBC·PostgreSQL까지 이어지는 내부 처리 경로와 소스 읽기·디버깅 지점을 자세히 설명합니다. |

## 8. 작업 중 보이는 도구·자동 생성 폴더

아래 항목은 개발 도구나 설치·빌드 결과를 보관합니다. 생성 파일의 세부 목록은 환경과 실행 상태에 따라 달라집니다.

| 폴더·파일 | 역할 |
| --- | --- |
| `.git/` | Git의 커밋 이력, 브랜치, 저장소 메타데이터입니다. |
| `.gradle/` | 이 프로젝트의 Gradle 작업 캐시와 빌드 상태입니다. |
| `build/` | 백엔드 컴파일 결과, 실행 JAR, 테스트 결과·리포트가 생성됩니다. 예: `build/libs/`, `build/reports/tests/test/`. |
| `.idea/` | IntelliJ IDEA의 프로젝트 설정입니다. |
| `.codex/` | Codex가 프로젝트에서 사용하는 도구 설정 폴더입니다. 현재 `config.toml`이 있으며, 서버나 프론트의 결제 실행 코드에 포함되지 않습니다. |
| `frontend/node_modules/` | npm으로 설치한 프론트 라이브러리입니다. 직접 작성한 앱 코드는 `frontend/src/`에 있습니다. |
| `frontend/dist/` | `npm run build`로 만들어지는 프론트 배포용 HTML·JavaScript·CSS입니다. |

## 9. 작업별로 어느 파일을 보면 되나요?

| 하고 싶은 일 | 먼저 볼 곳 |
| --- | --- |
| 주문 상품·수량·금액 변경 | `order/OrderService.java`, `PurchaseOrder.java`, DB 제약조건과 프론트 상품·주문 표시 |
| 주문·결제 API 경로 확인 | `OrderController.java`, `PaymentController.java`, `frontend/src/api/paymentApi.ts` |
| 서버의 승인 처리 순서 확인 | `payment/application/PaymentService.java` |
| 승인 결과 불확실 시 즉시 처리 순서 확인 | `payment/application/PaymentService.java`의 `verifyAndCancelIfNeeded()`, [재조회·취소](payment-recovery.md) |
| 자동 취소 조건과 토스 취소 응답 검증 | `payment/infrastructure/toss/TossPaymentClient.java`의 `readLookupResult()`, `readCancellationLookupResult()`, `cancel()`, `canceledResult()` |
| 취소 멱등키·취소 의도 저장 확인 | `payment/domain/Payment.java`, `payment/persistence/PaymentRepository.java`, V3·V4 마이그레이션 |
| 토스에 보내는 HTTP 요청 확인 | `payment/infrastructure/toss/TossPaymentClient.java`, `PaymentConfiguration.java` |
| 토스 키·결제 UI 설정 변경 | `application.yml`, `payment/infrastructure/toss/TossProperties.java`, [환경변수 문서](environment-variables.md) |
| 결제창 열기·닫기·선택 처리 변경 | `frontend/src/payments/tossPayments.ts`, `paymentWindow.ts` |
| 인증 후 결과 처리 확인 | `paymentRedirect.ts`, `pages/PaymentResultPage.tsx` |
| 주문 화면의 저장된 결과 확인 | `frontend/src/api/paymentApi.ts`, `pages/StorePage.tsx`, `pages/PaymentResultPage.tsx` |
| 서버 오류 응답 변경 | `api/error/ApiExceptionHandler.java` |
| 주문·결제 데이터 저장 구조 확인 | `PurchaseOrder.java`, `Payment.java`, `resources/db/migration/` |
| 화면 구성·스타일 변경 | `frontend/src/pages/`, `components/`, `styles.css` |

파일들이 연결되는 대표 경로는 다음과 같습니다.

```text
주문 생성
StorePage → paymentApi → OrderController → OrderService → OrderRepository → DB

결제수단 인증
StorePage → tossPayments → paymentWindow → 토스 SDK·결제창

인증 후 최종 승인과 결과 확인
paymentRedirect → PaymentResultPage → paymentApi → PaymentController
→ PaymentService → TossPaymentClient → 토스 승인 API
→ 승인 결과가 확실하면 PaymentRepository에 저장
→ 불확실하면 PaymentService.verifyAndCancelIfNeeded()
  → TossPaymentClient.lookup() → 토스 결제 GET 조회
    ├─ 같은 거래의 정상 승인 → SUCCEEDED 저장
    ├─ 같은 거래의 승인 금액·통화 불일치
    │  → CANCEL_PENDING·취소 멱등키·실제 승인 금액을 DB에 저장
    │  → TossPaymentClient.cancel() → 토스 취소 POST
    │  → 취소 완료 검증 → CANCELED·취소 시각 저장
    └─ 확인 불가·식별자 문제 → REVIEW_REQUIRED 저장

주문·결제·취소 내역 표시
StorePage 또는 PaymentResultPage → paymentApi.getOrder() → GET /orders/{orderId}
→ OrderController → OrderService → DB 조회 → OrderResponse → 화면 표시
```

승인 경로는 결제키를, 취소 경로는 취소 의도와 멱등키를 외부 호출 전에 저장합니다. 주문 조회는 저장된 내역만 읽습니다. 서버 중단으로 남은 미확정 행은 자동 재처리되지 않으므로 운영 확인이 필요합니다. 저장 순서는 [코드 읽는 순서](code-reading-guide.md), 처리 한계는 [재조회·조건부 취소](payment-recovery.md)에 설명합니다.
