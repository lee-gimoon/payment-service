# Railway 배포

이 서비스를 [Railway](https://railway.com)에 올리는 구성과 순서입니다. 결제는 토스 **테스트 키**로만 동작하므로(서버가 운영 키를 거부) 실제 청구가 없는 시연용 배포입니다.

방문이 없으면 백엔드와 Keycloak을 재워(Railway Serverless) 비용을 Hobby 플랜 포함 사용량(월 5달러) 안으로 맞춥니다. 대신 잠든 뒤 첫 접속은 서버가 깨어날 때까지 기다립니다.

## 구성

```text
브라우저
 ├─ https://<frontend 도메인>  → [frontend] nginx: React 화면, API·/ws는 내부망으로 전달 ─▶ [backend] Spring Boot ─▶ [Postgres]
 └─ https://<keycloak 도메인>  → [keycloak] 로그인·회원가입·계정 설정·관리 콘솔 ─────────────────────────────▶ [Postgres의 keycloak DB]
```

| 서비스 | 빌드 | 공개 주소 | 잠듦 |
| --- | --- | --- | --- |
| `Postgres` | Railway PostgreSQL 템플릿. 결제 DB는 템플릿 기본 DB, Keycloak은 같은 서버의 `keycloak` DB | 없음 | 항상 켜 둠 |
| `backend` | [Dockerfile](../Dockerfile) (Gradle `bootJar` → Java 21 JRE) | 없음. frontend만 내부망으로 호출 | 잠듦 |
| `frontend` | [frontend/Dockerfile](../frontend/Dockerfile) (`npm run build` → nginx) | 스토어 주소 | 항상 켜 둠 |
| `keycloak` | [docker/keycloak.Dockerfile](../docker/keycloak.Dockerfile) (테마·운영용 realm 포함, 운영 모드) | 로그인 주소 | 잠듦 |

- 화면은 API를 같은 주소의 상대 경로(`/orders`, `/ws` 등)로 부릅니다. 개발 서버의 [vite.config.ts](../frontend/vite.config.ts) 프록시 규칙을 운영에서는 [nginx 설정](../frontend/nginx/default.conf.template)이 그대로 맡습니다. API 경로라도 브라우저가 페이지(`Accept: text/html`)를 요청하면 React 화면을 줍니다.
- Postgres를 같이 재우면 백엔드·Keycloak이 깨어날 때 DB가 아직 잠들어 있어 시작에 실패할 수 있습니다. frontend는 메모리를 거의 쓰지 않고, 깨어 있어야 첫 화면이 바로 뜹니다.
- 서비스별 빌드·배포 설정은 [railway/](../railway/)의 설정 파일(config as code)에 있습니다. Dockerfile 위치, 다시 배포할 파일 경로(watch paths), 상태 확인 주소, 잠듦 여부를 정합니다.
- pgAdmin은 올리지 않습니다. 운영 DB는 Railway 대시보드의 Postgres 화면에서 봅니다.

## 방문이 없으면 잠드는 방식

Railway는 서비스가 **바깥으로 보내는 통신이 5~10분 없으면** 재우고, 인터넷이나 같은 프로젝트의 다른 서비스에서 요청이 오면 깨웁니다. 내부망으로 DB와 주고받는 통신도 통신으로 칩니다. 잠든 서비스에 온 첫 요청은 502로 실패할 수 있습니다([Railway 문서](https://docs.railway.com/reference/app-sleeping)).

### 잠들 수 있게 바꾼 것

로컬 설정 그대로는 서버가 주기적으로 DB와 통신해 잠들지 못합니다. 운영 이미지에만 다음 값을 넣었고, 로컬 기본값은 그대로입니다.

| 잠들지 못하게 하던 것 | 운영 설정 | 위치 |
| --- | --- | --- |
| 결제 복구 작업이 1분마다 DB 조회 | 30분마다(`PAYMENT_RECOVERY_INTERVAL=PT30M`) | [Dockerfile](../Dockerfile) |
| 미결제 주문 취소 작업이 10분마다 DB 조회 | 30분마다(`ORDER_UNPAID_EXPIRY_INTERVAL=PT30M`) | [Dockerfile](../Dockerfile) |
| DB 커넥션 풀이 쉬는 연결 10개를 유지 | 최소 0개, 쉬는 연결은 1분 뒤 닫음(`PAYMENT_DB_POOL_*`) | [Dockerfile](../Dockerfile) |
| Keycloak 클러스터 기능(JDBC_PING)의 주기적 DB 갱신 | 서버 1대 모드(`KC_CACHE=local`) | [keycloak.Dockerfile](../docker/keycloak.Dockerfile) |
| Keycloak이 만료된 로그인 세션을 3분마다, 만료된 이벤트·토큰을 15분마다 DB에서 지움 | 둘 다 30분마다 | [keycloak.Dockerfile](../docker/keycloak.Dockerfile) |
| Keycloak 커넥션 풀이 쉬는 연결을 2분마다 검사하고 5분 뒤에야 정리 | 검사를 끄고, 최소 연결 0개, 2분마다 정리 | [keycloak.Dockerfile](../docker/keycloak.Dockerfile) |
| 숨긴 탭의 상담 WebSocket heartbeat | 탭을 2분 넘게 숨기면 연결을 닫고, 다시 보면 연결 | [chatSocket.ts](../frontend/src/chat/chatSocket.ts) |

30분 주기는 잠드는 기준(5~10분)보다 길어서, 사람이 쓰는 동안과 깨어난 직후에만 작업이 돕니다. 로컬에서 운영 이미지를 띄워 Postgres 쿼리 로그로 확인하면, 마지막 요청 뒤 백엔드의 DB 연결은 약 1분, Keycloak은 약 4분 안에 모두 닫히고 그 뒤로는 통신이 없습니다.

### 화면이 서버를 깨우는 방식

[serverWakeup.ts](../frontend/src/lib/serverWakeup.ts)가 맡습니다. 운영 이미지만 켜고(`VITE_SERVERS_SLEEP=true`), 로컬 개발 서버에서는 아무것도 기다리지 않습니다.

- 4분 넘게 응답이 없던 서버에는 실제 요청 전에 가벼운 GET 확인을 2초 간격으로 보냅니다. 백엔드는 `/payment-config`, Keycloak은 realm 공개 정보(`/realms/modo-club`)입니다.
- 깨어나면 실제 요청을 **한 번만** 보냅니다. 주문 생성·결제 승인 같은 요청을 실패 후 다시 보내지 않으므로 중복 요청이 생기지 않습니다.
- 0.8초 넘게 걸리면 공지 띠 아래에 `서버를 깨우고 있습니다…` 안내를 띄웁니다. 로그인 버튼은 Keycloak이 깨어난 뒤 나타나고, 로그인·계정 설정·로그아웃도 Keycloak을 깨운 뒤 이동합니다.
- 오래 쉬는 사이 Keycloak이 잠들어 토큰 갱신이 실패하면, 깨운 뒤 한 번 더 갱신합니다. Keycloak 26은 로그인 세션을 DB에 저장하므로 잠들었다 깨어도 로그인이 유지됩니다.
- 2분 안에 깨어나지 않으면 기다리기를 멈추고, 요청이 평소처럼 `서버에 연결하지 못했습니다`를 보여줍니다.

### 감수할 점

- **첫 접속 대기:** 잠든 뒤 첫 방문은 Spring과 Keycloak이 시작될 때까지 기다립니다. 로컬에서 운영 이미지를 다시 시작해 보면 백엔드 약 20초, Keycloak 약 16초가 걸렸고 둘은 동시에 깨어납니다. Railway의 CPU 사양에 따라 달라집니다. 면접 전에 링크를 한 번 열어 깨워 두면 좋습니다.
- **주기 작업 지연:** 잠든 동안에는 미결제 주문 취소와 결제 복구가 돌지 않습니다. 깨어나고 1분 뒤 밀린 것을 처리하므로 결과는 같고 시점만 늦어집니다. 승인 요청 중의 즉시 조회와 같은 결제 키로 다시 요청할 때의 조회는 그대로 동작합니다([결제 복구 작업](configuration.md#결제-복구-작업)).
- **실제로 잠드는지는 배포 후 확인합니다.** [잠듦 확인](#7-잠듦-확인)을 참고합니다.

## 파일

| 파일 | 역할 |
| --- | --- |
| [Dockerfile](../Dockerfile) | 백엔드 이미지. 테스트는 Docker가 필요해 빌드에서 돌리지 않으므로 푸시 전에 로컬에서 실행 |
| [frontend/Dockerfile](../frontend/Dockerfile) | 스토어 이미지. Keycloak 공개 주소를 빌드 인자 `VITE_KEYCLOAK_URL`로 받음 |
| [frontend/nginx/default.conf.template](../frontend/nginx/default.conf.template) | 화면·API·WebSocket 나누기, 내부망 백엔드 전달, 캐시·결제창 팝업 헤더 |
| [docker/keycloak.Dockerfile](../docker/keycloak.Dockerfile) | Keycloak 이미지. 스토어 주소를 빌드 인자 `STORE_URL`로 받아 realm과 계정 테마에 넣음 |
| [railway/*.json](../railway/) | 서비스별 Railway 빌드·배포 설정 |
| [.dockerignore](../.dockerignore) | 세 이미지가 공유하는 빌드 컨텍스트(저장소 루트)에서 뺄 파일 |

운영용 realm은 [modo-club-realm.json](../docker/keycloak/modo-club-realm.json)에서 이미지 빌드 때 만듭니다. **테스트 회원은 뺍니다.** 비밀번호가 저장소에 공개돼 있어 그대로 올리면 누구나 쇼핑몰 관리자로 로그인할 수 있기 때문입니다. 로컬 스토어 주소 `http://127.0.0.1:5173`은 `STORE_URL`로 바꿉니다.

## Railway 설정 순서

### 0. 준비

- 배포할 코드를 GitHub `main`에 푸시합니다. Railway는 GitHub 저장소에서 빌드합니다.
- 토스 개발자센터에서 같은 상점의 테스트 키 한 쌍(`test_gck_`, `test_gsk_`)을 준비합니다.
- 비용이 예상을 넘지 않도록 **Workspace Settings → Usage**에서 사용량 상한(hard limit)을 먼저 걸어 둡니다. 상한에 닿으면 서비스가 멈춥니다.

### 1. 프로젝트와 Postgres

1. **New Project**로 빈 프로젝트를 만들고 **+ Create → Database → PostgreSQL**을 추가합니다. 서비스 이름은 `Postgres`로 둡니다(변수 참조에 쓰임).
2. Keycloak용 DB를 한 번 만듭니다. Postgres 서비스의 **Variables**에서 `DATABASE_PUBLIC_URL` 값을 복사해 로컬에서 실행합니다.

   ```sh
   docker run --rm postgres:18-alpine psql "DATABASE_PUBLIC_URL_값" -c "CREATE DATABASE keycloak;"
   ```

   결제 DB는 템플릿 기본 DB(`PGDATABASE`, 보통 `railway`)를 쓰며, 백엔드가 처음 시작할 때 Flyway가 스키마와 초기 상품을 만듭니다.

### 2. 서비스 3개

**+ Create → GitHub Repo**로 이 저장소를 세 번 추가하고 이름을 정확히 `backend`, `frontend`, `keycloak`으로 바꿉니다. 이름이 내부 주소(`backend.railway.internal`)와 변수 참조에 쓰입니다.

각 서비스의 **Settings**에서:

| 항목 | backend | frontend | keycloak |
| --- | --- | --- | --- |
| Source → Branch | `main` | `main` | `main` |
| Config-as-code → Railway Config File | `/railway/backend.json` | `/railway/frontend.json` | `/railway/keycloak.json` |
| Networking → Public Domain | **만들지 않음** | Generate Domain (포트 `8080`) | Generate Domain (포트 `8080`) |

Root Directory는 비워 둡니다. 세 이미지 모두 저장소 루트를 빌드 컨텍스트로 씁니다. 잠듦(Serverless)은 설정 파일의 `sleepApplication`으로 켜지므로 따로 켜지 않아도 됩니다.

### 3. 변수

각 서비스의 **Variables → Raw Editor**에 붙여 넣습니다. `${{...}}`는 Railway가 다른 서비스의 값으로 채우는 참조라 그대로 둡니다.

**backend**

```properties
PORT=8080
PAYMENT_DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
PAYMENT_DB_USERNAME=${{Postgres.PGUSER}}
PAYMENT_DB_PASSWORD=${{Postgres.PGPASSWORD}}
KEYCLOAK_ISSUER_URI=https://${{keycloak.RAILWAY_PUBLIC_DOMAIN}}/realms/modo-club
TOSS_CLIENT_KEY=test_gck_REPLACE_WITH_YOUR_KEY
TOSS_SECRET_KEY=test_gsk_REPLACE_WITH_YOUR_KEY
```

**keycloak**

```properties
PORT=8080
STORE_URL=https://${{frontend.RAILWAY_PUBLIC_DOMAIN}}
KC_HOSTNAME=https://${{RAILWAY_PUBLIC_DOMAIN}}
KC_DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/keycloak
KC_DB_USERNAME=${{Postgres.PGUSER}}
KC_DB_PASSWORD=${{Postgres.PGPASSWORD}}
KC_BOOTSTRAP_ADMIN_USERNAME=REPLACE_WITH_ADMIN_NAME
KC_BOOTSTRAP_ADMIN_PASSWORD=REPLACE_WITH_LONG_RANDOM_PASSWORD
```

**frontend**

```properties
PORT=8080
VITE_KEYCLOAK_URL=https://${{keycloak.RAILWAY_PUBLIC_DOMAIN}}
BACKEND_URL=http://${{backend.RAILWAY_PRIVATE_DOMAIN}}:${{backend.PORT}}
```

- `STORE_URL`과 `VITE_KEYCLOAK_URL`은 **빌드할 때** 이미지에 들어갑니다(Dockerfile의 `ARG`). 도메인을 바꾸면 해당 서비스를 다시 배포합니다.
- Keycloak 관리 콘솔은 공개 주소에 있으므로 관리자 비밀번호는 길고 무작위로 정합니다. 첫 실행 때만 적용됩니다.
- 이미지에 들어 있는 운영 기본값(메모리, 작업 주기, 커넥션 풀 등)은 [Dockerfile](../Dockerfile)과 [keycloak.Dockerfile](../docker/keycloak.Dockerfile)의 `ENV`에 있습니다. 같은 이름의 변수를 Railway에 넣으면 덮어씁니다.

### 4. 배포

변수를 저장하면 Railway가 바뀐 내용을 모아 보여 줍니다. **Deploy**로 적용합니다. 세 서비스를 한꺼번에 배포해도 됩니다.

| 서비스 | 정상 로그 |
| --- | --- |
| keycloak | `Realm 'modo-club' imported`, `Keycloak ... started` (첫 시작은 DB 테이블을 만드느라 1~2분) |
| backend | Flyway 마이그레이션 적용, `Started PaymentServiceApplication` |
| frontend | nginx 시작 로그 |

frontend 도메인을 열어 상품 목록이 보이면 화면·백엔드·DB 연결이 된 것입니다.

### 5. 쇼핑몰 관리자 만들기

운영 realm에는 테스트 회원이 없으므로 관리자를 직접 만듭니다.

1. `https://<keycloak 도메인>/admin`에 `KC_BOOTSTRAP_ADMIN_*` 계정으로 로그인합니다. 임시 관리자라는 경고가 보이면 `master` realm에 영구 관리자를 만들고 임시 계정을 지웁니다.
2. [쓰던 환경에 쇼핑몰 관리자 추가하기](authentication.md#쓰던-환경에-쇼핑몰-관리자-추가하기)의 3~6단계를 따릅니다. 역할 `shop-admin`은 realm에 이미 있으므로 2단계는 건너뜁니다. 이메일은 실제로 받을 수 있는 주소가 아니어도 되고, 비밀번호는 새로 정합니다.

### 6. 결제 확인

일반 회원으로 회원가입한 뒤 상품을 담고 토스 테스트 결제를 진행합니다. 확인 순서는 [실제 결제 확인](development.md#실제-결제-확인)과 같습니다. 테스트 키 결제는 청구되지 않습니다.

### 7. 잠듦 확인

아무도 접속하지 않은 채 15분쯤 지난 뒤 프로젝트 캔버스에서 backend·keycloak이 잠든(Sleeping) 상태인지 봅니다. 잠들지 않으면:

- 스토어 탭을 화면에 띄워 둔 채로 두지 않았는지 확인합니다. 상담 연결은 탭을 숨긴 지 2분 뒤에 닫힙니다.
- 서비스의 **Variables**에서 이미지 기본값(`PAYMENT_RECOVERY_INTERVAL`, `PAYMENT_DB_POOL_MIN_IDLE` 등)을 짧게 덮어쓰지 않았는지 확인합니다.
- 로그에서 주기적으로 반복되는 작업이 있는지 봅니다.

잠든 뒤 스토어를 열면 안내 띠가 보였다가 상품이 뜨는지, 로그인 버튼이 나타나는지 확인합니다.

## 비용

Hobby 플랜은 월 5달러이고 사용량 5달러가 포함됩니다. 사용량은 메모리 GB당 월 약 10달러, CPU vCPU당 월 약 20달러로 계산합니다([요금표](https://railway.com/pricing)).

로컬에서 운영 이미지를 띄워 잰 메모리는 백엔드 약 270MB, Keycloak 약 440MB, Postgres 약 80MB, nginx 약 10MB입니다(쉬는 상태). 이미지에서 Spring 힙을 320MB, Keycloak 힙을 512MB로 제한했습니다.

| 방식 | 월 사용량(추정) |
| --- | --- |
| 네 서비스를 24시간 켜 둠 | 약 9~12달러 |
| backend·keycloak만 잠듦, 하루 1~3시간 사용 | 약 2~4달러. 포함 사용량 안이라 청구는 플랜 요금 5달러 |

실제 사용량은 **Workspace → Usage**에서 확인합니다.

## 바꿀 때

- `main`에 푸시하면 설정 파일의 watch paths에 맞는 서비스만 다시 배포됩니다. 예를 들어 `frontend/`만 바꾸면 frontend만 다시 빌드합니다.
- 이미지 빌드는 테스트를 돌리지 않습니다. 푸시 전에 로컬에서 [백엔드](development.md#백엔드-검증)와 [프런트엔드](development.md#프런트엔드-검증)를 검증합니다.
- realm 파일은 Keycloak DB에 realm이 **없을 때만** 읽습니다. 처음 배포한 뒤 realm 파일을 고치거나 스토어 도메인을 바꾸면 관리 콘솔에서 직접 고칩니다([realm 설정 바꾸기](authentication.md#realm-설정-바꾸기)). 스토어 도메인은 **Clients → modo-club-web**의 Root·Home URL, Redirect URI, Web origins, Post logout redirect URI와 **Realm settings**의 HTML display name에 있습니다. 계정 화면 로고 링크는 테마 파일이라 `STORE_URL`을 바꾸고 keycloak을 다시 배포합니다.

## 로컬에서 운영 이미지 확인

푸시 전에 이미지가 빌드되는지 확인할 때 저장소 루트에서 실행합니다.

```sh
docker build -t payment-backend .
docker build -f frontend/Dockerfile --build-arg VITE_KEYCLOAK_URL=http://127.0.0.1:8081 -t payment-frontend .
docker build -f docker/keycloak.Dockerfile --build-arg STORE_URL=http://127.0.0.1:8080 -t payment-keycloak .
```

## 문제 해결

| 증상 | 확인할 것 |
| --- | --- |
| 안내 띠가 계속 보이고 상품이 뜨지 않음 | backend 로그, frontend의 `BACKEND_URL`, backend의 `PORT=8080` |
| Keycloak이 `database "keycloak" does not exist`로 멈춤 | [1단계](#1-프로젝트와-postgres)의 `CREATE DATABASE keycloak;` |
| Keycloak 화면에 `Invalid parameter: redirect_uri` | keycloak의 `STORE_URL`. realm을 만든 뒤 바꿨다면 관리 콘솔의 client 주소 |
| 로그인은 되는데 API가 401 | backend의 `KEYCLOAK_ISSUER_URI`가 `KC_HOSTNAME` + `/realms/modo-club`과 정확히 같은지 |
| 상담 창이 `연결이 끊겨 다시 연결하고 있습니다`에서 멈춤 | backend 로그의 WebSocket 403. nginx가 `Host`·`X-Forwarded-Proto`를 넘기는지, `PAYMENT_FORWARD_HEADERS_STRATEGY=framework`인지 |
| 결제 버튼이 비활성화됨 | backend의 `TOSS_CLIENT_KEY`·`TOSS_SECRET_KEY`([토스 키](configuration.md#토스-키)) |
