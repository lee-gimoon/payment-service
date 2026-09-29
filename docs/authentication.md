# 로그인과 회원

회원가입·로그인은 오픈소스 인증 서버 [Keycloak](https://www.keycloak.org/)이 맡습니다. 쇼핑몰은 비밀번호를 받지 않고, Keycloak이 발급한 access token으로 회원을 확인합니다. 실행 설정은 [설정 문서](configuration.md#keycloak), API 계약은 [API 문서](api.md)에 있습니다.

## 구성

| 구성 요소 | 위치 | 역할 |
| --- | --- | --- |
| Keycloak 26.7 | Docker Compose `keycloak`, `http://127.0.0.1:8081` | 로그인·회원가입 화면, 토큰 발급, 회원 저장 |
| Keycloak DB | 결제 PostgreSQL 컨테이너 안의 `keycloak` DB | realm 설정, 회원, 세션. Keycloak이 테이블을 직접 만든다 |
| 프런트엔드 | [auth/keycloak.ts](../frontend/src/auth/keycloak.ts) | `keycloak-js`로 로그인 화면 이동, 토큰 보관·갱신 |
| 백엔드 | [SecurityConfiguration](../src/main/java/com/example/payment/config/SecurityConfiguration.java) | Spring Security가 토큰의 서명·발급자·만료·대상을 검증 |

백엔드에는 Keycloak 전용 라이브러리가 없습니다. 표준(OIDC·JWT) 방식으로 검증하므로 다른 로그인 서버로 바꿀 때도 발급자 주소만 바꾸면 됩니다.

## 핵심 개념

| 개념 | 뜻 | 이 프로젝트에서 |
| --- | --- | --- |
| Realm | 회원과 설정을 묶는 독립 공간 | `modo-club` (관리자용 `master`와 분리) |
| Client | Keycloak에 로그인을 맡기는 앱 | `modo-club-web` (React, 비밀키 없는 public client + PKCE) |
| Access token | 로그인 후 받는 서명된 JWT. 기본 5분 유효 | API 요청의 `Authorization: Bearer` 헤더 |
| `sub` | 회원 고유 ID | `purchase_orders.customer_id`에 저장 |
| `aud` | 토큰을 받을 서비스 | `payment-service`. 다른 서비스용 토큰은 거부 |

## 로그인 흐름

```text
브라우저(5173)            Keycloak(8081)             Spring(8080)
 로그인 클릭 ──────────▶ 로그인·회원가입 화면
             ◀────────── code를 들고 쇼핑몰로 복귀
 code → 토큰 교환 ─────▶ access token 발급
 API 호출 + Bearer 토큰 ──────────────────────────▶ 서명·발급자·만료·aud 검증
                                                    sub로 주문 주인 기록·확인
```

- 로그인 화면은 Keycloak 사이트에 있습니다. 비밀번호는 Keycloak으로만 전송되고 쇼핑몰은 토큰만 받습니다.
- 백엔드는 처음 토큰을 검증할 때 Keycloak에서 공개키를 받아 두고, 이후 요청마다 Keycloak을 호출하지 않습니다.
- 새로고침이나 토스 결제창 복귀로 페이지를 다시 열면, 숨은 iframe([silent-check-sso.html](../frontend/public/silent-check-sso.html))이 Keycloak 세션으로 토큰을 다시 받습니다.

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

## 자주 막히는 곳

| 증상 | 원인 |
| --- | --- |
| Keycloak 화면에 `Invalid parameter: redirect_uri` | client의 Redirect URI에 쇼핑몰 주소가 없음 |
| 토큰 교환에서 CORS 오류 | client의 Web origins에 쇼핑몰 주소가 없음 |
| 로그인은 되는데 API가 401 | 토큰의 `iss`나 `aud`가 서버 설정과 다름 |
| 새로고침하면 로그인이 풀림 | `localhost`와 `127.0.0.1`을 섞어 씀. 항상 `127.0.0.1`을 씀 |
| 헤더에 로그인 버튼이 늦게 뜨거나 주문 조회가 401 | Keycloak이 꺼져 있거나 아직 시작 중. 첫 시작은 1분쯤 걸림 |

## 운영 배포 전에 할 일

이 구성은 로컬 개발용입니다. 배포할 때는 `start-dev` 대신 `start` 모드, HTTPS와 고정 hostname, 관리 콘솔 접근 제한, 메일 서버(이메일 인증·비밀번호 찾기), Keycloak DB 백업과 버전 업그레이드 절차를 따로 준비합니다.
