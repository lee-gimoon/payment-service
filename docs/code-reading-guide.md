# 코드 주석과 읽는 순서

이 프로젝트의 소스에는 학습을 위해 파일 첫머리에 역할을, 클래스·타입·함수 위에는 책임과 처리 규칙을 적었습니다. Java에는 Javadoc, TypeScript에는 JSDoc 형식의 `/** ... */` 주석을 사용합니다. 편집기에서 선언이나 호출에 마우스를 올리면 설명을 확인할 수 있습니다.

## 주석은 보통 어떻게 작성하나요?

모든 파일과 함수에 같은 분량의 설명을 붙이는 보편적인 정답은 없습니다. 팀 규칙에 따라 다르지만, 이름만으로 알기 어려운 책임·입력 조건·반환 결과·오류·설계 이유를 기록하는 방식이 유용합니다. 코드의 동작이 바뀌면 주석도 함께 수정해야 합니다.

| 위치 | 적으면 도움이 되는 내용 | 이 프로젝트의 예 |
| --- | --- | --- |
| 파일 첫머리 | 전체 흐름에서 맡는 역할 | `paymentApi.ts`는 React와 Spring Boot API 사이의 요청·응답 처리 |
| 클래스·타입 위 | 객체의 책임과 다른 객체와의 관계 | `PaymentService`는 DB 작업 선점 → PG 호출 → 결과 저장의 순서를 조율 |
| 함수·메서드 위 | 무엇을 처리하며 어떤 조건·결과가 있는지 | `claimConfirmation()`은 금액을 확인하고 새 결제 시도를 저장하거나 기존 결과를 반환 |
| 함수 내부 | 코드만 보고 놓치기 쉬운 이유 | PG 응답을 기다리는 동안 DB 잠금을 유지하지 않는 이유 |
| 테스트 위 | 어떤 상황에서 어떤 결과를 기대하는지 | 승인 후 DB 저장에 실패해도 PG 조회로 복구할 수 있는지 |

Java에서 파일 하나에 클래스 하나만 있으면 파일 설명과 클래스 설명을 합치기도 합니다. 이번에는 파일을 처음 여는 학습 상황을 고려해 둘 다 붙였습니다. 단순 getter도 요청에 맞춰 설명했지만, 일반적인 유지보수 코드에서는 이름만으로 충분한 getter의 주석을 생략하기도 합니다. 필요한 경우 Javadoc에 `@param`, `@return`, `@throws`로 입력·반환·예외 조건을 더 자세히 작성할 수 있습니다.

React의 `StorePage()`와 `CheckoutCard()`도 함수입니다. 화면을 반환하는 함수는 컴포넌트, 클릭에 반응하는 함수는 이벤트 처리 함수, `runAction()`처럼 다른 함수를 실행하는 함수는 공통 처리 도우미로 읽으면 됩니다. 짧은 익명 콜백은 감싸는 함수의 설명과 함께 읽고, 초기화 effect와 정리 함수처럼 별도의 역할이 있는 곳에는 내부 주석도 붙였습니다.

## 먼저 주문 생성 한 건을 따라가세요

| 순서 | 코드 | 확인할 내용 |
| --- | --- | --- |
| 1 | [CheckoutCard.tsx](../frontend/src/components/CheckoutCard.tsx)의 주문 만들기 버튼 | `onClick={onCreateOrder}`로 부모가 전달한 함수 호출 |
| 2 | [StorePage.tsx](../frontend/src/pages/StorePage.tsx)의 `handleCreateOrder()` | `runAction()`을 거쳐 `createOrder()` 실행 |
| 3 | [paymentApi.ts](../frontend/src/api/paymentApi.ts)의 `createOrder()`와 `request()` | `fetch`로 본문 없는 `POST /orders` 요청 |
| 4 | [OrderController.java](../src/main/java/com/example/payment/order/OrderController.java)의 `create()` | 요청 본문 검사 후 주문 서비스 호출 |
| 5 | [OrderService.java](../src/main/java/com/example/payment/order/OrderService.java)의 `create()` | 트랜잭션 안에서 주문 생성·저장 |
| 6 | [PurchaseOrder.java](../src/main/java/com/example/payment/order/PurchaseOrder.java)의 `tShirt()` | 주문번호 발급과 상품·수량·금액 결정 |
| 7 | [OrderRepository.java](../src/main/java/com/example/payment/order/OrderRepository.java) | 상속받은 `save()`의 구현은 Spring Data JPA가 제공 |
| 8 | [OrderResponse.java](../src/main/java/com/example/payment/order/OrderResponse.java)의 `of()` | 결제 기록이 없는 주문을 `READY` 응답으로 변환 |
| 9 | `OrderController` → `paymentApi` → `StorePage.showOrder()` | HTTP 201 응답이 돌아오고 React state 갱신으로 주문 표시 |

각 단계에서 **입력값 → 호출한 함수 → 데이터 변경 → 반환값**을 확인하세요. `runAction()`의 `busyRef`는 현재 화면의 중복 실행을 막는 보조 장치입니다. 서버의 중복 결제 처리는 별도로 `PaymentTransactions`와 DB 제약조건이 담당합니다.

## 다음으로 결제 승인과 재확인을 읽으세요

1. [StorePage.tsx](../frontend/src/pages/StorePage.tsx)의 `handlePayment()`가 최신 주문을 확인합니다.
2. [tossPayments.ts](../frontend/src/payments/tossPayments.ts)의 `openTossPayment()`가 카드 인증창을 엽니다.
3. 인증 후 [paymentRedirect.ts](../frontend/src/payments/paymentRedirect.ts)가 복귀 URL을 읽고 [PaymentResultPage.tsx](../frontend/src/pages/PaymentResultPage.tsx)가 서버에 승인을 요청합니다.
4. [PaymentController.java](../src/main/java/com/example/payment/payment/PaymentController.java) → [PaymentService.java](../src/main/java/com/example/payment/payment/PaymentService.java) → [PaymentTransactions.java](../src/main/java/com/example/payment/payment/PaymentTransactions.java)의 `claimConfirmation()` 순으로 진행합니다.
5. 새 시도의 식별 정보와 `PROCESSING`을 커밋한 뒤 [TossPaymentGateway.java](../src/main/java/com/example/payment/gateway/TossPaymentGateway.java)가 PG에 승인을 요청합니다.
6. `PaymentTransactions.finish()` → [Payment.java](../src/main/java/com/example/payment/payment/Payment.java)의 `complete()`로 결과를 반영하고 별도 트랜잭션으로 저장합니다.
7. 결과가 미확정이면 `reconcile()`이 `claimReconciliation()` → PG `lookup()` → `finish()`를 거쳐 결과를 재확인합니다.

`READY`는 아직 결제 기록이 없는 주문의 응답 상태입니다. `UNKNOWN`은 통신 오류 등의 결과로 DB에 저장되기도 하지만, 처리 기한이 지난 `PROCESSING`을 `visibleStatus()`가 응답에서만 `UNKNOWN`으로 보여주기도 합니다. 조회했다고 항상 DB 상태가 바뀌는 것은 아닙니다.

정상 흐름을 먼저 읽은 뒤 [PaymentTest.java](../src/test/java/com/example/payment/payment/PaymentTest.java), [TossPaymentGatewayTest.java](../src/test/java/com/example/payment/gateway/TossPaymentGatewayTest.java), [PaymentIntegrationTest.java](../src/test/java/com/example/payment/PaymentIntegrationTest.java)에서 실패·중복·복구 시나리오를 대조하세요. 통합 테스트는 별도 PostgreSQL 컨테이너를 사용하며 PG 응답은 모의 구현입니다.

## 설정과 보조 파일의 역할

설정 파일은 클래스나 함수가 없어도 애플리케이션 동작에 영향을 줍니다. 주석을 지원하는 설정에는 파일 역할을 적었고, JSON·생성 도구의 파일·기존 DB 마이그레이션은 아래에서 설명합니다.

| 파일 | 역할 |
| --- | --- |
| [build.gradle](../build.gradle) | 백엔드 Java 버전·라이브러리·빌드·테스트 설정 |
| [settings.gradle](../settings.gradle) | Gradle 프로젝트 이름 |
| [gradlew](../gradlew), [gradlew.bat](../gradlew.bat) | 운영체제별 Gradle Wrapper 실행 스크립트. 도구가 생성한 표준 파일 |
| [gradle-wrapper.properties](../gradle/wrapper/gradle-wrapper.properties) | Wrapper가 내려받아 사용할 Gradle 배포판과 다운로드 설정 |
| `gradle/wrapper/gradle-wrapper.jar` | 지정한 Gradle을 준비하고 실행하는 바이너리 도구 |
| [compose.yaml](../compose.yaml) | PostgreSQL·pgAdmin 컨테이너와 포트·볼륨 연결 |
| [application.yml](../src/main/resources/application.yml) | Spring Boot의 DB·JPA·Flyway·토스·웹 서버 설정 |
| [V1__create_orders_and_payments.sql](../src/main/resources/db/migration/V1__create_orders_and_payments.sql) | 주문·결제 테이블, 고정 상품 제약, 주문과 결제 금액 일치, 결제 키 유일성, 복구 조회용 인덱스 생성 |
| [servers.json](../docker/pgadmin/servers.json) | pgAdmin이 사용할 PostgreSQL 연결 정보 등록 |
| [pgpass](../docker/pgadmin/pgpass) | pgAdmin에서 로컬 예제 DB에 연결할 때 사용할 암호 파일 |
| [frontend/package.json](../frontend/package.json) | React 의존성과 개발·빌드·타입 검사 명령 |
| [frontend/package-lock.json](../frontend/package-lock.json) | npm이 기록한 실제 의존성 버전·다운로드 정보 |
| [frontend/tsconfig.json](../frontend/tsconfig.json) | TypeScript 타입 검사·모듈·JSX 해석 설정 |
| [frontend/vite.config.ts](../frontend/vite.config.ts) | React 개발 서버·빌드 플러그인·개발용 API 프록시 |
| [frontend/index.html](../frontend/index.html) | React를 붙일 root 요소와 프런트엔드 시작 스크립트 |
| [main.tsx](../frontend/src/main.tsx) | React 시작과 스토어·결제 결과 화면 라우팅 |
| [vite-env.d.ts](../frontend/src/vite-env.d.ts) | Vite 클라이언트 환경·정적 파일 import 타입 선언 |
| [styles.css](../frontend/src/styles.css) | 두 화면의 공통 스타일과 작은 화면 배치 |
| [.gitignore](../.gitignore), [frontend/.gitignore](../frontend/.gitignore) | Git에 넣지 않을 빌드 결과·로컬 도구 파일 패턴 |
| [.gitattributes](../.gitattributes) | Git이 파일 형식과 줄바꿈을 다루는 규칙 |
| [README.md](../README.md) | 전체 기능·아키텍처·결제 시퀀스·실행 방법 |
| [payment-domain.md](payment-domain.md) | 결제 업무 개념·범위·설계·복구 규칙 |
| [environment-configuration.md](environment-configuration.md) | 로컬 개발 환경 구성 안내 |
| [environment-variables.md](environment-variables.md) | 실행 환경변수 설정 안내 |
| [code-reading-guide.md](code-reading-guide.md) | 현재 문서. 주석 작성 기준과 코드를 따라 읽는 순서 |

표준 JSON은 주석을 지원하지 않으므로 `package.json`이나 `servers.json` 안에 설명을 삽입하지 않습니다. `tsconfig.json`은 주석을 허용하지만, 여기서는 설정 파일들의 역할을 위 표에서 함께 안내합니다. 이미 적용된 Flyway 버전 마이그레이션은 주석 수정도 체크섬에 영향을 줄 수 있어 원문을 유지합니다. 이 프로젝트의 `V1`에서 `READY`가 상태 제약에 없는 이유는 결제 시도 전에는 결제 행 자체가 없기 때문입니다.
