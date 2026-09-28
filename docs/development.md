# 개발 가이드

실행 준비와 시작 명령은 [README](../README.md), 설정값은 [configuration.md](configuration.md)에 있습니다.

## 코드 위치

| 경로 | 역할 |
| --- | --- |
| `src/main/java/com/example/payment/product/` | 상품 카탈로그 |
| `src/main/java/com/example/payment/order/` | 주문 생성, 구매 항목, 조회 |
| `src/main/java/com/example/payment/payment/api/` | 결제 요청·응답 HTTP 계약 |
| `src/main/java/com/example/payment/payment/application/` | 승인 순서, 트랜잭션 경계, 미확정 승인 복구 |
| `src/main/java/com/example/payment/payment/domain/` | 결제 시도, 승인 상태, 성공한 결제 기록 |
| `src/main/java/com/example/payment/payment/infrastructure/toss/` | 토스 설정과 HTTP 연동 |
| `src/main/resources/db/migration/` | Flyway 스키마와 초기 상품 |
| `frontend/src/pages/` | 스토어, 상품, 장바구니, 결제 결과, 주문 화면 |
| `frontend/src/payments/` | SDK 결제창, 결제수단 제한, 인증 복귀 처리 |
| `frontend/src/api/` | 백엔드 API 호출 |
| `frontend/src/lib/` | 장바구니, 브라우저 저장소, 금액·날짜 표시 |
| `frontend/tests/` | SDK 대역을 사용한 결제창·인증 복귀 테스트 |

도메인 규칙과 저장 구조는 [architecture.md](architecture.md)에 있습니다.

## 백엔드 검증

Java 21과 실행 중인 Docker가 필요합니다. 저장소 루트에서 실행합니다.

```powershell
.\gradlew.bat test bootJar
```

macOS / Linux:

```sh
./gradlew test bootJar
```

통합 테스트는 Testcontainers가 생성한 PostgreSQL 18 컨테이너에 모든 마이그레이션을 적용합니다. Compose의 개발 DB나 실행 중인 백엔드는 필요하지 않습니다. 테스트용 토스 설정과 API 대역을 사용하므로 개인 테스트 키 없이 실행할 수 있습니다. 통합 테스트는 [복구 작업](configuration.md#결제-복구-작업)의 주기 실행을 끄고(`payment.recovery.enabled=false`) 복구 작업을 직접 호출합니다.

주요 검증 범위는 다음과 같습니다.

| 테스트 | 검증 범위 |
| --- | --- |
| [PaymentIntegrationTest](../src/test/java/com/example/payment/PaymentIntegrationTest.java) | 서버 가격 계산과 항목 스냅샷, 트랜잭션 원자성, 주문 승인 슬롯과 DB 제약, 동시 요청, 미확정 복구, 성공 결제 기록과 실패 후 재시도 |
| [TossPaymentClientTest](../src/test/java/com/example/payment/payment/infrastructure/toss/TossPaymentClientTest.java) | HTTP 요청의 인증·멱등 키·금액, 승인·조회 응답 검증, 거절·통신 오류·불일치 처리 |
| [MigrationUpgradeTest](../src/test/java/com/example/payment/MigrationUpgradeTest.java) | V1 데이터가 V2~V4를 거쳐 시도, 주문 슬롯, 성공 결제 기록으로 이전되고 미확정 시도의 완료 시각이 정리되는지 확인 |

주문·결제 도메인과 토스 설정 검증은 별도 단위 테스트가 있습니다. 이 테스트들은 실제 토스 결제창이나 외부 API를 호출하지 않습니다.

- 테스트 보고서: `build/reports/tests/test/index.html`
- 백엔드 실행 파일: `build/libs/payment-service-0.0.1-SNAPSHOT.jar`

`bootJar`는 백엔드 실행 파일을 생성합니다. React 정적 파일을 이 JAR에 포함시키는 설정은 없습니다.

## 프런트엔드 검증

Node.js 22.18 이상과 npm 사용을 권장합니다. Vite 자체의 지원 범위는 잠금 파일 기준 `^20.19.0 || >=22.12.0`이지만, 이 프로젝트의 테스트는 Node.js의 TypeScript 타입 제거 기능과 `Promise.withResolvers()`도 사용합니다. 의존성은 `npm ci`로 [잠금 파일](../frontend/package-lock.json)에 맞춰 설치합니다.

저장소 루트에서 실행합니다.

```sh
cd frontend
npm ci
npm test
npm run build
```

`npm test`는 Node.js 내장 테스트 러너와 SDK·브라우저 대역으로 결제창 중복 실행·닫기·화면 이탈, 지원 결제수단, 인증 복귀 URL, 결제 시도 식별, 임시 승인 정보 복원을 확인합니다. 실제 브라우저를 실행하는 E2E 테스트나 React 화면 렌더링 테스트는 포함하지 않습니다.

`npm run typecheck`는 TypeScript 검사만 수행하고, `npm run build`는 같은 검사 후 `frontend/dist/`를 생성합니다. 백엔드 JAR과 프런트엔드 산출물은 각각 생성됩니다.

화면을 변경할 때는 [DESIGN.md](../DESIGN.md)와 [styles.css](../frontend/src/styles.css)를 기준으로 PC·모바일, 키보드 포커스, 긴 상품명, 처리 중·오류 상태를 확인합니다.

## 화면과 브라우저 상태

스토어는 상품 목록·분류, 상품별 SVG 아바타 미리보기, `S`·`M`·`L`·`XL` 사이즈 선택을 제공합니다. 장바구니는 같은 상품·사이즈별 수량을 합치고 옵션당 최대 10장으로 제한합니다. 미리보기는 상품 일러스트이며 신체 치수나 사이즈별 착용감을 계산하지 않습니다.

장바구니와 최근·대기 주문번호는 `localStorage`에, 인증 후 아직 확인할 승인 정보는 `sessionStorage`에 보관합니다. 인증 복귀 직후 URL에서 결제 키를 제거하며, 저장소를 사용할 수 없을 때도 현재 요청은 진행합니다. 이 정보는 브라우저 편의 기능이며 계정별 주문 내역이나 주문 소유권 검증이 아닙니다.

장바구니에서 주문을 생성하면 담긴 항목을 비우고 대기 주문번호를 보관합니다. 인증을 취소했거나 승인이 확실히 실패한 주문은 장바구니 화면에서 같은 주문으로 다시 결제할 수 있습니다. 상품을 새로 담아 결제하면 별도 주문이 생성됩니다. 결과 화면과 주문 화면의 새로고침은 DB에 저장된 결과를 읽으며, 토스 조회는 서버의 승인 처리와 복구 작업이 담당합니다.

### 개발 서버의 경로 제한

현재 Vite의 API 프록시 경로 `/products`, `/orders`가 React 화면 경로와 겹칩니다. `/products/:productId`, `/orders`, `/orders/:orderId`를 주소창에서 직접 열거나 브라우저에서 새로고침하면, 페이지 요청이 백엔드로 전달되어 화면 대신 API 응답이나 오류가 표시될 수 있습니다.

개발 중에는 스토어 루트(`/`)로 접속한 뒤 화면의 링크로 상품·주문 페이지에 이동합니다. 내부 링크는 React Router를 통해 이동하므로 이 프록시 충돌을 피합니다. 주문 화면의 `주문 내역 새로고침` 버튼은 API 결과만 갱신하므로 브라우저 새로고침과 다릅니다. 이 제한은 화면 경로와 API 경로를 분리하거나 프록시 분기 처리를 추가해야 해결됩니다.

## 실제 SDK 연동 확인

백엔드 테스트의 토스 응답 검증과 브라우저 SDK 연동 확인은 별도입니다. 유효한 테스트 키로 스토어와 백엔드를 실행하고 다음을 확인합니다.

1. 스토어에서 상품·사이즈·수량을 선택해 주문을 생성하고 화면 금액과 DB 주문 금액을 비교합니다.
2. 결제창을 닫으면 인증 취소 시도가 기록되고, 같은 대기 주문에서 새 시도를 만들 수 있는지 확인합니다.
3. 카드와 국내 간편결제로 인증한 후 서버 승인 결과, DB 기록, 해당 테스트 상점의 결제내역을 비교합니다.
4. 인증 실패 시 승인 요청을 보내지 않고 주문과 시도 결과를 안내하는지 확인합니다.
5. 완료한 주문의 결과를 다시 조회하고, 같은 결제 정보를 다시 전송해도 새 토스 승인이 발생하지 않는지 확인합니다.

테스트 API 대역으로 확인한 동시 요청·응답 유실·DB 반영 실패와 실제 SDK의 결제수단 선택·리다이렉트는 검증 범위가 다릅니다. 자동 테스트 통과만으로 브라우저의 실제 결제 연동 확인을 대신하지 않습니다.

자신의 상점에서 발급받은 테스트 키로 만든 거래를 해당 상점의 결제내역과 비교합니다. 외부 결제 키나 시크릿 키를 이슈·문서·로그에 첨부하지 않습니다.

## 데이터베이스와 마이그레이션

현재 스키마는 다음 네 마이그레이션으로 만듭니다.

| 파일 | 내용 |
| --- | --- |
| [V1__initial_schema.sql](../src/main/resources/db/migration/V1__initial_schema.sql) | 상품·주문·항목·결제 시도·승인 거래 테이블과 초기 상품 |
| [V2__merge_payments_into_attempts.sql](../src/main/resources/db/migration/V2__merge_payments_into_attempts.sql) | 승인 거래를 결제 시도로 합치고 주문 승인 슬롯과 DB 제약 추가 |
| [V3__add_completed_payments.sql](../src/main/resources/db/migration/V3__add_completed_payments.sql) | 성공한 결제만 담는 `payments` 테이블 추가 |
| [V4__align_attempt_finished_at.sql](../src/main/resources/db/migration/V4__align_attempt_finished_at.sql) | V1에서 옮긴 미확정 시도의 완료 시각을 비우고, 종료 상태에만 완료 시각을 두는 제약 추가 |

처음 백엔드를 시작하면 Flyway가 V1부터 차례로 적용하고 초기 상품을 등록합니다. 이전 버전까지만 적용된 개발 DB는 다음 실행 때 남은 마이그레이션이 기존 데이터를 옮깁니다. 이후 서버를 다시 시작해도 적용된 마이그레이션은 반복 실행되지 않습니다. Hibernate는 스키마를 검증하며, 테이블 생성과 변경은 Flyway가 수행합니다.

스키마를 변경할 때는 이미 적용된 마이그레이션을 수정하지 않고 다음 버전의 파일을 추가합니다. SQL과 엔티티를 함께 변경하고 빈 DB와 이전 버전이 적용된 DB에서 모두 검증합니다. 새 버전을 추가하면 적용 버전을 확인하는 `PaymentIntegrationTest`와 기존 데이터 이전을 확인하는 `MigrationUpgradeTest`도 갱신합니다.

## 변경과 기여

수정한 동작에 맞는 테스트와 문서를 함께 갱신합니다.

- 주문 계산·승인·저장 흐름을 변경하면 백엔드 테스트를 실행합니다.
- 브라우저 결제 흐름을 변경하면 프런트엔드 테스트와 빌드를 실행합니다.
- API 계약을 변경하면 요청 검증, 응답 타입, 프런트엔드 호출, [API 문서](api.md)를 함께 맞춥니다.
- UI의 시각 기준을 변경하면 루트 `DESIGN.md`를 갱신합니다.
- PR에는 해결한 문제, 결과 동작, 검증 결과를 적습니다.

## 종료

로컬 서버와 컨테이너 종료 방법은 [README](../README.md#종료)에 있습니다. `docker compose stop`과 `docker compose down`의 차이와 데이터 보존 범위는 [설정 문서](configuration.md#로컬-postgresql과-pgadmin)를 참고합니다.
