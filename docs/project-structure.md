# 프로젝트 디렉토리 구조와 폴더·파일별 역할

이 문서는 현재 `payment-service` 프로젝트에서 **각 폴더에 무엇을 모아 두었는지, 그 안의 파일이 어떤 일을 하는지** 설명합니다. 신규 결제창형 SDK를 사용하는 현재 코드를 기준으로 작성했습니다.

직접 관리하는 소스·설정·문서는 파일별로 설명하고, 설치·빌드 과정에서 만들어지는 라이브러리와 캐시는 폴더 단위로 설명합니다. 결제 업무 흐름은 [기본 결제 흐름](payment-domain.md), 실행 방법은 [README](../README.md)를 함께 참고하세요.

## 1. 전체 구조 먼저 보기

```text
payment-service/
├─ src/                              Spring Boot 백엔드 코드와 테스트
│  ├─ main/                          서버 실행에 사용되는 코드와 설정
│  │  ├─ java/com/example/payment/   Java 기본 패키지
│  │  │  ├─ PaymentServiceApplication.java
│  │  │  ├─ api/
│  │  │  │  └─ error/                공통 API 오류 처리
│  │  │  ├─ config/                 Spring 설정과 토스 연결 설정
│  │  │  ├─ gateway/                외부 토스 서버와 HTTP 통신
│  │  │  ├─ order/                  주문 생성·조회·저장
│  │  │  └─ payment/                결제 승인·결과 저장·재확인
│  │  └─ resources/
│  │     ├─ application.yml         서버 실행 설정
│  │     └─ db/migration/           DB 구조 변경 이력
│  └─ test/java/com/example/payment/
│     ├─ PaymentIntegrationTest.java
│     ├─ config/                    토스 설정 테스트
│     └─ gateway/                   토스 HTTP 통신 테스트
├─ frontend/                         React 프론트엔드
│  ├─ src/
│  │  ├─ main.tsx                   React 시작과 URL 연결
│  │  ├─ styles.css                 공통 화면 스타일
│  │  ├─ vite-env.d.ts              Vite 환경 타입 선언
│  │  ├─ api/                       우리 서버의 HTTP API 호출
│  │  ├─ components/                화면을 구성하는 UI 조각
│  │  ├─ lib/                       공통 표시·브라우저 저장소 함수
│  │  ├─ pages/                     URL별 화면과 동작 관리
│  │  ├─ payments/                  토스 SDK와 인증 복귀 처리
│  │  └─ types/                     주문·결제 데이터의 타입
│  ├─ tests/                        프론트엔드 자동 테스트
│  └─ 설정 파일들                   package.json, vite.config.ts 등
├─ docker/pgadmin/                   pgAdmin의 로컬 DB 접속 설정
├─ gradle/wrapper/                   Gradle 실행 도구
├─ docs/                             프로젝트 설명 문서
├─ build.gradle                      백엔드 의존성과 빌드 설정
├─ settings.gradle                   Gradle 프로젝트 이름
├─ gradlew / gradlew.bat              Gradle 실행 스크립트
├─ compose.yaml                      PostgreSQL·pgAdmin 실행 설정
├─ README.md                         프로젝트 소개와 실행 방법
├─ .gitignore                        Git에서 제외할 파일 규칙
└─ .gitattributes                    Git의 파일 처리 규칙
```

`java/com/example/payment/`는 Java 패키지 경로입니다. 예를 들어 `order/OrderService.java`의 패키지 이름은 `com.example.payment.order`입니다. 중간의 `com`, `example`은 각각 별도 업무 기능을 뜻하는 폴더가 아닙니다.

## 2. 백엔드: `src/main/java/com/example/payment/`

Spring Boot 서버에서 실행하는 Java 코드입니다. 브라우저가 보낸 요청을 받고, 주문과 결제 규칙을 검사하며, DB와 토스 서버를 사용합니다.

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

### 2.3. `config/`: 서버와 외부 연결의 설정

Spring이 사용할 설정 객체와 공통 도구를 준비하는 폴더입니다. 여기서 준비한 객체를 Service나 외부 통신 코드가 주입받아 사용합니다.

| 파일 | 역할 |
| --- | --- |
| [OpenApiConfiguration.java](../src/main/java/com/example/payment/config/OpenApiConfiguration.java) | Swagger UI·OpenAPI 문서에 표시할 API 제목, 버전, 설명을 설정합니다. 개별 API 설명은 각 Controller에 있습니다. |
| [PaymentConfiguration.java](../src/main/java/com/example/payment/config/PaymentConfiguration.java) | 토스 전용 `RestClient`를 만듭니다. 토스 서버 주소, 시크릿 키를 사용하는 Basic 인증, 연결·응답 대기 시간을 설정합니다. |
| [TossProperties.java](../src/main/java/com/example/payment/config/TossProperties.java) | `application.yml`의 `payment.toss` 값을 Java 객체로 받습니다. 클라이언트 키·시크릿 키·두 UI variantKey를 보관하고, 결제창형 테스트 키 쌍의 형식을 검사합니다. 키가 모두 없으면 주문 기능만 사용할 수 있도록 합니다. |

`application.yml`이 설정값을 적는 곳이라면, `TossProperties`는 그 값을 코드에서 읽는 형태이고, `PaymentConfiguration`은 그 값으로 HTTP 통신 도구를 만드는 곳입니다.

### 2.4. `gateway/`: 외부 토스 서버와 통신

우리 서버에서 외부 결제 서비스로 요청을 보내는 코드를 모읍니다. 토스의 HTTP 응답을 우리 서비스가 사용할 결과로 해석합니다.

| 파일 | 역할 |
| --- | --- |
| [TossPaymentClient.java](../src/main/java/com/example/payment/gateway/TossPaymentClient.java) | 토스 승인 `POST /v1/payments/confirm`과 조회 `GET /v1/payments/{paymentKey}`를 호출합니다. 응답의 주문번호·키·금액·통화·상태·결제수단을 확인해 `PaymentResult`로 바꿉니다. 타임아웃처럼 결과를 확신할 수 없는 경우에는 `UNKNOWN`으로 처리합니다. |

이 파일 안의 `TossPaymentResponse`, `TossErrorResponse` record는 토스의 JSON을 읽기 위한 내부 데이터 형식입니다. DB 저장은 `PaymentService`와 Repository가 담당합니다.

### 2.5. `order/`: 주문 생성과 조회

무엇을 얼마에 주문했는지를 관리합니다. 현재 상품은 티셔츠 1장, 금액은 10,000원으로 서버가 결정합니다.

| 파일 | 역할 |
| --- | --- |
| [OrderController.java](../src/main/java/com/example/payment/order/OrderController.java) | `POST /orders`, `GET /orders/{orderId}` 요청을 받습니다. `OrderService`를 호출하고 주문 응답을 반환합니다. |
| [OrderService.java](../src/main/java/com/example/payment/order/OrderService.java) | 주문을 만들고 저장하거나, 기존 주문과 연결된 결제 결과를 조회합니다. 주문 생성과 조회의 트랜잭션 범위를 지정합니다. |
| [OrderRepository.java](../src/main/java/com/example/payment/order/OrderRepository.java) | `PurchaseOrder`를 저장·조회하는 Spring Data JPA 인터페이스입니다. `save()`, `findById()` 등의 실제 구현은 Spring Data JPA가 제공합니다. |
| [PurchaseOrder.java](../src/main/java/com/example/payment/order/PurchaseOrder.java) | `purchase_orders` 테이블과 연결되는 Entity입니다. 주문번호, 상품명, 수량, 총금액, 생성 시각을 보관합니다. 새 주문번호는 UUID로 만듭니다. |
| [OrderResponse.java](../src/main/java/com/example/payment/order/OrderResponse.java) | 프론트에 반환할 주문·결제 응답 DTO입니다. Entity를 응답 형태로 바꾸고 상태별 안내 문구를 넣습니다. 내부 `PaymentResponse`에는 결제 상태·시각·오류·재확인 가능 여부를 담습니다. 결제 행이 없으면 `READY`로 표현합니다. |

### 2.6. `payment/`: 결제 승인과 결과 관리

결제수단 인증이 끝난 주문을 최종 승인하고, 결과를 저장하거나 다시 확인하는 기능을 모읍니다.

| 파일 | 역할 |
| --- | --- |
| [PaymentController.java](../src/main/java/com/example/payment/payment/PaymentController.java) | `/payment-config`, `/payments/confirm`, `/payments/{orderId}/reconcile` 요청을 받습니다. 브라우저용 공개 설정을 반환하고, 결제 처리 결과에 따라 HTTP 200·202·422를 선택합니다. 내부 `PublicConfig`에는 클라이언트 키와 UI 설정만 포함하고 시크릿 키는 제외합니다. |
| [PaymentService.java](../src/main/java/com/example/payment/payment/PaymentService.java) | 주문·금액 검증 → 중복 확인 → 결제 키 저장 → 토스 승인 → 결과 저장 순서를 관리합니다. `reconcile()`은 기존 결제를 토스에서 조회해 결과를 다시 확인합니다. |
| [PaymentRepository.java](../src/main/java/com/example/payment/payment/PaymentRepository.java) | `Payment`를 저장·조회하는 JPA 인터페이스입니다. 결제의 기본 키가 주문번호이므로 `findById(orderId)`로 해당 주문의 결제를 찾습니다. |
| [Payment.java](../src/main/java/com/example/payment/payment/Payment.java) | `payments` 테이블과 연결되는 Entity입니다. 주문번호, 토스 결제 키, 처리 상태와 시각 등을 보관합니다. `applyResult()`로 결과를 반영하고, `@Version`으로 동시 저장 충돌을 검사합니다. |
| [ConfirmPaymentRequest.java](../src/main/java/com/example/payment/payment/ConfirmPaymentRequest.java) | 프론트가 승인 요청에 보내는 `orderId`, `paymentKey`, `amount`를 받는 DTO입니다. 빈 값·길이·숫자 범위 같은 입력 형식을 검사합니다. DB 금액과의 비교는 `PaymentService`가 합니다. |
| [PaymentResult.java](../src/main/java/com/example/payment/payment/PaymentResult.java) | 토스 응답을 해석한 뒤 서버 내부에서 전달하는 결과 DTO입니다. 우리 결제 상태, 토스 상태, 오류 코드, 승인 시각을 담아 `TossPaymentClient`에서 `PaymentService`로 전달합니다. |
| [PaymentStatus.java](../src/main/java/com/example/payment/payment/PaymentStatus.java) | `READY`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `UNKNOWN`이라는 우리 서비스의 결제 상태를 정의하는 enum입니다. `READY`는 주문만 있고 결제 행은 없는 상태를 응답에서 표현할 때 사용합니다. |

### 2.7. 파일 이름에서 자주 보는 역할

| 이름·종류 | 이 프로젝트에서 하는 일 | 예 |
| --- | --- | --- |
| Controller | HTTP 요청을 받아 Service에 전달하고 응답을 돌려줍니다. | `OrderController` |
| Service | 업무 규칙과 작업 순서를 처리합니다. | `PaymentService` |
| Repository | Entity의 DB 저장·조회를 맡습니다. | `PaymentRepository` |
| Entity | DB 테이블과 연결되는 객체입니다. | `PurchaseOrder`, `Payment` |
| Request·Response DTO | 요청·응답으로 전달할 데이터 모양입니다. | `ConfirmPaymentRequest`, `OrderResponse` |
| Client | 외부 서버로 HTTP 요청을 보냅니다. | `TossPaymentClient` |
| Configuration·Properties | 공통 도구와 설정값을 준비합니다. | `PaymentConfiguration`, `TossProperties` |

`PaymentResult`처럼 서버 내부 전달에 쓰는 DTO도 있습니다. `record`는 이런 데이터 객체를 간단히 작성하는 Java 문법이며, 이름이 `record`라고 해서 DB에 저장되는 것은 아닙니다.

## 3. 백엔드 리소스: `src/main/resources/`

Java 코드와 함께 서버 실행에 사용되는 설정과 SQL을 둡니다.

| 파일 | 역할 |
| --- | --- |
| [application.yml](../src/main/resources/application.yml) | DB 접속, JPA·Flyway, 토스 키와 UI 설정, 서버 주소·포트, Swagger UI 경로를 설정합니다. `${환경변수:기본값}` 형식으로 실행 환경의 값을 가져옵니다. |

### `db/migration/`: DB 구조의 변경 이력

Flyway가 서버 시작 시 아직 적용하지 않은 SQL 파일을 버전 순서대로 실행합니다. 새 DB도 V1을 거쳐 V2까지 적용된 상태가 현재 구조입니다.

| 파일 | 역할 |
| --- | --- |
| [V1__create_orders_and_payments.sql](../src/main/resources/db/migration/V1__create_orders_and_payments.sql) | 최초 주문·결제 테이블과 제약조건을 생성한 이력입니다. 이후 V2에서 제거한 예전 컬럼도 이 파일에는 남아 있습니다. |
| [V2__simplify_payment_processing.sql](../src/main/resources/db/migration/V2__simplify_payment_processing.sql) | 기존 행을 유지하면서 사용하지 않는 컬럼을 제거하고 결제 테이블에 `version`을 추가합니다. 현재 Entity와 맞는 구조로 변경합니다. |

이미 적용한 마이그레이션은 변경 이력이므로, 이후 DB 구조를 수정할 때는 새 버전 SQL을 추가하는 방식으로 관리합니다.

## 4. 백엔드 테스트: `src/test/java/com/example/payment/`

서버의 실제 동작이 기대한 규칙을 지키는지 확인하는 코드입니다. 서버 실행 코드와 별도로 테스트할 때 실행됩니다.

| 폴더 | 목적 |
| --- | --- |
| 기본 테스트 패키지 | HTTP 요청부터 업무 처리·DB 저장까지 연결한 통합 테스트를 둡니다. |
| `config/` | 설정값의 허용·거부 조건을 확인합니다. |
| `gateway/` | 외부 HTTP 요청 형식과 응답 해석을 확인합니다. |

| 파일 | 역할 |
| --- | --- |
| [PaymentIntegrationTest.java](../src/test/java/com/example/payment/PaymentIntegrationTest.java) | Testcontainers의 별도 PostgreSQL에서 주문·승인·조회·중복 요청·동시성·DB 변경 이력 등을 확인합니다. 토스 호출만 모의 객체로 바꿔 실제 결제 없이 서버 흐름을 검증합니다. |
| [config/TossPropertiesTest.java](../src/test/java/com/example/payment/config/TossPropertiesTest.java) | 신규 제품용 테스트 키의 허용, 기존 제품·운영·불완전한 키의 거부, UI 설정값 처리, 키 원문이 문자열 출력에 노출되지 않는지를 확인합니다. |
| [gateway/TossPaymentClientTest.java](../src/test/java/com/example/payment/gateway/TossPaymentClientTest.java) | 모의 HTTP 응답으로 승인·조회 URL, 인증 헤더, 멱등키, 요청 본문과 결과 해석을 검증합니다. 금액 불일치·타임아웃·거절·간편결제 응답 등을 다룹니다. |

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
| [paymentApi.ts](../frontend/src/api/paymentApi.ts) | 공개 결제 설정 조회, 주문 생성·조회, 승인, PG 결과 재확인 함수를 제공합니다. 공통 `request()`가 `fetch`와 JSON 응답을 처리하고, `ApiRequestError`가 서버 오류를 화면으로 전달합니다. 결제 결과를 담은 HTTP 422는 조회할 결과로 받아들입니다. |

서버의 `api/error/`는 **들어온 요청의 오류 응답을 만드는 곳**이고, 프론트의 `api/`는 **서버로 요청을 보내는 곳**입니다. 같은 `api`라는 이름이지만 역할이 다릅니다.

### 5.4. `frontend/src/components/`: 화면을 구성하는 UI 조각

상품 카드, 주문 요약, 조회 입력란 같은 화면 요소를 모읍니다. 표시할 데이터와 버튼 동작을 부모 페이지에서 전달받아 사용합니다.

| 파일 | 역할 |
| --- | --- |
| [AppShell.tsx](../frontend/src/components/AppShell.tsx) | 스토어와 결과 페이지의 공통 머리글·본문·바닥글 레이아웃입니다. 전달받은 페이지 내용을 공통 틀 안에 배치합니다. |
| [ProductCard.tsx](../frontend/src/components/ProductCard.tsx) | 판매하는 티셔츠의 상품 소개와 시각적 상품 카드를 표시합니다. |
| [CheckoutCard.tsx](../frontend/src/components/CheckoutCard.tsx) | 주문 요약, 금액, 주문 만들기·테스트 결제 버튼, 결제 설정 안내를 표시합니다. 주문 상태와 작업 중 여부에 따라 버튼을 표시하거나 비활성화합니다. |
| [OrderLookup.tsx](../frontend/src/components/OrderLookup.tsx) | 주문번호 입력란과 조회 버튼을 표시합니다. 버튼 클릭이나 Enter 입력을 부모의 조회 동작에 연결합니다. |
| [OrderResultCard.tsx](../frontend/src/components/OrderResultCard.tsx) | 스토어에서 주문의 결제 상태·시각·오류를 표시하고, 저장 결과 조회·PG 결과 재확인 버튼을 제공합니다. |

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
| [StorePage.tsx](../frontend/src/pages/StorePage.tsx) | `/`의 스토어 화면입니다. 결제 설정과 마지막 주문을 불러오고 주문 생성·조회·결제창 열기를 연결합니다. 작업 중 중복 클릭을 막으며, 페이지를 떠날 때 진행 중인 결제창 작업을 정리하도록 알립니다. |
| [PaymentResultPage.tsx](../frontend/src/pages/PaymentResultPage.tsx) | `/payment/result`의 결과 화면입니다. 인증 복귀 정보를 바탕으로 서버에 승인을 요청하거나 기존 주문을 조회합니다. 인증 실패 안내와 저장 결과 조회·PG 재확인·동일 승인 요청 재전송을 관리합니다. |

### 5.7. `frontend/src/payments/`: 토스 결제창과 복귀 처리

우리 서버 호출을 담당하는 `api/`와 별도로, 브라우저에서 토스 SDK를 사용하고 인증 결과 URL을 처리하는 코드를 모읍니다.

| 파일 | 역할 |
| --- | --- |
| [tossPayments.ts](../frontend/src/payments/tossPayments.ts) | 공개 클라이언트 키로 공식 SDK를 불러옵니다. 비회원용 `ANONYMOUS`로 `widgets()`를 초기화하고 `openPaymentWindow()`에 처리를 넘깁니다. |
| [paymentWindow.ts](../frontend/src/payments/paymentWindow.ts) | 금액과 UI 설정을 적용해 결제창형 UI를 엽니다. `paymentRequest` 이벤트에서 카드·국내 간편결제인지 확인한 뒤 인증을 요청합니다. 중복 이벤트, 창 닫기, 오류, 화면 이탈을 처리합니다. |
| [paymentRedirect.ts](../frontend/src/payments/paymentRedirect.ts) | 복귀 URL에서 주문번호·결제 키·금액·실패 사유를 읽습니다. 승인 정보를 형식 검사하고 같은 탭의 새로고침에 대비해 임시 저장·복원합니다. 처리 후 URL에서 `paymentKey`를 제거합니다. 서버 승인을 직접 요청하지는 않습니다. |

### 5.8. `frontend/src/types/`: 주고받는 데이터의 타입

프론트 코드가 주문·설정·결제 정보를 같은 형식으로 사용하도록 TypeScript 타입을 모읍니다.

| 파일 | 역할 |
| --- | --- |
| [payment.ts](../frontend/src/types/payment.ts) | 주문 응답 `Order`, 결제 요약 `PaymentDetails`, 상태 `PaymentStatus`, 공개 설정 `PaymentConfig`, 승인 요청 `ConfirmPaymentCommand`를 정의합니다. 이 타입은 개발 중 검사를 위한 것이며 실제 서버 입력 검증을 대신하지 않습니다. |

### 5.9. `frontend/tests/`: 브라우저 결제 처리의 자동 테스트

Node 내장 테스트 도구를 사용합니다. 브라우저 저장소와 SDK를 모의 객체로 대체하여 처리 규칙을 확인합니다.

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
| [payment-domain.md](payment-domain.md) | 주문부터 인증·승인·조회까지의 업무 흐름, 식별자, 사용하는 결제 제품과 MVP 범위를 설명합니다. |
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
| 서버의 승인 처리 순서 확인 | `payment/PaymentService.java` |
| 토스에 보내는 HTTP 요청 확인 | `gateway/TossPaymentClient.java`, `config/PaymentConfiguration.java` |
| 토스 키·결제 UI 설정 변경 | `application.yml`, `config/TossProperties.java`, [환경변수 문서](environment-variables.md) |
| 결제창 열기·닫기·선택 처리 변경 | `frontend/src/payments/tossPayments.ts`, `paymentWindow.ts` |
| 인증 후 결과 처리 확인 | `paymentRedirect.ts`, `pages/PaymentResultPage.tsx` |
| 서버 오류 응답 변경 | `api/error/ApiExceptionHandler.java` |
| 주문·결제 데이터 저장 구조 확인 | `PurchaseOrder.java`, `Payment.java`, `resources/db/migration/` |
| 화면 구성·스타일 변경 | `frontend/src/pages/`, `components/`, `styles.css` |

파일들이 연결되는 대표 경로는 다음과 같습니다.

```text
주문 생성
StorePage → paymentApi → OrderController → OrderService → OrderRepository → DB

결제수단 인증
StorePage → tossPayments → paymentWindow → 토스 SDK·결제창

인증 후 최종 승인
paymentRedirect → PaymentResultPage → paymentApi → PaymentController
→ PaymentService → TossPaymentClient → 토스 승인 API
→ PaymentService → PaymentRepository → DB
```

승인 경로에서 `PaymentService`는 토스 호출 전에 결제 키를 먼저 저장하고, 호출 후에 승인 결과를 저장합니다. 각 단계의 의미와 예외 처리는 [코드 읽는 순서](code-reading-guide.md)에 이어서 정리되어 있습니다.
