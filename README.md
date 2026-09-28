# Payment Service

Spring Boot와 React로 구성한 티셔츠 쇼핑몰의 주문·결제 서비스입니다. MODO CLUB 스토어에서 상품과 사이즈를 선택하고, 서버가 계산한 주문 금액으로 토스페이먼츠 결제를 진행합니다.

현재 버전은 **토스 테스트 키를 사용하는 로컬 개발용 서비스**입니다. 로그인, 주문 접근 권한, 배송·재고 관리, 취소·환불은 구현되어 있지 않습니다.

## 주요 기능

- 상품 카탈로그, 아바타 착용 미리보기, 옵션별 장바구니
- 서버 가격으로 계산한 주문과 구매 당시 상품 정보 보존
- 토스 결제창 인증과 서버 승인(카드·국내 간편결제)
- 결제 시도 이력 보존, 인증 취소·실패 또는 승인 실패 확정 후 같은 주문에서 재시도
- 주문번호를 이용한 주문·결제 상태 조회

## 결제 설계

결제창의 인증 성공과 서버의 승인 완료를 구분합니다. 서버는 저장된 주문 금액으로만 승인을 요청하며, 동일 주문의 중복 승인을 차단합니다.

승인 결과가 불명확하면 새 결제를 막고 토스 조회로 확인합니다. 자동으로 확정하지 못한 건은 수동 확인 대상으로 남깁니다. 결제 시도 이력과 승인 성공이 확인된 결제 기록은 별도로 보존합니다.

데이터 모델, 중복 방지 범위와 복구 정책은 [아키텍처](docs/architecture.md)에 있습니다.

## 기술 구성

| 영역 | 구성 |
| --- | --- |
| 백엔드 | Java 21, Spring Boot 4.1, Spring Data JPA |
| 데이터베이스 | PostgreSQL 18, Flyway |
| 프런트엔드 | React 19, TypeScript, Vite |
| 결제 | 토스페이먼츠 SDK v2 결제창형, 서버 승인·조회 API |
| 테스트 | JUnit, Testcontainers, Node.js 내장 테스트 |

정확한 의존성 버전은 [build.gradle](build.gradle)과 [package-lock.json](frontend/package-lock.json)에 있습니다.

## 시작하기

### 준비 사항

- Java 21
- Node.js 22.18 이상과 npm
- Docker Compose를 사용할 수 있는 실행 중인 Docker

아래 명령은 저장소 루트에서 시작합니다. 백엔드와 프런트엔드는 각각 별도 터미널에서 실행합니다.

### 1. 데이터베이스 실행

```sh
docker compose up -d
```

PostgreSQL과 pgAdmin이 실행됩니다. 기본 연결 정보는 [설정 문서](docs/configuration.md)에 있습니다.

처음 백엔드를 시작하면 Flyway가 스키마를 생성하고 초기 상품 10종을 등록합니다. 기존 DB에는 아직 적용하지 않은 마이그레이션만 실행합니다.

### 2. 토스 설정과 백엔드 실행

결제까지 확인하려면 같은 상점에서 발급된 **주문서형·결제창형 테스트 키 한 쌍**을 사용합니다. 프로젝트는 `test_gck_` 클라이언트 키와 `test_gsk_` 시크릿 키만 허용합니다. 키 발급과 종류는 [토스 공식 API 키 안내](https://docs.tosspayments.com/reference/using-api/api-keys)를 참고하세요.

상품 조회·장바구니와 주문 API를 개발하려면 두 환경변수를 모두 설정하지 않은 상태에서 마지막 실행 명령만 사용합니다. 이때 화면의 결제 버튼은 비활성화되며, 장바구니에서 주문 생성·결제 시작은 할 수 없습니다.

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

위 키 값은 자리 표시자입니다. 발급받은 테스트 키로 교체하고, 백엔드를 실행하는 터미널이나 IDE 실행 설정에 전달합니다. Spring Boot는 `.env` 파일을 자동으로 읽지 않습니다. 시크릿 키는 서버에만 설정하며 프런트엔드 환경변수는 필요하지 않습니다.

결제 어드민의 UI는 카드·국내 간편결제에 맞춰 설정합니다. UI variant 키, DB 연결과 기타 환경변수는 [설정 문서](docs/configuration.md)에 있습니다.

### 3. 프런트엔드 실행

저장소 루트에서 별도 터미널을 열어 실행합니다.

```sh
cd frontend
npm ci
npm run dev
```

Vite 개발 서버가 API 요청을 백엔드 8080 포트로 전달합니다. 상품 상세에서 사이즈를 선택하고 장바구니로 이동한 뒤 결제를 시작할 수 있습니다. 테스트 키 결제는 실제 청구되지 않습니다.

### 접속 주소

| 서비스 | 기본 주소 | 제공 프로세스 |
| --- | --- | --- |
| 스토어 | [http://127.0.0.1:5173](http://127.0.0.1:5173) | 프런트엔드 개발 서버 (Vite) |
| Swagger UI | [http://127.0.0.1:8080/swagger-ui.html](http://127.0.0.1:8080/swagger-ui.html) | 백엔드 (Spring Boot) |
| OpenAPI JSON | [http://127.0.0.1:8080/v3/api-docs](http://127.0.0.1:8080/v3/api-docs) | 백엔드 (Spring Boot) |
| pgAdmin | [http://127.0.0.1:5050](http://127.0.0.1:5050) | Docker Compose의 pgAdmin 컨테이너 |

스토어 화면의 상품·주문·결제 기능을 사용하려면 백엔드와 PostgreSQL도 실행해야 합니다.

### 종료

백엔드와 프런트엔드는 실행한 터미널에서 `Ctrl+C`로 종료합니다. IDE에서 실행한 백엔드는 해당 실행 구성을 중지합니다. PostgreSQL과 pgAdmin은 저장소 루트에서 중지합니다.

```sh
docker compose stop
```

DB 데이터는 유지되며, 다시 사용할 때는 `docker compose up -d`로 시작합니다.

## 테스트와 빌드

Docker가 실행 중인 상태에서 백엔드를 검증합니다.

```powershell
.\gradlew.bat test bootJar
```

macOS / Linux에서는 `./gradlew test bootJar`를 사용합니다. 통합 테스트는 별도 PostgreSQL 컨테이너를 생성하며 토스 API는 테스트 대역으로 검증합니다. 테스트 보고서는 `build/reports/tests/test/index.html`, 실행 파일은 `build/libs/`에 생성됩니다.

프런트엔드 디렉터리에서는 다음을 실행합니다.

```sh
npm test
npm run build
```

변경 사항별 검증과 수동 결제 확인 절차는 [개발 가이드](docs/development.md)에 있습니다.

빌드 결과는 백엔드 JAR와 `frontend/dist/`로 나뉩니다. 운영 배포 구성은 포함되어 있지 않습니다.

## 문서

| 문서 | 내용 |
| --- | --- |
| [아키텍처](docs/architecture.md) | 도메인, 트랜잭션 경계, 승인·복구, 중복 방지와 실패 처리 범위 |
| [API](docs/api.md) | 엔드포인트, 요청 예시, 응답 상태와 오류 |
| [설정](docs/configuration.md) | 환경변수, 토스 키, 로컬 DB·pgAdmin 연결 |
| [개발 가이드](docs/development.md) | 테스트, 빌드, 스키마 변경, 기여 시 확인 사항 |
| [UI 디자인 기준](DESIGN.md) | 현재 화면의 스타일과 결제 문구 |

## 라이선스

현재 저장소에는 라이선스 파일이 없습니다.
