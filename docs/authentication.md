# 로그인과 회원

회원가입·로그인은 오픈소스 인증 서버 [Keycloak](https://www.keycloak.org/)이 맡습니다. 쇼핑몰은 비밀번호를 받지 않고, Keycloak이 발급한 access token으로 회원을 확인합니다. 실행 설정은 [설정 문서](configuration.md#keycloak), API 계약은 [API 문서](api.md)에 있습니다.

한 문장으로 줄이면 **OpenID Connect(OAuth 2.0 기반)로 Keycloak에서 로그인하고, 받은 JWT를 들고 Spring API를 호출합니다. Spring은 세션 없이 JWT만 검사합니다.** 이 용어들이 처음이면 [로그인 기술 기초](#로그인-기술-기초)부터 읽습니다.

## 로그인 기술 기초

### 로그인이 하는 두 가지 일

1. **처음 한 번 확인하기**: 이 사람이 정말 `buyer@modo.test`인지 비밀번호로 확인합니다.
2. **요청마다 알아보기**: 주문 조회·결제 요청마다 비밀번호를 물을 수는 없으므로, "아까 확인한 그 사람"임을 알아볼 수단이 필요합니다.

세션, JWT, OAuth 2.0, OpenID Connect는 이 두 가지를 어떻게 하느냐에 붙은 이름입니다.

### 세션: 서버가 기억하는 방식

옷 보관소 번호표와 같습니다. 로그인하면 서버가 "17번 = 김모도"를 장부에 적고, 브라우저는 번호표(쿠키)만 들고 다닙니다. 요청이 오면 서버가 장부에서 번호를 찾아 회원을 알아봅니다. 번호표에는 아무 정보가 없고 서버가 기억합니다.

이 프로젝트의 Spring 서버는 세션을 쓰지 않습니다([SecurityConfiguration](../src/main/java/com/example/payment/config/SecurityConfiguration.java)의 `STATELESS`).

### JWT: 도장 찍힌 출입증

회사 출입증과 같습니다. 출입증에 이름과 유효기간이 적혀 있고 발급처 도장이 찍혀 있어서, 경비는 장부를 보지 않고 도장만 확인합니다.

JWT(JSON Web Token)는 이런 출입증을 글자로 만든 것입니다. 머리말, 내용, 서명 세 부분이 점(`.`)으로 이어져 있습니다.

```text
eyJhbGciOi...  .  eyJzdWIiOi...  .  SflKxwRJSM...
    머리말             내용               서명
```

이 프로젝트의 access token 내용에는 이런 값이 들어 있습니다.

| 이름 | 뜻 | 이 프로젝트의 값 |
| --- | --- | --- |
| `iss` | 발급처 | `http://127.0.0.1:8081/realms/modo-club` |
| `aud` | 토큰을 받을 서비스 | `payment-service` 포함. 다른 서비스용 토큰은 거부 |
| `sub` | 회원 고유 ID | Keycloak이 만든 UUID. `purchase_orders.customer_id`에 저장 |
| `exp` | 만료 시각 | 발급 후 5분 |
| `email`, `given_name`, `family_name` | 회원 정보 | 헤더에 회원 이름 표시 |

- **서버가 기억하지 않습니다.** 필요한 정보가 토큰에 있으므로 Spring은 서명이 Keycloak 것인지, 기간이 남았는지만 검사합니다.
- **위조는 못 하지만 내용은 누구나 읽을 수 있습니다.** 서명은 고치지 못하게 막을 뿐 숨기지 않습니다. 비밀번호 같은 비밀은 넣지 않습니다.
- **짧게 씁니다.** 도둑맞으면 만료 전까지 쓸 수 있으므로 5분만 유효하고, 만료가 가까우면 새로 받습니다.

### OAuth 2.0: 로그인을 다른 곳에 맡기는 규칙

OAuth(Open Authorization) 2.0은 앱이 회원의 비밀번호를 직접 받지 않고, 비밀번호를 확인한 로그인 서버에서 토큰을 받아 쓰게 하는 표준 규칙입니다.

배달 앱에서 **카카오로 로그인**을 눌러 봤다면 이미 OAuth 2.0을 써 본 것입니다.

1. 배달 앱이 카카오 로그인 화면을 띄웁니다.
2. 카카오 아이디·비밀번호는 배달 앱이 아니라 **카카오 화면**에 입력합니다.
3. 카카오가 "배달 앱에 닉네임과 프로필 사진을 알려줘도 될까요?"라고 묻고, 동의하면 배달 앱으로 돌아갑니다.
4. 카카오는 배달 앱에 비밀번호 대신 **토큰**을 줍니다. 배달 앱은 이 토큰으로 내가 허락한 정보만 가져갈 수 있습니다.

배달 앱은 끝까지 카카오 비밀번호를 모릅니다. 이 프로젝트에서는 카카오 자리에 Keycloak이, 배달 앱 자리에 쇼핑몰이 있습니다. 쇼핑몰과 Keycloak이 같은 서비스 소속이라 3번의 동의 화면은 나오지 않습니다.

OAuth 2.0 규칙에는 네 등장인물이 나오고, 규칙은 각자 무엇을 하는지 정합니다.

| 등장인물 | OAuth 용어 | 하는 일 | 카카오 로그인에서 | 이 프로젝트에서 |
| --- | --- | --- | --- | --- |
| 회원 | Resource Owner | 로그인하고 앱에 허락해 주는 사람 | 나 | 쇼핑몰 회원 |
| 앱 | Client | 회원을 로그인 화면으로 보내고 토큰을 받는 앱 | 배달 앱 | React 쇼핑몰 `modo-club-web` |
| 로그인 서버 | Authorization Server | 비밀번호를 확인하고 토큰을 발급 | 카카오 로그인 | Keycloak |
| API 서버 | Resource Server | 토큰을 확인하고 요청을 처리 | 카카오 프로필 API | Spring 결제 서버 |

토큰을 받는 절차는 여러 가지이고, 이 프로젝트는 **Authorization Code + PKCE**를 씁니다.

- Keycloak은 로그인 뒤 JWT 대신 **일회용 교환권**(Authorization Code)을 주소에 붙여 쇼핑몰로 돌려보냅니다. 교환권은 풀어 봐도 아무 정보가 없는 무작위 문자열입니다.
- 쇼핑몰 페이지가 열리면 `keycloak-js`가 이 교환권을 **Keycloak에 다시 보내고**, Keycloak이 확인한 뒤 access token(JWT)을 돌려줍니다. 교환은 라이브러리가 자동으로 하므로 직접 작성한 코드는 없습니다.
- 교환권은 한 번만, 1분 안에 쓸 수 있습니다. 브라우저 주소창과 방문 기록에 JWT 대신 금방 쓸모없어지는 교환권만 남으므로 더 안전합니다.
- **PKCE**(Proof Key for Code Exchange)는 교환권을 가로채도 쓰지 못하게 하는 장치입니다. 로그인을 시작한 앱만 아는 비밀 문자열이 있어야 교환됩니다.

### OpenID Connect: OAuth에 "누구인지"를 더한 로그인 표준

OAuth 2.0은 토큰으로 **무엇을 할 수 있는지**(권한)만 정하고, 로그인한 사람이 **누구인지** 알리는 방법은 정하지 않았습니다. OpenID Connect(OIDC)는 OAuth 2.0 위에 로그인 규칙을 더한 표준입니다.

- 회원 ID는 `sub`, 이메일은 `email`처럼 회원 정보의 이름을 통일합니다.
- 발급처 주소 하나로 공개키 위치 등을 찾는 안내 문서(`/.well-known/openid-configuration`)를 둡니다.

그래서 Spring에는 [application.yml](../src/main/resources/application.yml)의 발급자 주소만 있으면 되고, Keycloak 전용 라이브러리가 필요 없습니다. "구글로 로그인", "카카오로 로그인"도 대부분 OIDC입니다.

### 세 용어의 관계

```text
OpenID Connect = OAuth 2.0 + "누구인지" 규칙   ← 로그인 절차
JWT            = 토큰을 담는 형식 (별개 표준)   ← 토큰 모양

이 프로젝트: OpenID Connect 절차로 로그인하고, JWT 모양의 토큰을 받는다
```

### 이 방식을 쓰는 이유

- 비밀번호 저장·암호화, 회원가입 화면처럼 보안 실수가 잦은 부분을 직접 만들지 않습니다.
- Spring이 로그인 상태를 기억하지 않으므로 서버를 여러 대로 늘려도 됩니다.
- 대신 Keycloak 서버가 하나 더 필요하고, 도둑맞은 토큰은 만료 전까지 쓸 수 있어 유효시간을 5분으로 짧게 둡니다.

## 구성

| 구성 요소 | 위치 | 역할 |
| --- | --- | --- |
| Keycloak 26.7 | Docker Compose `keycloak`, `http://127.0.0.1:8081` | 로그인·회원가입 화면, 토큰 발급, 회원 저장 |
| Keycloak DB | 결제 PostgreSQL 컨테이너 안의 `keycloak` DB | realm 설정, 회원, 세션. Keycloak이 테이블을 직접 만든다 |
| 프런트엔드 | [auth/keycloak.ts](../frontend/src/auth/keycloak.ts) | `keycloak-js`로 로그인 화면 이동, 토큰 보관·갱신 |
| 백엔드 | [SecurityConfiguration](../src/main/java/com/example/payment/config/SecurityConfiguration.java) | Spring Security가 토큰의 서명·발급자·만료·대상을 검증 |

백엔드는 표준(OIDC·JWT) 방식으로만 검증하므로 다른 로그인 서버로 바꿀 때도 발급자 주소만 바꾸면 됩니다.

## Keycloak 용어

| 용어 | 뜻 | 이 프로젝트에서 |
| --- | --- | --- |
| Realm | 회원과 설정을 묶는 독립 공간 | `modo-club` (관리자용 `master`와 분리) |
| Client | Keycloak에 로그인을 맡기는 앱. OAuth 등장인물 중 앱 | `modo-club-web`. 브라우저 앱은 비밀키를 숨길 수 없어 비밀키 없는 public client로 두고 PKCE로 보호 |
| Access token | 로그인 후 받는 JWT | API 요청의 `Authorization: Bearer` 헤더 |

## 로그인 흐름

```text
브라우저(5173)            Keycloak(8081)             Spring(8080)
 로그인 클릭 ──────────▶ 로그인·회원가입 화면
             ◀────────── code를 들고 쇼핑몰로 복귀
 code → 토큰 교환 ─────▶ access token 발급
 API 호출 + Bearer 토큰 ──────────────────────────▶ 서명·발급자·만료·aud 검증
                                                    sub로 주문 주인 기록·확인
```

| 순서 | 일어나는 일 | 코드 |
| --- | --- | --- |
| 1 | 헤더의 **로그인**을 누르면 Keycloak 로그인 화면으로 이동 | [keycloak.ts](../frontend/src/auth/keycloak.ts) `login()` |
| 2 | 이메일·비밀번호 입력. 확인은 Keycloak이 하고 쇼핑몰은 비밀번호를 보지 못함 | Keycloak |
| 3 | Keycloak이 교환권(code)을 붙여 쇼핑몰로 돌려보냄 | Keycloak |
| 4 | `keycloak-js`가 교환권을 Keycloak에 보내 access token(JWT)으로 바꿔 받고 메모리에 보관 | [keycloak.ts](../frontend/src/auth/keycloak.ts) `initAuth()` |
| 5 | 주문·결제 API에 `Authorization: Bearer <token>` 헤더를 붙임 | [paymentApi.ts](../frontend/src/api/paymentApi.ts) |
| 6 | Spring이 서명·발급처·대상·만료를 검사. 실패하면 `401` | [SecurityConfiguration](../src/main/java/com/example/payment/config/SecurityConfiguration.java) |
| 7 | 토큰의 `sub`로 주문 주인을 기록·확인 | [PurchaseOrder](../src/main/java/com/example/payment/order/PurchaseOrder.java) `isOwnedBy()` |
| 8 | API 호출 전에 만료가 30초 안으로 남았으면 토큰을 새로 받음 | [keycloak.ts](../frontend/src/auth/keycloak.ts) `getAccessToken()` |

- 백엔드는 처음 토큰을 검증할 때 Keycloak에서 공개키를 받아 두고, 이후 요청마다 Keycloak을 호출하지 않습니다.
- 토큰은 메모리에만 있어 새로고침하면 사라집니다. Keycloak은 자기 주소(8081)에 로그인 세션 쿠키를 두므로, 새로고침이나 토스 결제창 복귀로 페이지를 다시 열면 숨은 iframe([silent-check-sso.html](../frontend/public/silent-check-sso.html))이 이 세션으로 토큰을 다시 받습니다. 세션은 Keycloak에만 있고 Spring에는 없습니다.
- 로그인한 상태에서 브라우저 개발자 도구(F12)의 **Network** 탭을 열고 주문 요청의 **Request Headers → Authorization**을 보면 실제 토큰(`Bearer eyJ...`)을 볼 수 있습니다.

## 로컬 계정

| 용도 | 계정 | 비밀번호 |
| --- | --- | --- |
| 관리 콘솔 `http://127.0.0.1:8081/admin` | `admin` | `keycloak_admin_local` |
| 테스트 회원 | `buyer@modo.test` | `modo_buyer_local` |
| 다른 회원 (주문 접근 차단 확인용) | `other@modo.test` | `modo_other_local` |

테스트 회원은 [realm 설정 파일](../docker/keycloak/modo-club-realm.json)에 들어 있습니다. 로컬 전용 값이며 운영에서 쓰지 않습니다.

## 관리 콘솔에서 해볼 것

1. 관리 콘솔에 로그인한 뒤 왼쪽 위 realm 선택에서 `modo-club`으로 바꿉니다.
2. **Users**: 회원 목록, 회원 추가·비밀번호 재설정·비활성화
3. **Clients → modo-club-web**: Valid redirect URIs, Web origins, PKCE 설정
4. **Realm settings → Login**: 회원가입 허용, 이메일로 로그인 등
5. `http://127.0.0.1:8081/realms/modo-club/account`: 회원 입장의 계정 화면
6. `http://127.0.0.1:8081/realms/modo-club/.well-known/openid-configuration`: 백엔드가 공개키 주소를 찾는 문서

## realm 설정 바꾸기

- 기준 설정은 [modo-club-realm.json](../docker/keycloak/modo-club-realm.json)입니다. Keycloak은 시작할 때 이 파일을 가져오지만 **realm이 이미 있으면 건너뜁니다.**
- 파일을 고쳤다면 관리 콘솔에서 `modo-club` realm을 삭제한 뒤 `docker compose restart keycloak`으로 다시 가져옵니다. 회원가입으로 만든 회원도 함께 지워집니다.
- 관리 콘솔에서 바꾼 설정은 파일에 반영되지 않습니다. 계속 쓸 설정이면 파일도 함께 고칩니다.

Keycloak 데이터를 모두 지우려면 결제 DB는 두고 `keycloak` DB만 다시 만듭니다.

```powershell
docker compose stop keycloak
docker compose exec postgres psql -U payment -d postgres -c "DROP DATABASE keycloak WITH (FORCE)"
docker compose exec postgres psql -U payment -d postgres -f /docker-entrypoint-initdb.d/01-keycloak.sql
docker compose up -d keycloak
```

## 로그인 화면 디자인

로그인·회원가입 화면은 Keycloak 로그인 테마 `modo-club`이 스토어와 같은 색·카드·버튼으로 꾸밉니다. 기본 테마(`keycloak.v2`)의 화면 구조는 그대로 두고 CSS와 일부 문구만 덮어씁니다.

| 파일 | 역할 |
| --- | --- |
| [theme.properties](../docker/keycloak-themes/modo-club/login/theme.properties) | 부모 테마(`keycloak.v2`)와 추가 CSS 지정, 다크 모드 끔 |
| [modo.css](../docker/keycloak-themes/modo-club/login/resources/css/modo.css) | 배경, 카드, 입력란, 버튼, 링크, 오류 색. 값은 [styles.css](../frontend/src/styles.css)를 따름 |
| [messages_ko.properties](../docker/keycloak-themes/modo-club/login/messages/messages_ko.properties) | `등록` → `회원가입` 등 스토어와 맞춘 문구 |

- 테마 폴더는 Compose가 Keycloak의 `/opt/keycloak/themes/modo-club`에 연결합니다. realm 설정의 `loginTheme`이 이 테마를 고릅니다.
- 위쪽 `MODO CLUB` 글자는 realm의 `displayNameHtml`이며, 스토어 주소(`http://127.0.0.1:5173/`)로 가는 링크입니다. 스토어 주소가 바뀌면 이 값도 바꿉니다.
- 개발 모드에서는 테마를 캐시하지 않으므로 CSS나 문구를 고친 뒤 로그인 화면을 새로고침하면 바로 반영됩니다. 테마 폴더를 처음 연결할 때만 `docker compose up -d keycloak`으로 컨테이너를 다시 만듭니다.
- 테마 적용 전부터 있던 realm은 설정 파일이 다시 적용되지 않습니다. 관리 콘솔 **Realm settings → Themes → Login theme**에서 `modo-club`을, **Realm settings → General → HTML Display name**에 설정 파일의 `displayNameHtml` 값을 넣습니다.

## 서버의 접근 규칙

| 요청 | 규칙 |
| --- | --- |
| `GET /products`, `GET /products/{id}`, `GET /payment-config`, Swagger | 누구나 |
| 주문·결제 시도·승인 API | 로그인 필요. 토큰이 없거나 잘못되면 `401 UNAUTHORIZED` |
| 다른 회원의 주문·시도 | 존재 여부를 알리지 않고 `404 ORDER_NOT_FOUND`·`ATTEMPT_NOT_FOUND` |
| 로그인 도입 전에 만든 주문 | `customer_id`가 비어 있어 누구도 조회·결제할 수 없음. 복구 작업은 계속 처리 |

## 로그인에서 자주 생기는 문제

로컬에서 로그인이 안 될 때 증상으로 원인을 찾는 표입니다.

| 증상 | 원인 |
| --- | --- |
| Keycloak 화면에 `Invalid parameter: redirect_uri` | client의 Redirect URI에 쇼핑몰 주소가 없음 |
| 토큰 교환에서 CORS 오류 | client의 Web origins에 쇼핑몰 주소가 없음 |
| 로그인은 되는데 API가 401 | 토큰의 `iss`나 `aud`가 서버 설정과 다름 |
| 새로고침하면 로그인이 풀림 | `localhost`와 `127.0.0.1`을 섞어 씀. 항상 `127.0.0.1`을 씀 |
| 헤더에 로그인 버튼이 늦게 뜨거나 주문 조회가 401 | Keycloak이 꺼져 있거나 아직 시작 중. 첫 시작은 1분쯤 걸림 |

## 운영 배포 전에 할 일

이 구성은 로컬 개발용입니다. 배포할 때는 `start-dev` 대신 `start` 모드, HTTPS와 고정 hostname, 관리 콘솔 접근 제한, 메일 서버(이메일 인증·비밀번호 찾기), Keycloak DB 백업과 버전 업그레이드 절차를 따로 준비합니다.
