# 설정

[README](../README.md)의 로컬 실행 절차에 사용되는 설정입니다. 서버 설정은 [application.yml](../src/main/resources/application.yml), 로컬 컨테이너 설정은 [compose.yaml](../compose.yaml)이 기준입니다.

## 서버 환경변수

환경변수는 백엔드를 실행하는 프로세스에 전달합니다. 이 프로젝트는 `.env` 파일을 Spring Boot 설정으로 자동 로드하지 않습니다. IDE에서 실행한다면 해당 실행 구성에 환경변수를 지정합니다.

| 변수 | 기본값 | 용도 |
| --- | --- | --- |
| `PAYMENT_DB_URL` | `jdbc:postgresql://localhost:5432/payment_service` | JDBC 연결 주소 |
| `PAYMENT_DB_USERNAME` | `payment` | DB 사용자 |
| `PAYMENT_DB_PASSWORD` | `payment_local` | 로컬 DB 암호 |
| `PAYMENT_BIND_ADDRESS` | `127.0.0.1` | 백엔드 수신 주소 |
| `PORT` | `8080` | 백엔드 포트 |
| `TOSS_CLIENT_KEY` | 빈 문자열 | 브라우저 SDK의 공개 클라이언트 키 |
| `TOSS_SECRET_KEY` | 빈 문자열 | 서버 승인·조회 API의 시크릿 키 |
| `TOSS_PAYMENT_METHOD_VARIANT_KEY` | 빈 문자열 | 결제수단 UI의 variantKey |
| `TOSS_AGREEMENT_VARIANT_KEY` | 빈 문자열 | 약관 UI의 variantKey |
| `PAYMENT_RECOVERY_ENABLED` | `true` | 미확정 승인 복구 작업 실행 여부 |

프런트엔드는 `GET /payment-config`에서 공개 클라이언트 키와 UI 설정을 받습니다. 시크릿 키는 이 응답에 포함하지 않습니다. 프런트엔드용 토스 환경변수를 별도로 설정할 필요가 없습니다.

## 토스 키

현재 설정 검증은 다음 조합만 허용합니다.

| 설정 | 결과 |
| --- | --- |
| 두 키 모두 없음 | 서버 실행 가능, 결제 비활성화. 상품·주문 API는 사용 가능 |
| `test_gck_` + `test_gsk_` | 결제 활성화 |
| 한쪽 키만 있음 | 서버 시작 시 검증 실패 |
| 운영 키 또는 개별 연동 키 | 서버 시작 시 검증 실패 |

서버 시작 시 검증하는 것은 키 형식이며, 실제 키의 짝과 유효성은 확인하지 않습니다. 같은 상점에서 함께 발급된 주문서형·결제창형 테스트 키를 사용합니다. 발급 절차와 키 종류는 [토스 API 키 안내](https://docs.tosspayments.com/reference/using-api/api-keys)에 있습니다.

키가 없으면 스토어에서 상품 조회와 장바구니 편집은 가능하지만 `결제하기` 버튼이 비활성화됩니다. 화면에서는 이 버튼을 통해 주문도 생성하므로, 키 없이 주문 생성을 확인하려면 [주문 API](api.md)를 사용합니다.

PowerShell 설정 예시:

```powershell
$env:TOSS_CLIENT_KEY = 'test_gck_REPLACE_WITH_YOUR_KEY'
$env:TOSS_SECRET_KEY = 'test_gsk_REPLACE_WITH_YOUR_KEY'
.\gradlew.bat bootRun
```

자리 표시자를 실제 키로 교체합니다. 환경변수는 해당 터미널에서 실행한 백엔드에 전달되므로 값을 바꾼 뒤 서버를 다시 실행합니다.

## 결제 UI

카드·국내 간편결제를 사용하는 UI로 설정합니다. 브라우저는 `widgets({ customerKey: ANONYMOUS })`로 결제창을 생성하며, 지원하지 않는 결제수단을 선택하면 인증 요청을 중단합니다. 서버도 승인 응답의 결제수단을 검증합니다.

variantKey를 생략하면 SDK 기본 UI를 사용합니다. 어드민에서 별도 UI를 구성한 경우 다음 값을 추가합니다.

```powershell
$env:TOSS_PAYMENT_METHOD_VARIANT_KEY = 'YOUR_PAYMENT_METHOD_VARIANT'
$env:TOSS_AGREEMENT_VARIANT_KEY = 'YOUR_AGREEMENT_VARIANT'
```

두 설정은 `renderPaymentWindow()`의 `variantKey.paymentMethod`, `variantKey.agreement`로 전달합니다. 옵션 명세는 [토스 결제창형 SDK](https://docs.tosspayments.com/sdk/v2/js/payment-window)에 있습니다.

## 결제 복구 작업

백엔드는 결과를 모르는 승인(`APPROVING`·`UNKNOWN`)을 토스에 조회합니다. 첫 주기 작업은 기동 1분 뒤 실행되고, 이후에는 직전 작업이 끝난 뒤 1분을 기다립니다. 한 번에 최대 50건을 처리하며, 마지막 확인 시각(없으면 승인 요청 시각)에서 1분이 지난 시도만 조회합니다. 승인 요청 후 1시간이 지난 복구 조회에서도 결과가 불명확하면 `REVIEW_REQUIRED`로 남겨 수동 확인이 필요함을 표시합니다.

`PAYMENT_RECOVERY_ENABLED=false`는 주기 작업만 끕니다. 다음 조회는 계속 수행합니다.

- 새 승인 요청의 결과가 `UNKNOWN`이고 이 결과의 DB 저장에 성공하면, 같은 요청 안에서 즉시 한 번 조회합니다.
- 같은 결제 키의 승인 요청을 다시 받으면, 위의 1분 대기 기준을 만족하는 미확정 시도만 조회합니다. 승인을 다시 호출하지는 않습니다.

`GET /orders/{orderId}`와 화면의 주문 새로고침은 저장된 결과만 읽습니다. `REVIEW_REQUIRED`는 자동 복구 대상이 아니며 수동 확정용 관리자 API도 없습니다. 미확정 주문은 확인될 때까지 새 결제가 막히므로 일반적인 로컬 개발에서는 주기 작업의 기본값을 유지합니다.

주기와 기한은 [PaymentRecoveryScheduler](../src/main/java/com/example/payment/payment/application/PaymentRecoveryScheduler.java), [PaymentRecoveryService](../src/main/java/com/example/payment/payment/application/PaymentRecoveryService.java)의 코드 상수입니다. 결과별 처리는 [아키텍처](architecture.md#실패와-복구)에 있습니다.

## 로컬 PostgreSQL과 pgAdmin

`docker compose up -d`는 다음 개발 환경을 실행합니다.

| 항목 | 기본값 |
| --- | --- |
| PostgreSQL 주소 | `127.0.0.1:5432` |
| 데이터베이스 | `payment_service` |
| 사용자 / 암호 | `payment` / `payment_local` |
| pgAdmin 주소 | `http://127.0.0.1:5050` |
| pgAdmin 계정 / 암호 | `admin@payment-service.com` / `payment_admin_local` |

pgAdmin의 기본 로그인 값은 Compose 환경변수 `PGADMIN_DEFAULT_EMAIL`, `PGADMIN_DEFAULT_PASSWORD`로 설정합니다. 이 값은 Spring Boot 환경변수와 별개입니다.

기본 서버 연결은 [servers.json](../docker/pgadmin/servers.json)과 [pgpass](../docker/pgadmin/pgpass)에 등록되어 있습니다. pgAdmin 컨테이너는 Compose 서비스명 `postgres`로 DB에 연결하고, 호스트에서 실행하는 백엔드는 `localhost`로 연결합니다.

기본 설정을 사용할 때는 `PAYMENT_DB_*`를 지정할 필요가 없습니다. 이 값을 변경해도 Compose의 DB 이름·계정이나 pgAdmin 연결 정보가 함께 변경되지는 않으므로, 각 설정의 접속 정보를 맞춰야 합니다.

PostgreSQL과 pgAdmin 데이터는 각각 명명된 볼륨에 저장합니다. `docker compose stop`은 컨테이너를 중지하고, `docker compose down`은 컨테이너와 네트워크를 제거합니다. 두 명령 모두 기본적으로 볼륨의 데이터를 유지합니다.

## 시각 저장과 표시

JDBC 시간대는 `UTC`이며, 주문·결제 시각 컬럼은 PostgreSQL의 `timestamp with time zone`을 사용합니다. SQL에서 보이는 시간은 접속한 세션의 시간대에 따릅니다. pgAdmin Query Tool 등 현재 연결에서 한국 시간으로 확인하려면 다음을 실행합니다.

```sql
SET TIME ZONE 'Asia/Seoul';
SHOW TIME ZONE;
```

해당 DB의 새 연결에도 기본 적용하려면 다음을 실행한 뒤 다시 접속합니다.

```sql
ALTER DATABASE payment_service SET timezone TO 'Asia/Seoul';
```

이 설정은 저장된 시각 자체를 바꾸지 않습니다. 프런트엔드의 날짜 표시는 `toLocaleString('ko-KR')`로 한국어 형식을 사용하지만 시간대는 브라우저 환경에 따릅니다. DB 시간대를 바꿔도 화면의 시간대가 한국 시간으로 고정되지는 않습니다.

## 개발 서버 주소

프런트엔드는 기본적으로 `127.0.0.1:5173`에서 실행되고, [vite.config.ts](../frontend/vite.config.ts)의 프록시가 `/products`, `/orders`, `/payment-attempts`, `/payments`, `/payment-config` 요청을 `127.0.0.1:8080`으로 전달합니다. 포트가 사용 중이면 Vite가 다른 포트를 선택할 수 있으므로 터미널에 출력된 주소를 확인합니다.

백엔드 포트를 변경하면 Vite 프록시도 같은 주소로 맞춥니다. 결제 인증 후 복귀 주소는 브라우저에서 현재 접속한 주소의 `/payment/result`로 생성합니다. 현재 기본 바인딩은 로컬 접속을 위한 설정입니다.
