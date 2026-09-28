# 설정

서버 설정의 기준은 [application.yml](../src/main/resources/application.yml), 로컬 컨테이너 설정의 기준은 [compose.yaml](../compose.yaml)입니다.

## 서버 환경변수

백엔드를 실행하는 터미널이나 IDE 실행 구성에 지정합니다. `.env` 파일은 자동으로 읽지 않습니다.

| 변수 | 기본값 | 용도 |
| --- | --- | --- |
| `PAYMENT_DB_URL` | `jdbc:postgresql://localhost:5432/payment_service` | JDBC 연결 주소 |
| `PAYMENT_DB_USERNAME` | `payment` | DB 사용자 |
| `PAYMENT_DB_PASSWORD` | `payment_local` | DB 암호 |
| `PAYMENT_BIND_ADDRESS` | `127.0.0.1` | 백엔드 수신 주소 |
| `PORT` | `8080` | 백엔드 포트 |
| `TOSS_CLIENT_KEY` | 빈 문자열 | 브라우저 SDK의 공개 클라이언트 키 |
| `TOSS_SECRET_KEY` | 빈 문자열 | 서버 승인·조회 API의 시크릿 키 |
| `TOSS_PAYMENT_METHOD_VARIANT_KEY` | 빈 문자열 | 결제수단 UI의 variantKey |
| `TOSS_AGREEMENT_VARIANT_KEY` | 빈 문자열 | 약관 UI의 variantKey |
| `PAYMENT_RECOVERY_ENABLED` | `true` | 미확정 승인 복구 작업 실행 여부 |

프런트엔드는 `GET /payment-config`로 공개 키와 UI 설정을 받으므로 별도 환경변수가 필요 없습니다.

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

- 결과를 모르는 승인(`APPROVING`·`UNKNOWN`)을 1분마다 토스에 조회합니다. 한 번에 최대 50건이며, 1시간이 지나도 모르면 `REVIEW_REQUIRED`로 남깁니다.
- `PAYMENT_RECOVERY_ENABLED=false`는 주기 작업만 끕니다. 승인 요청 중 즉시 조회와, 같은 결제 키로 다시 요청할 때의 조회는 계속 동작합니다.
- 끄면 미확정 주문이 오래 막힐 수 있으니 로컬 개발에서도 기본값을 유지합니다. 동작 규칙은 [아키텍처](architecture.md#실패와-복구)를 참고합니다.

## 로컬 PostgreSQL과 pgAdmin

`docker compose up -d`로 실행합니다.

| 항목 | 기본값 |
| --- | --- |
| PostgreSQL | `127.0.0.1:5432`, DB `payment_service`, `payment` / `payment_local` |
| pgAdmin | `http://127.0.0.1:5050`, `admin@payment-service.com` / `payment_admin_local` |

- pgAdmin에는 DB 연결이 미리 등록되어 있습니다([servers.json](../docker/pgadmin/servers.json)).
- `PAYMENT_DB_*`를 바꿔도 Compose 설정은 바뀌지 않으므로 양쪽을 함께 맞춥니다.
- 데이터는 명명된 볼륨에 저장됩니다. `docker compose stop`과 `docker compose down` 모두 데이터를 유지합니다.

## 시각 표시

DB 시각은 `timestamp with time zone`으로 저장하고, 서버는 UTC로 다룹니다. pgAdmin에서 한국 시간으로 보려면 다음을 실행합니다.

```sql
SET TIME ZONE 'Asia/Seoul';
```

화면의 시각은 브라우저의 시간대로 표시되며, DB 설정과는 무관합니다.

## 개발 서버 주소

- 프런트엔드는 `127.0.0.1:5173`에서 실행되고, [vite.config.ts](../frontend/vite.config.ts)의 프록시가 API 요청을 `127.0.0.1:8080`으로 전달합니다. 포트가 사용 중이면 Vite가 다른 포트를 쓰므로 터미널에 표시된 주소를 확인합니다.
- 백엔드 포트를 바꾸면 Vite 프록시도 함께 바꿉니다.
