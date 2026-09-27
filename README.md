# Payment Service

Spring Boot와 React로 구성한 티셔츠 쇼핑몰의 주문·결제 서비스입니다. MODO CLUB 스토어에서 상품과 사이즈를 선택하고, 서버가 확정한 주문 금액으로 토스페이먼츠 결제수단 인증과 최종 승인을 진행합니다.

현재 버전은 **토스 테스트 키를 사용하는 로컬 개발용 서비스**입니다. 로그인, 주문 접근 권한, 배송·재고 관리, 취소·환불은 구현되어 있지 않습니다.

## 주요 기능

- 상품 카탈로그, 아바타 착용 미리보기, 옵션별 장바구니
- 서버 가격으로 계산한 주문과 구매 당시 상품 정보 보존
- 결제 시도 이력과 카드·국내 간편결제 승인
- 동일 결제 요청의 중복 승인 방지와 명확한 실패 후 재시도
- 승인 결과가 불확실할 때 한 번 재조회하고 확인이 필요한 거래 기록
- 주문번호를 이용한 저장 결과 조회

결제수단 인증 성공만으로 주문을 완료하지 않습니다. 서버가 토스의 승인 결과와 주문번호·결제 키·금액·통화를 검증하고 저장한 경우에만 결제가 완료됩니다.

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

Flyway는 서버 시작 시 [V1 초기 스키마](src/main/resources/db/migration/V1__initial_schema.sql)를 적용하고 상품 10종을 등록합니다. 현재 V1은 새 DB를 위한 정의입니다. 이전 마이그레이션 이력이 있는 DB는 그대로 업그레이드할 수 없으며, 기존 데이터를 유지하려면 [별도 개발 DB를 지정](docs/development.md)합니다.

### 2. 토스 설정과 백엔드 실행

같은 상점에서 발급된 **주문서형·결제창형 테스트 키 한 쌍**을 사용합니다. 프로젝트는 `test_gck_` 클라이언트 키와 `test_gsk_` 시크릿 키만 허용합니다. 키 발급과 종류는 [토스 공식 API 키 안내](https://docs.tosspayments.com/reference/using-api/api-keys)를 참고하세요.

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

위 값은 실제 키로 교체해야 하는 자리 표시자입니다. 시크릿 키는 서버 환경변수로만 설정합니다. 두 키를 모두 생략하면 상품·주문 기능을 이용할 수 있고 결제는 비활성화됩니다.

결제 어드민의 UI는 카드·국내 간편결제에 맞춰 설정합니다. 다른 UI 변형을 사용하는 경우의 설정은 [configuration.md](docs/configuration.md)에 있습니다.

### 3. 프런트엔드 실행

```sh
cd frontend
npm ci
npm run dev
```

| 서비스 | 기본 주소 |
| --- | --- |
| 스토어 | [http://127.0.0.1:5173](http://127.0.0.1:5173) |
| Swagger UI | [http://127.0.0.1:8080/swagger-ui.html](http://127.0.0.1:8080/swagger-ui.html) |
| OpenAPI | [http://127.0.0.1:8080/v3/api-docs](http://127.0.0.1:8080/v3/api-docs) |
| pgAdmin | [http://127.0.0.1:5050](http://127.0.0.1:5050) |

Vite 개발 서버가 API 요청을 백엔드 8080 포트로 전달합니다. 상품 상세에서 사이즈를 선택하고 장바구니로 이동한 뒤 결제를 시작할 수 있습니다. 테스트 키 결제는 실제 청구되지 않습니다. [토스 결제창형 연동 가이드](https://docs.tosspayments.com/guides/v2/payment-widget/integration-window)

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

## 문서

| 문서 | 내용 |
| --- | --- |
| [아키텍처](docs/architecture.md) | 도메인, 트랜잭션 경계, 승인·재조회, 중복 방지 |
| [API](docs/api.md) | 엔드포인트, 요청 예시, 응답 상태와 오류 |
| [설정](docs/configuration.md) | 환경변수, 토스 키, 로컬 DB·pgAdmin 연결 |
| [개발 가이드](docs/development.md) | 테스트, 빌드, 스키마 변경, 기여 시 확인 사항 |
| [UI 디자인 기준](DESIGN.md) | 현재 화면의 스타일과 결제 문구 |

## 현재 범위

승인 결과가 불확실하면 같은 요청 안에서 한 번 조회합니다. 금액·통화 불일치나 확인할 수 없는 결과는 `REVIEW_REQUIRED`로 기록하며 자동 취소를 요청하지 않습니다. 주문 조회는 저장된 DB 결과를 반환합니다.

백그라운드 재처리, 웹훅, 서버 중단 후 자동 복구는 구현되어 있지 않습니다. 승인 이후 저장 장애가 발생한 거래는 토스 내역과 DB 기록을 대조해 확인해야 합니다. 관련 경계와 처리 규칙은 [아키텍처 문서](docs/architecture.md)에 있습니다.

## 라이선스

현재 저장소에는 라이선스 파일이 없습니다.
