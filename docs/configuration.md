# 설정

서버 설정의 기준은 [application.yml](../src/main/resources/application.yml), 로컬 컨테이너 설정의 기준은 [compose.yaml](../compose.yaml)입니다.

## 서버 환경변수

백엔드를 실행하는 터미널이나 IDE 실행 구성에 지정합니다. `.env` 파일은 자동으로 읽지 않습니다.

| 변수 | 기본값 | 용도 |
| --- | --- | --- |
| `PAYMENT_DB_URL` | `jdbc:postgresql://localhost:5432/payment_service` | JDBC 연결 주소 |
| `PAYMENT_DB_USERNAME` | `payment` | DB 사용자 |
| `PAYMENT_DB_PASSWORD` | `payment_local` | DB 암호 |
| `PAYMENT_DB_POOL_MIN_IDLE` | `10` | DB 커넥션 풀이 남겨 두는 쉬는 연결 수 |
| `PAYMENT_DB_POOL_IDLE_TIMEOUT_MS` | `600000` | 최소 수를 넘는 쉬는 연결을 닫기까지의 시간(밀리초) |
| `PAYMENT_BIND_ADDRESS` | `127.0.0.1` | 백엔드 수신 주소 |
| `PORT` | `8080` | 백엔드 포트 |
| `PAYMENT_FORWARD_HEADERS_STRATEGY` | `none` | 앞단 프록시가 넘긴 `X-Forwarded-*`를 원래 주소로 쓸지. 프록시 뒤에서만 받을 때 `framework` |
| `TOSS_CLIENT_KEY` | 빈 문자열 | 브라우저 SDK의 공개 클라이언트 키 |
| `TOSS_SECRET_KEY` | 빈 문자열 | 서버 승인·조회 API의 시크릿 키 |
| `TOSS_PAYMENT_METHOD_VARIANT_KEY` | 빈 문자열 | 결제수단 UI의 variantKey |
| `TOSS_AGREEMENT_VARIANT_KEY` | 빈 문자열 | 약관 UI의 variantKey |
| `PAYMENT_RECOVERY_ENABLED` | `true` | 미확정 승인 복구 작업 실행 여부 |
| `PAYMENT_RECOVERY_INTERVAL` | `PT1M` | 미확정 승인 복구 작업 주기. ISO-8601 기간 |
| `ORDER_UNPAID_EXPIRY_ENABLED` | `true` | 미결제 주문 자동 취소 작업 실행 여부 |
| `ORDER_UNPAID_EXPIRY_AFTER` | `PT24H` | 결제 대기 주문의 결제 기한. ISO-8601 기간(`PT24H`, `PT2M` 등) |
| `ORDER_UNPAID_EXPIRY_INTERVAL` | `PT10M` | 미결제 주문 자동 취소 작업 주기. ISO-8601 기간 |
| `KEYCLOAK_ISSUER_URI` | `http://127.0.0.1:8081/realms/modo-club` | access token 발급자. 공개키를 이 주소에서 찾는다 |

운영 이미지([Dockerfile](../Dockerfile))는 방문이 없을 때 서버가 잠들 수 있도록 일부 값을 바꿔 둡니다(작업 주기 30분, 쉬는 연결 0개 등). 운영 값과 Railway 변수는 [Railway 배포](deployment.md)에 있습니다.

프런트엔드는 `GET /payment-config`로 공개 키와 UI 설정을 받습니다. Keycloak 주소를 바꿀 때만 `frontend/.env.local`에 `VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_REALM`, `VITE_KEYCLOAK_CLIENT_ID`를 지정합니다.

## 토스 키

| 설정 | 결과 |
| --- | --- |
| 두 키 모두 없음 | 서버 실행 가능. 상품·주문 API는 동작하고 결제는 비활성화 |
| `test_gck_` + `test_gsk_` | 결제 활성화 |
| 한쪽 키만 있음, 운영 키·개별 연동 키 | 서버 시작 시 검증 실패 |

- 검증하는 것은 키 형식뿐입니다. 같은 상점에서 발급한 주문서형·결제창형 테스트 키 한 쌍을 사용합니다([토스 API 키 안내](https://docs.tosspayments.com/reference/using-api/api-keys)).
- 키가 없으면 화면의 `결제하기` 버튼이 비활성화됩니다. 화면에서는 이 버튼이 주문도 만들므로, 키 없이 주문을 확인하려면 [주문 API](api.md#주문-생성)를 호출합니다.

```powershell
$env:TOSS_CLIENT_KEY = 'test_gck_REPLACE_WITH_YOUR_KEY'
$env:TOSS_SECRET_KEY = 'test_gsk_REPLACE_WITH_YOUR_KEY'
.\gradlew.bat bootRun
```

## 결제 UI

- 결제 어드민의 UI는 카드·국내 간편결제로 설정합니다. 브라우저가 미지원 결제수단의 인증을 막고, 서버도 승인 응답의 결제수단을 검증합니다.
- variantKey를 비워 두면 SDK 기본 UI를 씁니다. 값은 `renderPaymentWindow()`의 `variantKey`로 전달됩니다([결제창 SDK](https://docs.tosspayments.com/sdk/v2/js/payment-window)).

## 결제 복구 작업

- 결과를 모르는 승인(`APPROVING`·`UNKNOWN`)을 1분마다(`PAYMENT_RECOVERY_INTERVAL`) 토스에 조회합니다. 운영 배포는 30분마다이며, 서버가 잠든 동안은 멈췄다가 깨어나고 1분 뒤 실행합니다. 한 번에 최대 50건이며, 1시간이 지나도 모르면 `REVIEW_REQUIRED`로 남깁니다.
- `PAYMENT_RECOVERY_ENABLED=false`는 주기 작업만 끕니다. 승인 요청 중 즉시 조회와, 같은 결제 키로 다시 요청할 때의 조회는 계속 동작합니다.
- 끄면 미확정 주문이 오래 막힐 수 있으니 로컬 개발에서도 기본값을 유지합니다. 동작 규칙은 [아키텍처](architecture.md#실패와-복구)를 참고합니다.

## 미결제 주문 자동 취소

- 결제 기한(`ORDER_UNPAID_EXPIRY_AFTER`, 기본 24시간)이 지난 결제 대기 주문을 10분마다(`ORDER_UNPAID_EXPIRY_INTERVAL`, 운영 배포는 30분) `CANCELED`로 바꿉니다. 승인 중·결과 모름·결제 완료 주문과, 최근 30분 안에 결제창을 연 주문은 건드리지 않습니다.
- 처음 켜면 이미 기한이 지난 로컬 결제 대기 주문(배송지 없이 만든 이전 주문 포함)이 서버 시작 1분 뒤 한꺼번에 취소됩니다.
- 로컬에서 바로 확인하려면 `ORDER_UNPAID_EXPIRY_AFTER=PT2M`으로 실행하거나, pgAdmin에서 주문의 `created_at`을 하루 전으로 바꿉니다.
- `ORDER_UNPAID_EXPIRY_ENABLED=false`로 끄면 결제 대기 주문이 계속 남습니다. 동작 규칙은 [아키텍처](architecture.md#미결제-주문-자동-취소)를 참고합니다.

## 로컬 PostgreSQL과 pgAdmin

`docker compose up -d`로 실행합니다.

| 항목 | 기본값 |
| --- | --- |
| PostgreSQL | `127.0.0.1:5432`, DB `payment_service`·`keycloak`, `payment` / `payment_local` |
| pgAdmin | `http://127.0.0.1:5050`, `admin@payment-service.com` / `payment_admin_local` |

- 한 PostgreSQL 컨테이너 안에 결제 DB(`payment_service`)와 Keycloak DB(`keycloak`)를 따로 둡니다. Keycloak DB의 테이블은 Keycloak이 직접 만들고 관리합니다.
- pgAdmin에는 DB 연결이 미리 등록되어 있어 두 DB 모두 `Payment Service` 아래에 보입니다([servers.json](../docker/pgadmin/servers.json)).
- pgAdmin은 [pgpass](../docker/pgadmin/pgpass)를 처음 실행할 때만 복사합니다. Keycloak을 추가하기 전부터 쓰던 pgAdmin에서 `keycloak` DB를 열 때 비밀번호를 물으면 DB 암호를 입력하거나 `docker compose exec pgadmin sh -c "cp /pgadmin4/pgpass /var/lib/pgadmin/storage/admin_payment-service.com/.pgpass"`를 한 번 실행합니다.
- `PAYMENT_DB_*`를 바꿔도 Compose 설정은 바뀌지 않으므로 양쪽을 함께 맞춥니다.
- 데이터는 명명된 볼륨에 저장됩니다. `docker compose stop`과 `docker compose down` 모두 데이터를 유지합니다.

## Keycloak

`docker compose up -d`로 PostgreSQL과 함께 실행됩니다. 개념과 사용법은 [로그인과 회원](authentication.md)에 있습니다.

| 항목 | 기본값 |
| --- | --- |
| 주소 | `http://127.0.0.1:8081`, 관리 콘솔 `/admin` |
| Keycloak 관리자(슈퍼 유저) | `admin` / `keycloak_admin_local` (`KEYCLOAK_ADMIN_USERNAME`·`KEYCLOAK_ADMIN_PASSWORD`로 변경, 첫 실행 때만 적용). 쇼핑몰 관리자와 다른 계정이며 [로컬 계정](authentication.md#로컬-계정) 참고 |
| realm·client | `modo-club` / `modo-club-web` ([realm 설정](../docker/keycloak/modo-club-realm.json)) |
| 로그인 테마 | `modo-club` ([테마 폴더](../docker/keycloak-themes/modo-club/login/), [디자인 설명](authentication.md#로그인-화면-디자인)) |
| 계정 테마 | `modo-club` ([테마 폴더](../docker/keycloak-themes/modo-club/account/), [디자인 설명](authentication.md#계정-설정-화면-디자인)) |
| 실행 모드 | `start-dev` (HTTP, 로컬 개발용) |
| 메모리 한도 | 1.5GB. 힙은 한도의 70%까지, 힙 밖 메모리는 약 300MB |

`keycloak` DB는 [01-keycloak.sql](../docker/postgres/init/01-keycloak.sql)이 만듭니다. 이 스크립트는 PostgreSQL 볼륨이 비어 있을 때만 자동 실행되므로, **Keycloak을 추가하기 전부터 쓰던 볼륨**에서는 한 번 직접 실행합니다.

```powershell
docker compose up -d postgres
docker compose exec postgres psql -U payment -d postgres -f /docker-entrypoint-initdb.d/01-keycloak.sql
docker compose up -d
```

- 스크립트는 DB가 이미 있으면 건너뛰므로 다시 실행해도 됩니다.
- 첫 시작은 1분쯤 걸립니다. `docker compose logs keycloak`에 `Realm 'modo-club' imported`와 `started`가 보이면 준비된 것입니다.
- 쓰지 않을 때는 `docker compose stop keycloak`으로 메모리를 돌려받을 수 있습니다. 이때 로그인과 주문·결제 API는 동작하지 않습니다.

## 시각 표시

DB 시각은 `timestamp with time zone`으로 저장하고, 서버는 UTC로 다룹니다. pgAdmin에서 한국 시간으로 보려면 다음을 실행합니다.

```sql
SET TIME ZONE 'Asia/Seoul';
```

화면의 시각은 브라우저의 시간대로 표시되며, DB 설정과는 무관합니다.

## 개발 서버 주소

- 프런트엔드는 `127.0.0.1:5173`에서 실행되고, [vite.config.ts](../frontend/vite.config.ts)의 프록시가 API 요청과 상담 WebSocket(`/ws`)을 `127.0.0.1:8080`으로 전달합니다. 포트가 사용 중이면 Vite가 다른 포트를 쓰므로 터미널에 표시된 주소를 확인합니다.
- 상담 WebSocket은 같은 출처 연결만 받습니다. 프록시가 브라우저의 `Host`를 그대로 넘기므로 개발 서버에서는 따로 설정할 것이 없습니다. 화면을 다른 주소에서 띄우면 [ChatWebSocketConfiguration](../src/main/java/com/example/payment/chat/infrastructure/websocket/ChatWebSocketConfiguration.java)에 허용할 출처를 추가합니다.
- 운영 배포처럼 앞단이 HTTPS를 처리하고 백엔드는 HTTP로 받으면, 브라우저 출처(`https://…`)와 백엔드가 보는 주소(`http://…`)가 달라 연결이 거부됩니다. nginx가 `Host`·`X-Forwarded-Host`·`X-Forwarded-Proto`를 넘기고 백엔드는 `PAYMENT_FORWARD_HEADERS_STRATEGY=framework`로 그 값을 씁니다.
- 백엔드 포트를 바꾸면 Vite 프록시도 함께 바꿉니다.
