# 로그인과 회원

회원가입·로그인은 오픈소스 인증 서버 [Keycloak](https://www.keycloak.org/)이 맡습니다. 쇼핑몰은 비밀번호를 받지 않고, Keycloak이 발급한 access token으로 회원을 확인합니다. 실행 설정은 [설정 문서](configuration.md#keycloak), API 계약은 [API 문서](api.md)에 있습니다.

한 문장으로 줄이면 **OpenID Connect(OAuth 2.0 기반)로 Keycloak에서 로그인하고, 받은 JWT를 들고 Spring API를 호출합니다. Spring은 세션 없이 JWT로 요청한 회원을 확인하고, API 접근 규칙과 주문 소유권을 검사합니다.** 이 용어들이 처음이면 [로그인 기술 기초](#로그인-기술-기초)부터 읽습니다.

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
| `realm_access.roles` | 회원이 가진 realm 역할 | 쇼핑몰 관리자는 `shop-admin` 포함. Spring이 관리자 API 접근을 판단 |

- **Spring이 로그인 세션을 저장하지 않습니다.** 필요한 정보가 토큰에 있으므로 요청마다 토큰의 서명·발급처·대상·유효기간을 검사해 회원을 확인합니다.
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
| 사용자 | Resource Owner | 사용자 | 나 | 쇼핑몰 회원 |
| 앱 | Client | 사용자가 이용하는 애플리케이션 | 배달 앱 | React 쇼핑몰 `modo-club-web` |
| 로그인 서버 | Authorization Server | 로그인하고 토큰을 발급하는 서버 | 카카오 로그인 | Keycloak |
| API 서버 | Resource Server | 실제 데이터를 가지고 있는 API 서버 | 카카오 프로필 API | Spring 결제 서버 |

토큰을 받는 절차는 여러 가지이고, 이 프로젝트는 **Authorization Code + PKCE**를 씁니다.

- 로그인은 **Keycloak 페이지로 이동해서** 진행하므로, 끝나면 **쇼핑몰 페이지로 다시 이동(리다이렉트)**해야 합니다. 이때는 쇼핑몰이 `fetch`로 JSON을 받는 요청이 아니라 브라우저의 페이지 이동이므로, Keycloak은 JWT 대신 **일회용 교환권**(Authorization Code)을 복귀 URL에 붙여 전달합니다. 교환권 자체로는 API를 호출할 수 없습니다.
- 쇼핑몰 페이지가 열리면 `keycloak-js`가 **`fetch`의 POST 요청으로 교환권을 Keycloak에 보냅니다.** Keycloak은 교환권을 확인한 뒤 access token(JWT)을 **응답 본문의 JSON**으로 돌려줍니다. 이 요청은 페이지 이동이 아니므로 JWT는 주소창이나 방문 기록에 들어가지 않습니다. 교환은 라이브러리가 자동으로 처리하므로 직접 작성한 코드는 없습니다.
- 교환권은 한 번만, 1분 안에 쓸 수 있습니다. 브라우저 주소창과 방문 기록에 JWT 대신 금방 쓸모없어지는 교환권만 남으므로 더 안전합니다.
- **PKCE**(Proof Key for Code Exchange)는 교환권을 가로채도 쓰지 못하게 하는 장치입니다. 로그인을 시작한 앱만 아는 비밀 문자열이 있어야 교환됩니다.

### OpenID Connect: OAuth에 "누구인지"를 더한 로그인 표준

OAuth 2.0은 토큰으로 **무엇을 할 수 있는지**(권한)만 정하고, 로그인한 사람이 **누구인지** 알리는 방법은 정하지 않았습니다. OpenID Connect(OIDC)는 OAuth 2.0 위에 로그인 규칙을 더한 표준입니다.

- `keycloak-js`가 **처음 Keycloak으로 이동하는 로그인 요청 URL의 `scope`에 `openid`를 자동으로 넣습니다**(`scope=openid`). Keycloak은 이 값을 보고 **OIDC 로그인 요청임을 알아봅니다.** `openid`는 JWT나 교환권에 붙이는 값이 아니라 로그인 요청에 넣는 표시입니다.
- 로그인에 성공하면 Keycloak은 **교환권(`code`)을 발급하면서, 그 교환권을 처음 로그인 요청의 정보와 연결해 Keycloak 인증 서버에 저장**합니다. 여기에는 `scope=openid`로 요청했다는 정보도 포함됩니다. 이후 교환권을 복귀 URL에 붙여 쇼핑몰로 보내고, 쇼핑몰이 이 교환권으로 토큰을 요청하면 **Keycloak이 저장해 둔 정보를 찾아 OIDC 로그인 요청이었다는 것을 확인**합니다.
- 교환권을 Keycloak에 보내 토큰으로 바꾸면, JSON 응답에 **`access_token`과 `id_token`이 함께 옵니다.** `id_token`은 로그인한 회원이 누구인지 알려주는 JWT이고, `access_token`은 API를 호출할 때 쓰는 토큰입니다. 이 프로젝트에서는 두 토큰 모두 JWT 형식이며, **Spring API에는 `access_token`을 보냅니다.**
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
| Realm role | realm 안에서 회원에게 주는 역할. 토큰의 `realm_access.roles`에 담김 | `shop-admin` (쇼핑몰 관리자) |

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
| Keycloak 관리자(슈퍼 유저). 관리 콘솔 `http://127.0.0.1:8081/admin` 전용 | `admin` | `keycloak_admin_local` |
| 쇼핑몰 관리자 (`shop-admin` 역할) | `shop-admin@modo.test` | `admin` |
| 테스트 회원 | `buyer@modo.test` | `modo_buyer_local` |
| 다른 회원 (주문 접근 차단 확인용) | `other@modo.test` | `modo_other_local` |

쇼핑몰 관리자와 테스트 회원은 [realm 설정 파일](../docker/keycloak/modo-club-realm.json)에 들어 있습니다. 로컬 전용 값이며 운영에서 쓰지 않습니다.

**관리자가 둘인 이유**: 이름이 비슷하지만 하는 일과 속한 곳이 다릅니다.

| | Keycloak 관리자 `admin` | 쇼핑몰 관리자 `shop-admin@modo.test` |
| --- | --- | --- |
| 속한 realm | `master` | `modo-club` |
| 할 수 있는 일 | 모든 realm의 설정·회원·역할 관리 | 쇼핑몰의 관리자 API(`/admin/**`) 호출. 관리자 홈에서 주문 관리(송장 등록·배송 완료)와 상담 관리 |
| 쇼핑몰 로그인 | 불가. 다른 realm 계정이고, Spring은 `modo-club`이 발급한 토큰만 받음 | 가능. 직원 계정이라 관리자 화면만 쓰고, 주문·결제·마이페이지·고객 상담 창구(문의하기) 같은 일반 회원 기능은 쓰지 않음(서버가 `403`). 상품을 직접 사 보려면 테스트 회원으로 로그인 |
| 만든 방법 | Compose의 `KC_BOOTSTRAP_ADMIN_*`가 첫 실행 때 생성 | realm 역할 `shop-admin`을 준 `modo-club` 회원 |

`master` realm에도 슈퍼 유저 권한을 뜻하는 `admin` 역할이 있으므로, 쇼핑몰 역할은 헷갈리지 않게 `shop-admin`으로 이름 붙였습니다.

## 관리 콘솔에서 해볼 것

1. 관리 콘솔에 로그인한 뒤 왼쪽 위 realm 선택에서 `modo-club`으로 바꿉니다.
2. **Users**: 회원 목록, 회원 추가·비밀번호 재설정·비활성화
3. **Realm roles**: realm 역할 목록. `shop-admin`을 가진 회원 확인
4. **Clients → modo-club-web**: Valid redirect URIs, Web origins, PKCE 설정
5. **Clients → modo-club-web → Client scopes → Evaluate**: 회원을 골라 그 회원이 받을 access token 내용 미리 보기
6. **Realm settings → Login**: 회원가입 허용, 이메일로 로그인 등
7. `http://127.0.0.1:8081/realms/modo-club/account`: 회원 입장의 계정 화면
8. `http://127.0.0.1:8081/realms/modo-club/.well-known/openid-configuration`: 백엔드가 공개키 주소를 찾는 문서

## realm 설정 바꾸기

- 기준 설정은 [modo-club-realm.json](../docker/keycloak/modo-club-realm.json)입니다. Keycloak은 시작할 때 `modo-club` realm이 **없을 때만** 이 파일로 realm을 만들고, **이미 있으면 파일을 읽지 않습니다.** 그래서 파일을 고치기만 해서는 쓰던 환경이 바뀌지도, 회원이 지워지지도 않습니다.
- 파일은 Keycloak DB가 비어 있는 새 환경(다른 PC에서 처음 실행, 볼륨·`keycloak` DB 초기화)에서 쓰입니다.
- 쓰던 환경에 같은 변경을 넣을 때는 realm을 지우지 말고 다음 중 하나로 추가합니다.
  - 관리 콘솔에서 같은 내용을 직접 추가합니다.
  - **Realm settings → 오른쪽 위 Action → Partial import**에 파일을 올립니다. realm은 그대로 두고 역할·회원 등만 추가하며, 이미 있는 항목은 건너뛰거나 덮어쓰도록 고릅니다.
- `modo-club` realm을 삭제하고 `docker compose restart keycloak`으로 다시 가져오면 파일 내용이 그대로 적용되지만, **회원가입으로 만든 회원이 모두 지워집니다.** 파일의 테스트 회원도 새 회원 ID(`sub`)로 다시 만들어져, 이전 주문은 쇼핑몰 DB에 남아 있어도 조회할 수 없게 됩니다.
- 관리 콘솔에서 바꾼 설정은 파일에 반영되지 않습니다. 계속 쓸 설정이면 파일도 함께 고칩니다.

Keycloak 데이터를 모두 지우려면 결제 DB는 두고 `keycloak` DB만 다시 만듭니다. 위와 같이 회원이 모두 지워지고 파일 내용으로 새로 만들어집니다.

```powershell
docker compose stop keycloak
docker compose exec postgres psql -U payment -d postgres -c "DROP DATABASE keycloak WITH (FORCE)"
docker compose exec postgres psql -U payment -d postgres -f /docker-entrypoint-initdb.d/01-keycloak.sql
docker compose up -d keycloak
```

### 쓰던 환경에 쇼핑몰 관리자 추가하기

realm 설정 파일에 쇼핑몰 관리자를 넣기 전부터 쓰던 환경이라면 관리 콘솔에서 한 번 추가합니다.

1. 관리 콘솔에 Keycloak 관리자로 로그인하고, 왼쪽 위 realm 선택을 `modo-club`으로 바꿉니다. `master`에 만들면 쇼핑몰과 무관한 곳에 생깁니다.
2. **Realm roles → Create role**에서 `shop-admin`을 만듭니다.
3. **Users → Add user**에서 `shop-admin@modo.test`를 만들고 **Email verified**를 켭니다. 이름을 비우면 첫 로그인 때 입력 화면이 나옵니다.
4. 만든 회원의 **Credentials → Set password**에서 [로컬 계정](#로컬-계정)의 비밀번호를 넣고 **Temporary**를 끕니다.
5. **Role mapping → Assign role**에서 필터를 **Filter by realm roles**로 바꾸고 `shop-admin`을 줍니다.
6. **Clients → modo-club-web → Client scopes → Evaluate**에서 이 회원을 고르고, access token에 `"realm_access": {"roles": [..., "shop-admin"]}`가 있는지 확인합니다.

역할은 로그인할 때 토큰에 담기므로, 이미 로그인한 상태였다면 로그아웃한 뒤 다시 로그인해야 반영됩니다.

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

## 계정 설정 화면 디자인

마이페이지·관리자 홈의 `계정 설정`이 여는 Keycloak 계정 화면(계정 콘솔)은 계정 테마 `modo-club`이 스토어와 같은 모양으로 꾸밉니다. 이 화면은 Keycloak이 만든 React 앱(PatternFly 5)이라, 화면 구조·메뉴·문구는 그대로 두고 CSS와 로고만 덧입힙니다.

| 파일 | 역할 |
| --- | --- |
| [theme.properties](../docker/keycloak-themes/modo-club/account/theme.properties) | 부모 테마(`keycloak.v3`), 추가 CSS, 로고와 로고 링크, 탭 제목 지정, 다크 모드 끔 |
| [modo-account.css](../docker/keycloak-themes/modo-club/account/resources/css/modo-account.css) | 크림색 배경, 흰 헤더, 흰 왼쪽 메뉴(고른 메뉴는 부드러운 파랑), 흰 카드, 입력란·버튼·알림. 값은 [styles.css](../frontend/src/styles.css)와 로그인 테마를 따름 |
| [modo-logo.svg](../docker/keycloak-themes/modo-club/account/resources/img/modo-logo.svg) | 헤더의 `MODO CLUB` 로고 |

- realm 설정의 `accountTheme`이 이 테마를 고릅니다.
- 로고를 누르면 스토어(`logoUrl`)로 가고, 스토어에서 연 경우 `MODO CLUB(으)로 돌아가기` 링크가 마이페이지로 돌려보냅니다. 배포할 때는 `logoUrl`도 실제 스토어 주소로 바꿉니다.
- 화면 언어는 브라우저 언어나 회원이 계정 화면에서 고른 언어를 따릅니다. 한국어 브라우저에서는 한국어로 나옵니다.
- 계정 테마 폴더를 처음 추가했을 때는 `docker compose restart keycloak`으로 Keycloak을 다시 시작해야 테마 목록에 나타납니다. 그 뒤 CSS 수정은 새로고침만 하면 반영됩니다.
- 테마 적용 전부터 있던 realm은 관리 콘솔 **Realm settings → Themes → Account theme**에서 `modo-club`을 고릅니다. 명령으로 바꿀 수도 있습니다.

```sh
docker exec payment-service-keycloak /opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user admin --password keycloak_admin_local
docker exec payment-service-keycloak /opt/keycloak/bin/kcadm.sh update realms/modo-club -s accountTheme=modo-club
```

Windows의 Git Bash에서는 컨테이너 안 경로가 바뀌지 않게 `MSYS_NO_PATHCONV=1`을 앞에 붙입니다.

## Keycloak·React·Spring의 책임

**인증**은 “요청한 사람이 누구인지” 확인하는 일이고, **인가**는 “그 사람이 이 요청을 해도 되는지” 판단하는 일입니다. Keycloak은 로그인할 때 회원을 확인하고 토큰을 발급합니다. Spring API 서버는 요청마다 그 토큰을 검증하고, API와 데이터에 접근해도 되는지 판단합니다.

| 하는 일 | 담당 | 이 프로젝트에서 처리하는 내용 |
| --- | --- | --- |
| 회원가입·비밀번호 관리 | **Keycloak 인증 서버** | 회원 정보를 저장하고 비밀번호를 관리 |
| 역할 부여 | **Keycloak 인증 서버** | 쇼핑몰 관리자에게 realm 역할 `shop-admin`을 주고, 로그인할 때 access token의 `realm_access.roles`에 담음 |
| 로그인 확인·토큰 발급 | **Keycloak 인증 서버** | 비밀번호를 확인하고 교환권·access token·ID token을 발급. 로그인 세션과 토큰 갱신도 처리 |
| 로그인 절차 연결 | **React 쇼핑몰의 `keycloak-js`** | Keycloak 화면으로 이동, PKCE와 교환권 교환 처리, 받은 토큰을 메모리에 보관·갱신 |
| API 요청에 토큰 전달 | **React 쇼핑몰** | 주문·결제·상담 요청의 `Authorization: Bearer <access token>` 헤더에 토큰을 첨부. 상담 WebSocket은 STOMP `CONNECT` 프레임에 첨부 |
| API 요청의 인증 | **Spring Security** | JWT를 검증하고, 검증된 토큰의 `sub`로 요청한 회원을 확인 |
| API별 접근 허용 | **Spring Security** | 상품·결제 설정 조회는 공개하고, 관리자 API(`/admin/**`)는 `shop-admin` 역할이 있는 회원에게만, 주문·결제·마이페이지·고객 상담 API는 `shop-admin`이 아닌 인증된 회원에게만 허용. 상담 WebSocket(`/ws`)은 연결 요청을 통과시키고 STOMP 프레임에서 토큰과 구독 주소를 검사 |
| 주문·결제 시도의 소유권 확인 | **Spring 서비스 코드** | 요청한 회원의 `sub`와 주문에 저장된 `customer_id`를 비교해 본인 주문·시도에만 접근 허용 |

예를 들어 A 회원의 JWT가 유효하면 Spring의 **인증**을 통과합니다. 이어서 B 회원의 주문을 조회하려 하면, 서비스 코드의 **인가** 검사에서 거부되어 `404 ORDER_NOT_FOUND`를 받습니다. 주문 주인이 누구인지는 Spring의 주문 DB에 저장되어 있고, 이 검사는 Spring에서 구현합니다.

## Spring API 서버의 접근 규칙

아래 규칙은 **상품·주문·결제를 제공하는 Spring API 서버**에 적용됩니다. API별 로그인 필요 여부는 Spring Security 설정에서, 주문·시도의 소유권은 Spring 서비스 코드에서 검사합니다.

| 요청 | 규칙 |
| --- | --- |
| `GET /products`, `GET /products/{id}`, `GET /payment-config`, Swagger | 누구나 |
| 주문·결제 시도·승인 API, 마이페이지 API `/me/**` | 로그인 필요. access token이 없거나 검증에 실패하면 `401 UNAUTHORIZED`. 쇼핑몰 관리자는 `403 FORBIDDEN` |
| 관리자 API `/admin/**` | 쇼핑몰 관리자(`shop-admin` 역할)만. 로그인했지만 역할이 없으면 `403 FORBIDDEN` |
| 고객 상담 API `/chat/**` | 로그인 필요. 상담방 ID를 받지 않고 토큰의 `sub`로 자기 상담방만 사용. 쇼핑몰 관리자는 `403 FORBIDDEN` |
| 상담 실시간 알림 `/ws` | 연결 요청은 통과시키고, STOMP `CONNECT` 프레임의 access token을 검증. 관리자 알림 구독은 `shop-admin`만([상담 문서](chat.md#연결-인증과-구독-권한)) |
| 다른 회원의 주문·시도 | 존재 여부를 알리지 않고 `404 ORDER_NOT_FOUND`·`ATTEMPT_NOT_FOUND` |
| 로그인 도입 전에 만든 주문 | `customer_id`가 비어 있어 누구도 조회·결제할 수 없음. 복구 작업은 계속 처리 |

### Spring Security 설정이 하는 일

[SecurityConfiguration](../src/main/java/com/example/payment/config/SecurityConfiguration.java)은 API 요청이 컨트롤러에 도착하기 전에 실행할 보안 필터와 접근 규칙을 설정합니다. JWT 검증은 Spring Security가 제공하는 기능을 사용합니다.

- **JWT 검증** — `.oauth2ResourceServer(...jwt(...))`로 Bearer JWT 인증을 켭니다. [application.yml](../src/main/resources/application.yml)의 설정에 따라 Keycloak 공개키로 서명을 확인하고, 발급처(`iss`), 대상(`aud`에 `payment-service` 포함), 유효기간을 검사합니다.
- **API 접근 허용** — `.authorizeHttpRequests(...)`로 공개 경로를 지정하고, `/admin/**`는 `.hasRole("shop-admin")`으로 쇼핑몰 관리자만 허용합니다. 주문·결제·마이페이지·고객 상담 경로는 "인증됨 그리고 `shop-admin` 아님"으로 일반 회원만 허용하고, 나머지는 `.authenticated()`로 인증을 요구합니다.
- **역할 읽기** — Spring의 기본 변환기는 토큰의 `scope`만 권한으로 읽고 Keycloak의 `realm_access.roles`는 읽지 않습니다. 그래서 `.jwt(...jwtAuthenticationConverter(...))`로 realm 역할을 `ROLE_shop-admin` 같은 Spring 권한으로 바꿉니다. `hasRole("shop-admin")`은 이 `ROLE_shop-admin` 권한을 확인합니다. 상담 WebSocket 연결도 같은 변환기를 씁니다.
- **WebSocket 연결** — 브라우저 WebSocket은 연결 요청에 `Authorization` 헤더를 붙일 수 없으므로 `/ws`는 `.permitAll()`로 통과시킵니다. 토큰은 연결 직후 STOMP `CONNECT` 프레임에서 [ChatSocketAuthorization](../src/main/java/com/example/payment/chat/infrastructure/websocket/ChatSocketAuthorization.java)이 HTTP API와 같은 방식으로 검증합니다.
- **요청마다 인증** — `STATELESS`로 Spring의 로그인 세션을 저장하지 않습니다. 보호된 API를 호출할 때마다 access token을 보내야 합니다.
- **CSRF 검사 비활성화** — 이 API는 인증용 쿠키 대신 요청의 `Authorization` 헤더에 담긴 Bearer 토큰으로 인증하므로, `.csrf(...disable())`로 CSRF 토큰 검사를 끕니다.
- **인증 실패 응답** — `.authenticationEntryPoint(unauthorized)`로 인증 실패 시 `401`, 표준 `WWW-Authenticate` 헤더, `{code, message}` JSON을 반환하도록 설정합니다.
- **권한 부족 응답** — `.accessDeniedHandler(forbidden)`로 로그인은 했지만 필요한 역할이 없으면 `403`, `WWW-Authenticate` 헤더, `{"code": "FORBIDDEN", ...}` JSON을 반환합니다.

### Spring 서비스 코드가 추가로 검사하는 일

보안 처리는 설정 클래스 외에도 주문·결제 서비스에 구현되어 있습니다.

- **회원 ID와 소유권** — [OrderController](../src/main/java/com/example/payment/order/OrderController.java)는 검증된 JWT에서 `sub`를 꺼내 서비스에 전달합니다. [OrderService](../src/main/java/com/example/payment/order/OrderService.java)와 결제 서비스는 이 값으로 주문 주인을 확인합니다. 요청 본문에 회원 ID를 적어 다른 회원의 주문에 접근할 수는 없습니다.
- **결제 금액** — 주문 금액은 서버의 상품 DB 가격으로 계산합니다. 결제 승인 요청의 금액이 저장된 주문 금액과 다르면 거부하고, 토스 승인에도 서버에 저장된 금액을 사용합니다.
- **결제 상태와 중복 요청** — [PaymentPreparationService](../src/main/java/com/example/payment/payment/application/PaymentPreparationService.java)는 승인 전에 주문 상태, 결제 키 중복, 결제 시도가 해당 주문에 속하는지도 검사합니다. 자세한 처리는 [결제 승인 흐름](architecture.md#결제-승인-흐름)에 있습니다.
- **토스 시크릿 키** — Spring 서버에서만 사용해 토스 승인·조회 API를 호출합니다. 브라우저에 제공하는 `/payment-config`에는 클라이언트 키와 UI 설정만 담습니다. 키의 용도는 [토스 키](configuration.md#토스-키)에 설명되어 있습니다.

## 로그인에서 자주 생기는 문제

로컬에서 로그인이 안 될 때 증상으로 원인을 찾는 표입니다.

| 증상 | 원인 |
| --- | --- |
| Keycloak 화면에 `Invalid parameter: redirect_uri` | client의 Redirect URI에 쇼핑몰 주소가 없음 |
| 토큰 교환에서 CORS 오류 | client의 Web origins에 쇼핑몰 주소가 없음 |
| 로그인은 되는데 API가 401 | 토큰의 `iss`나 `aud`가 서버 설정과 다름 |
| 쇼핑몰 관리자인데 관리자 API가 403 | 토큰에 `shop-admin`이 없음. 역할을 `master`가 아닌 `modo-club` realm에 만들었는지 확인하고, 역할을 준 뒤 로그아웃·재로그인 |
| `계정 설정` 화면이 `Something went wrong`, 계정 API가 401 | 회원에게 기본 역할 `default-roles-modo-club`(계정 화면 권한 포함)이 없음. 예전 설정 파일로 만든 테스트 회원에게 생길 수 있음. **Users → 회원 → Role mapping → Assign role**에서 `default-roles-modo-club`을 주고 다시 로그인. 지금 설정 파일은 테스트 회원에게 이 역할을 줌 |
| 새로고침하면 로그인이 풀림 | `localhost`와 `127.0.0.1`을 섞어 씀. 항상 `127.0.0.1`을 씀 |
| 헤더에 로그인 버튼이 늦게 뜨거나 주문 조회가 401 | Keycloak이 꺼져 있거나 아직 시작 중. 첫 시작은 1분쯤 걸림 |

## 운영 배포 전에 할 일

이 구성은 로컬 개발용입니다. 배포할 때는 `start-dev` 대신 `start` 모드, HTTPS와 고정 hostname, 관리 콘솔 접근 제한, 메일 서버(이메일 인증·비밀번호 찾기), Keycloak DB 백업과 버전 업그레이드 절차를 따로 준비합니다.
