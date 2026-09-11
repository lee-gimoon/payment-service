# payment-service

Java 21과 Spring Boot 4.1 기반의 독립 결제 서비스입니다.

## 개발 계획과 결제 도메인 문서

[결제 서비스 개발 계획과 기본 개념](docs/payment-domain.md)은 SDLC와 애자일의 의미를 설명하고, 주문부터 결제 결과 조회까지 연결되는 초기 MVP의 목표·범위·도메인·설계·완료 기준을 정리합니다. MVP를 사용해본 피드백에 따라 개선할 내용을 정하며, 문서는 한 파일로 관리하고 진행에 맞춰 보충합니다.

[로컬 개발 환경 설정](docs/environment-configuration.md)은 `application.yml`과 `compose.yaml`의 차이, Spring Boot·PostgreSQL·pgAdmin의 실행 위치와 연결 구조, 전체 프로젝트의 실행 순서를 설명합니다.

[환경변수 이해하기](docs/environment-variables.md)는 환경변수의 이름과 값, Spring Boot가 기본값을 선택하는 방식, PowerShell과 IntelliJ에서 값을 전달하는 방법을 설명합니다.

## 로컬 실행

PostgreSQL을 실행합니다.

```powershell
docker compose up -d
```

이 명령은 PostgreSQL과 브라우저용 pgAdmin을 함께 실행합니다. pgAdmin은 [http://127.0.0.1:5050](http://127.0.0.1:5050)에서 열 수 있습니다.

```text
pgAdmin 로그인 이메일: admin@payment-service.com
pgAdmin 로그인 비밀번호: payment_admin_local
```

로그인하면 `Servers → Local Development → Payment Service → Databases → payment_service → Schemas → public → Tables`에 현재 테이블이 표시됩니다. 서버 연결 정보와 로컬 DB 비밀번호는 `docker/pgadmin/servers.json`, `docker/pgadmin/pgpass`로 미리 등록되어 있습니다. 이 프로젝트 전용 pgAdmin은 시작할 때 해당 서버 정의를 다시 적용합니다.

테이블을 우클릭한 뒤 `View/Edit Data → All Rows`를 선택하면 행을 표로 볼 수 있습니다. 데이터베이스를 선택하고 `Tools → Query Tool`을 열면 SQL을 직접 실행할 수 있습니다.

pgAdmin 로그인 정보를 바꾸려면 처음 컨테이너를 만들기 전에 환경변수를 설정합니다.

```powershell
$env:PGADMIN_DEFAULT_EMAIL = '원하는 로컬 로그인 이메일'
$env:PGADMIN_DEFAULT_PASSWORD = '원하는 로컬 로그인 비밀번호'
docker compose up -d
```

pgAdmin 설정은 `payment-service-pgadmin-data` 볼륨에, 실제 주문·결제 데이터는 `payment-service-postgres-data` 볼륨에 각각 저장됩니다. `docker compose down`은 두 볼륨을 보존합니다. 로컬 PostgreSQL만 실행하려면 `docker compose up -d postgres`를 사용합니다. 이 기본 계정과 HTTP 설정은 `127.0.0.1`에만 공개되는 로컬 개발 전용입니다.

토스페이먼츠 개발자센터의 **API 개별 연동 키**에서 같은 테스트 상점의 키 한 쌍을 설정합니다. 결제위젯 키(`test_gck_` / `test_gsk_`)가 아닌 `test_ck_` / `test_sk_` 키를 사용합니다. 키는 Git에 저장하지 않습니다.

```powershell
$env:TOSS_CLIENT_KEY = '발급받은 test_ck_ 클라이언트 키'
$env:TOSS_SECRET_KEY = '발급받은 test_sk_ 시크릿 키'
```

애플리케이션을 실행하고 [로컬 스토어](http://127.0.0.1:8080)를 엽니다.

```powershell
.\gradlew.bat bootRun
```

키를 둘 다 생략하면 주문 생성·조회는 가능하고 결제 승인은 `503 PAYMENT_NOT_CONFIGURED`로 차단됩니다. 라이브 키, 한쪽만 설정한 키, 위젯 키는 시작 시 거부합니다. 테스트 키 여부만 검사하므로 실제 유효성과 동일 상점 여부는 토스페이먼츠에서 확인합니다.

로컬 기본 연결 정보는 `application.yml`과 `compose.yaml`에 맞춰져 있습니다. 서버는 기본적으로 `127.0.0.1:8080`에 바인딩합니다. 인증과 주문별 접근 권한을 제외한 학습용 MVP이므로 배포 시에는 접근을 제한한 테스트 환경을 사용합니다.

배포 환경에서는 다음 환경변수로 실제 연결 정보를 주입합니다.

- `PAYMENT_DB_URL`
- `PAYMENT_DB_USERNAME`
- `PAYMENT_DB_PASSWORD`
- `TOSS_CLIENT_KEY`
- `TOSS_SECRET_KEY`
- `PAYMENT_BIND_ADDRESS` (컨테이너에서는 `0.0.0.0`)
- `PORT` (기본 `8080`)

## 구현된 MVP

티셔츠 1장, 10,000원, KRW 고정입니다. 주문 생성 → 토스 카드 인증 → 서버 승인 → 결과 저장·조회 흐름을 제공합니다. 취소·배송·정산·상품 관리·로그인은 범위에 포함하지 않습니다.

| API | 요청 / 응답 |
| --- | --- |
| `POST /orders` | 빈 본문 또는 `{}`. 서버가 상품·수량·금액·주문번호를 정하고 `201`과 `Location`을 반환합니다. 다른 필드를 보내면 `400`입니다. |
| `GET /orders/{orderId}` | 주문과 결제 상태를 `200`으로 반환합니다. PG를 호출하지 않습니다. |
| `POST /payments/confirm` | `orderId`, `paymentKey`, `amount`로 승인합니다. 완료 `200`, 거절 `422`, 처리 중/확인 필요 `202`입니다. |
| `POST /payments/{orderId}/reconcile` | 확인 필요 상태의 결제를 PG에서 조회해 저장합니다. 이미 처리 중이거나 확정된 결과에는 추가 PG 호출 없이 현재 결과를 반환합니다. |
| `GET /payment-config` | 브라우저용 `enabled`, `clientKey`만 반환합니다. 시크릿 키는 반환하지 않습니다. |

잘못된 값·금액 불일치는 `400`, 없는 주문은 `404`, 다른 결제 시도나 중복 결제 키는 `409`, 설정 누락·저장 장애는 `503`입니다. 결제 거절의 `422`는 주문 응답을 포함하며, 일반 API 오류는 `{ "code": "...", "message": "..." }` 형식입니다.

```powershell
$order = Invoke-RestMethod -Method Post http://127.0.0.1:8080/orders
Invoke-RestMethod "http://127.0.0.1:8080/orders/$($order.orderId)"
```

승인 요청 예시입니다. `paymentKey`는 테스트 결제창에서 카드 인증 후 발급된 값을 사용하며, 아래 예시 값을 그대로 보내서는 승인되지 않습니다.

```json
{
  "orderId": "서버가-생성한-주문번호",
  "paymentKey": "카드-인증으로-발급된-paymentKey",
  "amount": 10000
}
```

응답의 `payment.status`는 다음과 같습니다. 주문 상태는 연결된 결제로부터 계산하며 별도의 중복 상태 컬럼을 두지 않습니다.

| 상태 | 의미와 다음 동작 |
| --- | --- |
| `READY` | 승인 요청 전입니다. 결제창에서 카드 인증을 진행합니다. |
| `PROCESSING` | 승인 또는 재조회 중입니다. 잠시 후 저장된 결과를 조회합니다. |
| `SUCCEEDED` | 주문번호·결제 키·금액·KRW·카드 수단·`DONE`·승인 시각을 PG 응답에서 확인했습니다. |
| `FAILED` | 명시적인 카드 거절 또는 PG의 `ABORTED`/`EXPIRED`를 확인했습니다. 새 주문으로 재시도합니다. |
| `UNKNOWN` | 통신 오류·모호한 응답·저장 실패 등으로 결과를 확정하지 못했습니다. PG 결과 재확인을 사용합니다. |

카드 인증 실패 리다이렉트는 브라우저에서 조작할 수 있으므로 그것만으로 DB 결제를 실패 처리하지 않습니다. 저장된 결과를 조회하고 인증이 끝나지 않았다는 안내만 표시합니다.

## 중복 처리와 복구

주문 행 잠금을 잡은 짧은 트랜잭션에서 결제 키, 금액, 결제 시도 UUID, 작업 UUID를 저장하고 **커밋한 뒤** PG를 호출합니다. 결제 시도 UUID는 토스 `Idempotency-Key`로 전달합니다. 주문당 결제 행 1개, 전역 결제 키 유일성, 주문·결제 금액 일치를 DB 제약조건으로 보장합니다.

동일 요청은 저장된 결과만 반환합니다. 다른 `paymentKey`로 기존 주문을 교체할 수 없고, 실패한 주문도 새로운 승인 시도를 받지 않습니다. 결과가 불명확하면 재승인 대신 PG 조회만 수행합니다. 네트워크 호출은 DB 트랜잭션 밖에서 수행하며 연결 3초, 응답 10초 제한을 둡니다.

PG 승인 후 DB 저장이 실패하거나 프로세스가 종료되어 `PROCESSING`이 남으면, 작업 시작 **30초 후** 조회 응답에서 `UNKNOWN` 및 `canReconcile: true`를 표시합니다. 복구 API는 새 작업 UUID로 조회를 소유하고, 이전 작업의 늦은 결과가 새 결과를 덮어쓰지 않게 합니다. 실제 DB 상태는 복구 전까지 `PROCESSING`일 수 있습니다.

```powershell
Invoke-RestMethod -Method Post "http://127.0.0.1:8080/payments/$($order.orderId)/reconcile"
```

PG 조회의 `404`, 인증 설정 오류, 처리 중 상태 등은 결제 실패의 증거로 사용하지 않고 `UNKNOWN`으로 유지합니다. 계속 확인되지 않으면 테스트 상점 키와 결제 내역을 확인합니다. 결제를 임의로 완료·실패로 수정하지 않습니다. 자동 재조회와 운영 화면은 이후 개선 범위입니다.

판매자 역할에서는 다음 SQL로 DB 결과와 토스 테스트 상점 결제 내역을 비교할 수 있습니다. `payment_key`는 서버 DB에서만 확인합니다.

```sql
SELECT o.id, o.amount, p.attempt_id, p.payment_key, p.status,
       p.pg_status, p.approved_at, p.checked_at, p.error_code
FROM purchase_orders o LEFT JOIN payments p ON p.order_id = o.id
ORDER BY o.created_at DESC;

SELECT order_id, attempt_id, status, error_code, processing_until
FROM payments
WHERE status = 'UNKNOWN'
   OR (status = 'PROCESSING' AND processing_until <= now());
```

## 테스트와 사용 확인

Java 21과 실행 중인 Docker가 필요합니다. 테스트는 Testcontainers의 별도 PostgreSQL 18 컨테이너를 사용하며 로컬 개발 DB와 분리됩니다. PG는 자동 테스트에서 모의 응답을 사용하므로 실제 키가 필요 없습니다.

```powershell
.\gradlew.bat clean test bootJar
```

테스트 리포트: `build/reports/tests/test/index.html`. 가격 변조, 요청 검증, PG 응답 검증·타임아웃·거절, 동시 승인, 결제 키 충돌, 응답 유실, PG 처리 후 DB 저장 장애, 만료 작업 복구, 늦은 응답 방어를 검증합니다.

직접 사용 확인은 다음 순서로 진행합니다.

1. 같은 테스트 상점의 키를 설정하고 서버를 실행합니다.
2. 화면에서 주문을 만들고 주문번호와 10,000원을 확인합니다.
3. 테스트 결제 버튼으로 토스 결제창에서 **카드**를 선택하고 인증을 완료합니다.
4. 결과 화면이 결제 완료인지 확인하고, 새로고침 및 주문번호 재조회 결과를 비교합니다.
5. DB와 토스 개발자센터의 테스트 결제 내역에서 주문번호·금액·상태를 대조합니다.
6. 접근을 제한한 테스트 환경에서도 같은 과정을 수행하고 관찰한 문제를 기록합니다.

자동 테스트와 로컬 화면 검증은 실제 토스 카드 인증 및 배포 환경 검증을 대신하지 않습니다. 현재 실제 상점 키를 사용한 결제 및 테스트 환경 배포는 별도 확인 항목으로 남아 있습니다.

연동 기준: [토스 결제창 가이드](https://docs.tosspayments.com/guides/v2/payment-window/integration), [API 명세](https://docs.tosspayments.com/reference), [인증·멱등키](https://docs.tosspayments.com/reference/using-api/authorization).
