# Payment Service

[![CI](https://github.com/lee-gimoon/payment-service/actions/workflows/ci.yml/badge.svg)](https://github.com/lee-gimoon/payment-service/actions/workflows/ci.yml)

Spring Boot와 React로 구성한 티셔츠 쇼핑몰의 주문·결제 서비스입니다. MODO CLUB 스토어에서 상품과 사이즈를 선택하고, 서버가 계산한 주문 금액으로 토스페이먼츠 결제를 진행합니다.

현재 버전은 **토스 테스트 키를 사용하는 시연용 서비스**로, 결제는 실제로 청구되지 않습니다. 회원가입·로그인은 Keycloak이 맡으며, 택배사 배송 추적 연동, 재고 보충·수정 기능, 취소·환불은 구현되어 있지 않습니다.

## 데모

**스토어:** https://frontend-production-ba4e.up.railway.app

| 계정 | 이메일 | 비밀번호 | 둘러볼 기능 |
| --- | --- | --- | --- |
| 일반 회원 | `user@modo-club.shop` | `user` | 장바구니, 배송지, 테스트 결제, 주문 확인·마이페이지, 1:1 문의 |
| 쇼핑몰 관리자 | `admin@modo-club.shop` | `admin` | 관리자 홈, 주문 관리(송장 등록·배송 완료), 상담 답변 |

- 헤더의 `로그인`을 누르면 로그인 화면(Keycloak)으로 이동합니다. 일반 회원은 직접 `회원가입`해서 써도 됩니다. 쇼핑몰 관리자는 Keycloak에서 역할을 준 직원 계정이라 회원가입으로는 만들 수 없어 위 계정을 제공합니다.
- 결제는 토스페이먼츠 **테스트 키**로 동작해 실제로 청구되지 않습니다.
- 방문이 없으면 서버를 재워 두므로, 한동안 아무도 접속하지 않았다면 `서버를 깨우고 있습니다` 안내와 함께 첫 화면과 로그인 버튼이 뜨기까지 수십 초 걸릴 수 있습니다([Railway 배포](docs/deployment.md)).
- 여러 사람이 함께 쓰는 공개 계정입니다. **실제 이름·연락처·주소는 입력하지 마세요.** 관리자 계정은 모든 주문의 배송지를 볼 수 있습니다. 다른 사람이 쓸 수 있도록 데모 계정의 비밀번호와 계정 정보는 바꾸지 말아 주세요.

## 주요 기능

- 상품 카탈로그, 아바타 착용 미리보기, 옵션별 장바구니
- 사이즈별 재고와 품절·남은 수량 표시, 상품 화면의 수량 선택, 결제 승인 직전 재고 차감과 승인 실패 시 복원
- Keycloak 회원가입·로그인, 주문한 회원만 주문 조회·결제
- 서버 가격으로 계산한 주문과 구매 당시 상품 정보 보존
- 토스 결제창 인증과 서버 승인(카드·국내 간편결제)
- 결제 시도 이력 보존, 인증 취소·실패 또는 승인 실패 확정 후 같은 주문에서 재시도
- 내 주문의 주문번호를 이용한 주문·결제 상태 조회
- 마이페이지: 내 주문 내역, 배송지 관리(우편번호 찾기, 기본 배송지), Keycloak 계정 설정 연결
- 결제 전 배송지 선택과 배송 메모. 고른 배송지는 주문에 복사해 보존
- 관리자 홈과 주문 관리: 결제 완료 주문의 송장 등록, 배송 완료 처리, 고객 화면의 배송 단계와 배송 조회 링크
- 쇼핑몰 관리자 계정은 직원 계정으로 분리해 주문·결제·마이페이지를 막음
- 고객 문의 창과 관리자 상담 관리 화면의 1:1 실시간 상담(WebSocket), 재전송 중복 방지와 읽음 표시([상담 문서](docs/chat.md))

## 결제 설계

- 결제창 인증 성공과 서버 승인 완료를 구분하고, 승인은 저장된 주문 금액으로만 요청합니다.
- 같은 주문에는 진행 중이거나 성공한 승인을 하나만 허용합니다.
- 승인 결과를 모르면 새 결제를 막고 토스 조회로 확정하며, 확정하지 못하면 수동 확인 대상으로 남깁니다.
- 결제 시도 이력과, 승인 성공이 확인된 결제 기록을 따로 보존합니다.
- 재고는 승인 요청 직전에 확보하고, 승인 실패가 확인될 때만 돌려줍니다. 품절이면 승인을 요청하지 않아 청구되지 않습니다.

흐름 그림과 상세 규칙은 [아키텍처](docs/architecture.md)에 있습니다.

## 기술 구성

| 영역 | 구성 |
| --- | --- |
| 백엔드 | Java 21, Spring Boot 4.1, Spring Data JPA |
| 데이터베이스 | PostgreSQL 18, Flyway |
| 프런트엔드 | React 19, TypeScript, Vite |
| 결제 | 토스페이먼츠 SDK v2 결제창형, 서버 승인·조회 API |
| 인증 | Keycloak 26.7 (OIDC), Spring Security OAuth2 Resource Server, keycloak-js |
| 실시간 상담 | Spring WebSocket(STOMP, 내장 메시지 브로커), @stomp/stompjs |
| 테스트 | JUnit, Testcontainers, Node.js 내장 테스트 |
| 배포 | Docker, nginx, Railway(방문이 없으면 백엔드·Keycloak을 재움) |

정확한 의존성 버전은 [build.gradle](build.gradle)과 [package-lock.json](frontend/package-lock.json)에 있습니다.

## 시작하기

### 준비 사항

- Java 21
- Node.js 22.18 이상과 npm
- Docker Compose를 사용할 수 있는 실행 중인 Docker

아래 명령은 저장소 루트에서 시작합니다. 백엔드와 프런트엔드는 각각 별도 터미널에서 실행합니다.

### 1. 데이터베이스와 Keycloak 실행

```sh
docker compose up -d
```

PostgreSQL, pgAdmin, Keycloak이 실행됩니다. Keycloak은 첫 시작에 1분쯤 걸리고 메모리를 최대 1.5GB 씁니다. 기본 연결 정보는 [설정 문서](docs/configuration.md)에 있습니다.

Keycloak을 추가하기 전부터 쓰던 DB 볼륨이라면 `keycloak` DB를 한 번 직접 만들어야 합니다. 방법은 [설정 문서의 Keycloak](docs/configuration.md#keycloak)에 있습니다.

처음 백엔드를 시작하면 Flyway가 스키마를 생성하고 초기 상품 10종과 사이즈별 재고를 등록합니다. 기존 DB에는 아직 적용하지 않은 마이그레이션만 실행합니다.

### 2. 토스 설정과 백엔드 실행

결제까지 확인하려면 같은 상점에서 발급한 **주문서형·결제창형 테스트 키 한 쌍**(`test_gck_`, `test_gsk_`)이 필요합니다([토스 API 키 안내](https://docs.tosspayments.com/reference/using-api/api-keys)). 키 없이 실행하면 상품·주문 API는 동작하지만 화면의 결제 버튼은 비활성화됩니다.

PowerShell:

```powershell
$env:TOSS_CLIENT_KEY = 'test_gck_REPLACE_WITH_YOUR_KEY'
$env:TOSS_SECRET_KEY = 'test_gsk_REPLACE_WITH_YOUR_KEY'
.\gradlew.bat bootRun
```

macOS / Linux:

```sh
export TOSS_CLIENT_KEY='test_gck_REPLACE_WITH_YOUR_KEY'
export TOSS_SECRET_KEY='test_gsk_REPLACE_WITH_YOUR_KEY'
./gradlew bootRun
```

키 값은 발급받은 테스트 키로 바꿉니다. `.env` 파일은 자동으로 읽지 않으므로 터미널이나 IDE 실행 설정에 지정하고, 시크릿 키는 서버에만 둡니다. 나머지 환경변수는 [설정 문서](docs/configuration.md)에 있습니다.

### 3. 프런트엔드 실행

저장소 루트에서 별도 터미널을 열어 실행합니다.

```sh
cd frontend
npm ci
npm run dev
```

Vite 개발 서버가 API 요청을 백엔드 8080 포트로 전달합니다. 상품 상세에서 사이즈를 선택하고 장바구니로 이동한 뒤, 로그인하고 결제를 시작할 수 있습니다. 테스트 회원 계정은 [로그인과 회원](docs/authentication.md#로컬-계정)에 있습니다. 테스트 키 결제는 실제 청구되지 않습니다.

### 접속 주소

| 서비스 | 기본 주소 | 제공 프로세스 |
| --- | --- | --- |
| 스토어 | [http://127.0.0.1:5173](http://127.0.0.1:5173) | 프런트엔드 개발 서버 (Vite) |
| Swagger UI | [http://127.0.0.1:8080/swagger-ui.html](http://127.0.0.1:8080/swagger-ui.html) | 백엔드 (Spring Boot) |
| OpenAPI JSON | [http://127.0.0.1:8080/v3/api-docs](http://127.0.0.1:8080/v3/api-docs) | 백엔드 (Spring Boot) |
| pgAdmin | [http://127.0.0.1:5050](http://127.0.0.1:5050) | Docker Compose의 pgAdmin 컨테이너 |
| Keycloak 관리 콘솔 | [http://127.0.0.1:8081/admin](http://127.0.0.1:8081/admin) | Docker Compose의 Keycloak 컨테이너 |

스토어 화면의 상품 기능은 백엔드와 PostgreSQL, 로그인과 주문·결제 기능은 Keycloak까지 실행해야 합니다.

### 종료

백엔드와 프런트엔드는 실행한 터미널에서 `Ctrl+C`로 종료합니다. IDE에서 실행한 백엔드는 해당 실행 구성을 중지합니다. PostgreSQL, pgAdmin, Keycloak은 저장소 루트에서 중지합니다.

```sh
docker compose stop
```

DB와 회원 데이터는 유지되며, 다시 사용할 때는 `docker compose up -d`로 시작합니다.

## 테스트와 빌드

Docker가 실행 중인 상태에서 백엔드를 검증합니다.

```powershell
.\gradlew.bat test bootJar
```

macOS / Linux에서는 `./gradlew test bootJar`를 사용합니다. 프런트엔드는 `frontend/`에서 실행합니다.

```sh
npm test
npm run build
```

빌드 결과는 백엔드 JAR와 `frontend/dist/`로 나뉩니다. `main` 푸시와 PR마다 GitHub Actions([ci.yml](.github/workflows/ci.yml))가 같은 테스트와 빌드를 돌리고, Railway는 이 검사를 통과한 커밋만 배포합니다. 테스트 범위와 수동 결제 확인 절차는 [개발 가이드](docs/development.md)에, 운영 이미지와 Railway 배포 순서는 [배포 문서](docs/deployment.md)에 있습니다.

## 문서

| 문서 | 내용 |
| --- | --- |
| [아키텍처](docs/architecture.md) | 결제 도메인 그림, 핵심 규칙, 상태, 재고, 복구 정책, 설계 결정 |
| [중복 결제 방지](docs/duplicate-payment-prevention.md) | 같은 주문의 중복 승인을 막는 5단계 |
| [API](docs/api.md) | 엔드포인트, 요청 예시, 응답 상태와 오류 |
| [1:1 상담](docs/chat.md) | 고객·관리자 상담의 규칙, 테이블, 메시지 저장 순서, WebSocket 실시간 전달과 재연결 |
| [WebSocket 주소와 STOMP 프레임](docs/websocket-basics.md) | `/ws` 연결 주소, 연결 안의 구독 채널과 STOMP 프레임, 요청·응답 순서 그림 |
| [Spring이 WebSocket 설정을 읽는 방식](docs/websocket-configuration.md) | `@EnableWebSocketMessageBroker`부터 채팅 알림까지, Spring이 설정 클래스를 찾아 STOMP 브로커를 만드는 순서 |
| [로그인과 회원](docs/authentication.md) | 로그인 기술 기초(OAuth 2.0·OIDC·JWT), 로그인 흐름, 테스트 계정, realm 설정 |
| [설정](docs/configuration.md) | 환경변수, 토스 키, 로컬 DB·pgAdmin·Keycloak 연결 |
| [개발 가이드](docs/development.md) | 테스트, 빌드, 스키마 변경, 기여 시 확인 사항 |
| [Railway 배포](docs/deployment.md) | 운영 서비스 구성, 방문이 없으면 잠드는 방식, Railway 설정 순서, 비용 |
| [UI 디자인 기준](DESIGN.md) | 현재 화면의 스타일과 결제 문구 |

## 라이선스

현재 저장소에는 라이선스 파일이 없습니다.
