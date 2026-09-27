# 개발 가이드

실행 준비와 시작 명령은 [README](../README.md), 설정값은 [configuration.md](configuration.md)에 있습니다.

## 코드 위치

| 경로 | 역할 |
| --- | --- |
| `src/main/java/com/example/payment/product/` | 상품 카탈로그 |
| `src/main/java/com/example/payment/order/` | 주문 생성, 구매 항목, 조회 |
| `src/main/java/com/example/payment/payment/api/` | 결제 요청·응답 HTTP 계약 |
| `src/main/java/com/example/payment/payment/application/` | 승인 순서와 트랜잭션 경계 |
| `src/main/java/com/example/payment/payment/domain/` | 결제 거래·시도 상태 |
| `src/main/java/com/example/payment/payment/infrastructure/toss/` | 토스 설정과 HTTP 연동 |
| `src/main/resources/db/migration/` | Flyway 스키마와 초기 상품 |
| `frontend/src/pages/` | 스토어, 상품, 장바구니, 결제 결과, 주문 화면 |
| `frontend/src/payments/` | SDK 결제창과 인증 복귀 처리 |
| `frontend/src/api/` | 백엔드 API 호출 |
| `frontend/tests/` | 결제창·인증 복귀 테스트 |

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

통합 테스트는 Testcontainers가 생성한 PostgreSQL 18 컨테이너에 새 V1을 적용합니다. 애플리케이션의 개발 DB는 사용하지 않습니다. 토스 API는 테스트 대역을 사용하므로 이 명령으로 실제 결제 인증이나 승인을 수행하지 않습니다.

테스트는 서버 금액 계산, 항목 스냅샷, 거래·시도·주문의 저장 원자성, 중복·동시 요청, 승인 응답 검증, 불확실한 결과 재조회, 실패 후 재시도를 검증합니다.

- 테스트 보고서: `build/reports/tests/test/index.html`
- 백엔드 실행 파일: `build/libs/payment-service-0.0.1-SNAPSHOT.jar`

`bootJar`는 백엔드 실행 파일을 생성합니다. React 정적 파일을 이 JAR에 포함시키는 설정은 없습니다.

## 프런트엔드 검증

`frontend/`에서 실행합니다.

```sh
npm ci
npm test
npm run build
```

테스트는 결제창 중복 실행·닫기·화면 이탈, 지원 결제수단, 인증 복귀 URL과 결제 시도 식별을 확인합니다. `npm run build`는 TypeScript 검사 후 `frontend/dist/`를 생성합니다.

화면을 변경할 때는 [DESIGN.md](../DESIGN.md)와 [styles.css](../frontend/src/styles.css)를 기준으로 PC·모바일, 키보드 포커스, 긴 상품명, 처리 중·오류 상태를 확인합니다.

## 실제 SDK 연동 확인

백엔드 테스트의 토스 응답 검증과 브라우저 SDK 연동 확인은 별도입니다. 유효한 테스트 키로 스토어와 백엔드를 실행하고 다음을 확인합니다.

1. 상품·사이즈·수량으로 주문을 생성하고 화면 금액과 DB 주문 금액을 비교합니다.
2. 결제창을 닫으면 인증 취소 시도가 기록되고, 같은 대기 주문에서 새 시도를 만들 수 있는지 확인합니다.
3. 카드와 국내 간편결제로 인증한 후 서버 승인 결과, DB 기록, 해당 테스트 상점의 결제내역을 비교합니다.
4. 인증 실패 시 승인 요청을 보내지 않고 주문과 시도 결과를 안내하는지 확인합니다.
5. 완료한 주문의 결과를 다시 조회하고, 같은 결제 정보를 다시 전송해도 새 토스 승인이 발생하지 않는지 확인합니다.

자신의 상점에서 발급받은 테스트 키로 만든 거래를 해당 상점의 결제내역과 비교합니다. 외부 결제 키나 시크릿 키를 이슈·문서·로그에 첨부하지 않습니다.

## 데이터베이스와 마이그레이션

현재 스키마는 [V1__initial_schema.sql](../src/main/resources/db/migration/V1__initial_schema.sql) 하나입니다. Hibernate는 스키마를 검증하며, 테이블 생성과 변경은 Flyway가 수행합니다.

이전 V1~V10을 적용한 DB는 새 V1과 이력이 일치하지 않습니다. 기존 데이터를 보존하려면 별도 개발 DB를 만들고 백엔드 연결을 변경합니다. 기본 Compose 환경에서 새 이름의 DB를 생성하는 예시입니다.

```powershell
docker compose exec postgres createdb -U payment payment_service_v1
$env:PAYMENT_DB_URL = 'jdbc:postgresql://localhost:5432/payment_service_v1'
.\gradlew.bat bootRun
```

이 명령은 `payment_service_v1`이 아직 없을 때 한 번 실행합니다. 기존 `payment_service` DB와 Docker 볼륨은 유지됩니다. pgAdmin에서는 생성한 DB를 선택해 조회합니다.

앞으로 공유·배포한 스키마를 변경할 때는 기존 마이그레이션을 수정하지 않고 다음 버전의 파일을 추가합니다. SQL과 엔티티를 함께 변경하고 새 DB 적용 및 이전 버전에서의 전환을 검증합니다.

## 변경과 기여

수정한 동작에 맞는 테스트와 문서를 함께 갱신합니다.

- 주문 계산·승인·저장 흐름을 변경하면 백엔드 테스트를 실행합니다.
- 브라우저 결제 흐름을 변경하면 프런트엔드 테스트와 빌드를 실행합니다.
- API 계약을 변경하면 요청 검증, 응답 타입, 프런트엔드 호출, [API 문서](api.md)를 함께 맞춥니다.
- UI의 시각 기준을 변경하면 루트 `DESIGN.md`를 갱신합니다.
- PR에는 해결한 문제, 결과 동작, 검증 결과를 적습니다.

## 종료

백엔드와 Vite 터미널에서 `Ctrl+C`로 프로세스를 종료한 뒤 저장소 루트에서 실행합니다.

```sh
docker compose down
```

Compose 종료 후에도 명명된 Docker 볼륨의 DB 데이터는 유지됩니다.
