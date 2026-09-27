# 설정

[README](../README.md)의 로컬 실행 절차에 사용되는 설정입니다. 서버 설정은 [application.yml](../src/main/resources/application.yml), 로컬 컨테이너 설정은 [compose.yaml](../compose.yaml)이 기준입니다.

## 서버 환경변수

환경변수는 백엔드를 실행하는 프로세스에 전달합니다. 이 프로젝트는 `.env` 파일을 Spring Boot 설정으로 자동 로드하지 않습니다.

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

프런트엔드는 `GET /payment-config`에서 공개 클라이언트 키와 UI 설정을 받습니다. 시크릿 키는 이 응답에 포함하지 않습니다. 프런트엔드용 토스 환경변수를 별도로 설정할 필요가 없습니다.

## 토스 키

현재 설정 검증은 다음 조합만 허용합니다.

| 설정 | 결과 |
| --- | --- |
| 두 키 모두 없음 | 서버 실행 가능, 결제 비활성화 |
| `test_gck_` + `test_gsk_` | 결제 활성화 |
| 한쪽 키만 있음 | 서버 시작 시 검증 실패 |
| 운영 키 또는 개별 연동 키 | 서버 시작 시 검증 실패 |

접두사가 맞아도 실제 키의 짝과 유효성은 확인되지 않습니다. 같은 상점에서 함께 발급된 주문서형·결제창형 테스트 키를 사용합니다. 발급 절차와 키 종류는 [토스 API 키 안내](https://docs.tosspayments.com/reference/using-api/api-keys)에 있습니다.

PowerShell 설정 예시:

```powershell
$env:TOSS_CLIENT_KEY = 'test_gck_REPLACE_WITH_YOUR_KEY'
$env:TOSS_SECRET_KEY = 'test_gsk_REPLACE_WITH_YOUR_KEY'
.\gradlew.bat bootRun
```

자리 표시자를 실제 키로 교체합니다. 환경변수는 해당 터미널에서 실행한 백엔드에 전달되므로 값을 바꾼 뒤 서버를 다시 실행합니다.

## 결제 UI

카드·국내 간편결제를 사용하는 UI로 설정합니다. 브라우저는 지원하지 않는 결제수단의 인증 요청을 중단하며, 서버도 승인 응답의 결제수단을 검증합니다.

variantKey를 생략하면 SDK 기본 UI를 사용합니다. 어드민에서 별도 UI를 구성한 경우 다음 값을 추가합니다.

```powershell
$env:TOSS_PAYMENT_METHOD_VARIANT_KEY = 'YOUR_PAYMENT_METHOD_VARIANT'
$env:TOSS_AGREEMENT_VARIANT_KEY = 'YOUR_AGREEMENT_VARIANT'
```

두 설정은 `renderPaymentWindow()`의 `variantKey.paymentMethod`, `variantKey.agreement`로 전달합니다. 옵션 명세는 [토스 결제창형 SDK](https://docs.tosspayments.com/sdk/v2/js/payment-window)에 있습니다.

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

`PAYMENT_DB_*`를 변경해도 Compose의 DB 이름·계정이나 pgAdmin 연결 정보가 함께 변경되지는 않습니다. 별도 DB를 사용하는 방법은 [개발 가이드](development.md)를 참고하세요.

## 개발 서버 주소

프런트엔드는 `127.0.0.1:5173`에서 실행되고, [vite.config.ts](../frontend/vite.config.ts)의 프록시가 API를 `127.0.0.1:8080`으로 전달합니다.

백엔드 포트를 변경하면 Vite 프록시도 같은 주소로 맞춥니다. 현재 기본 바인딩은 로컬 접속을 위한 설정입니다.
