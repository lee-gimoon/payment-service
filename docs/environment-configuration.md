# 로컬 개발 환경 설정

이 문서는 payment-service를 로컬에서 실행할 때 사용하는 `application.yml`과 `compose.yaml`의 역할, 두 설정이 연결되는 방식, 실행 순서를 설명합니다.

## 현재 실행 구성

| 구성 요소 | 실행 위치 | 역할 |
|---|---|---|
| 정적 클라이언트 | Spring Boot 애플리케이션 내부 | `index.html`, CSS, JavaScript 제공 |
| Spring Boot 서버 | 로컬 Windows JVM | HTTP API 처리 및 정적 파일 제공 |
| PostgreSQL | Docker 컨테이너 | 주문·결제 데이터 저장 |
| pgAdmin | Docker 컨테이너 | PostgreSQL을 관리하는 웹 화면 제공 |
| 브라우저 | 로컬 컴퓨터 | 클라이언트와 pgAdmin 화면 표시 |

프로젝트 전체를 실행하려면 다음 두 명령이 필요합니다.

```powershell
# PostgreSQL과 pgAdmin 실행
docker compose up -d

# Spring Boot 실행
.\gradlew.bat bootRun
```

실행 후 프로젝트 화면은 [http://127.0.0.1:8080](http://127.0.0.1:8080), pgAdmin은 [http://127.0.0.1:5050](http://127.0.0.1:5050)에서 확인할 수 있습니다.

## 설정 파일의 차이

두 파일은 모두 YAML 형식이지만 읽는 프로그램과 설정 대상이 다릅니다.

| 파일 | 읽는 프로그램 | 설정 대상 |
|---|---|---|
| `src/main/resources/application.yml` | Spring Boot | 결제 서버 자체 |
| `compose.yaml` | Docker Compose | PostgreSQL과 pgAdmin 컨테이너 |

현재 프로젝트의 Spring 설정 파일 확장자는 `.yml`입니다. `.yml`과 `.yaml`은 같은 YAML 형식의 확장자입니다.

## application.yml

Spring Boot가 시작될 때 `src/main/resources/application.yml`을 읽습니다. 이 파일은 서버 포트, 데이터베이스 접속 정보, JPA, Flyway, 토스페이먼츠 키 등을 설정합니다.

### 애플리케이션 이름

```yaml
spring:
  application:
    name: payment-service
```

Spring 애플리케이션의 이름을 `payment-service`로 지정합니다.

### 데이터베이스 접속 정보

```yaml
spring:
  datasource:
    url: ${PAYMENT_DB_URL:jdbc:postgresql://localhost:5432/payment_service}
    username: ${PAYMENT_DB_USERNAME:payment}
    password: ${PAYMENT_DB_PASSWORD:payment_local}
```

Spring Boot가 접속할 PostgreSQL의 주소와 계정을 지정합니다.

| 항목 | 로컬 기본값 |
|---|---|
| 주소 | `localhost:5432` |
| 데이터베이스 | `payment_service` |
| 사용자 | `payment` |
| 비밀번호 | `payment_local` |

`application.yml`은 PostgreSQL을 실행하지 않습니다. 이미 실행 중인 PostgreSQL에 접속할 정보를 Spring Boot에 알려줍니다.

`${PAYMENT_DB_URL:기본값}`은 `PAYMENT_DB_URL` 환경변수가 있으면 그 값을 사용하고, 없으면 콜론 뒤의 기본값을 사용한다는 뜻입니다. 사용자 이름과 비밀번호도 같은 방식으로 동작합니다.

환경변수의 이름과 값, 설정 방법은 [환경변수 이해하기](environment-variables.md)에서 더 자세히 설명합니다.

### JPA와 Hibernate

```yaml
spring:
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
```

- `open-in-view: false`: 웹 요청 처리 계층 밖까지 영속성 컨텍스트를 열어두지 않습니다.
- `ddl-auto: validate`: Entity와 실제 테이블 구조가 일치하는지 시작할 때 검사합니다.
- `time_zone: UTC`: Hibernate가 JDBC 날짜와 시간을 UTC 기준으로 처리합니다.

`ddl-auto: validate`이므로 Hibernate가 테이블을 자동으로 생성하거나 수정하지 않습니다.

### Flyway

```yaml
spring:
  flyway:
    enabled: true
```

Spring Boot 시작 시 `src/main/resources/db/migration`에 있는 마이그레이션 SQL을 순서대로 실행합니다. 이 프로젝트에서는 Flyway가 테이블 구조의 변경 이력을 관리하고, Hibernate는 그 결과가 Entity와 일치하는지 검증합니다.

### 토스페이먼츠 키

```yaml
payment:
  toss:
    client-key: ${TOSS_CLIENT_KEY:}
    secret-key: ${TOSS_SECRET_KEY:}
```

토스페이먼츠 키를 환경변수에서 가져옵니다. 환경변수가 없으면 빈 값이 사용되며, 실제 키는 Git에 저장하지 않습니다.

### 서버 주소와 포트

```yaml
server:
  address: ${PAYMENT_BIND_ADDRESS:127.0.0.1}
  port: ${PORT:8080}
```

기본적으로 Spring Boot는 `127.0.0.1:8080`에서 실행됩니다. 브라우저가 `/`로 요청하면 Spring Boot의 정적 리소스 자동 설정이 `src/main/resources/static/index.html`을 찾아 반환합니다.

## compose.yaml

`docker compose up -d`를 실행하면 Docker Compose가 프로젝트 루트의 `compose.yaml`을 읽고 PostgreSQL과 pgAdmin 컨테이너를 실행합니다.

### PostgreSQL

```yaml
services:
  postgres:
    image: postgres:18-alpine
    environment:
      POSTGRES_DB: payment_service
      POSTGRES_USER: payment
      POSTGRES_PASSWORD: payment_local
    ports:
      - "127.0.0.1:5432:5432"
```

Docker가 `postgres:18-alpine` 이미지로 PostgreSQL을 실행하고 데이터베이스와 사용자를 초기화합니다.

포트 설정에서 왼쪽 `127.0.0.1:5432`는 로컬 컴퓨터의 주소이고, 오른쪽 `5432`는 컨테이너 안 PostgreSQL의 포트입니다. 따라서 로컬 JVM에서 실행되는 Spring Boot가 `localhost:5432`로 접속할 수 있습니다.

### PostgreSQL 상태 확인

```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U payment -d payment_service"]
  interval: 5s
  timeout: 3s
  retries: 10
```

`pg_isready`로 PostgreSQL이 연결을 받을 준비가 되었는지 확인합니다.

### pgAdmin

```yaml
pgadmin:
  image: dpage/pgadmin4:9.17
  ports:
    - "127.0.0.1:5050:80"
  depends_on:
    postgres:
      condition: service_healthy
```

pgAdmin 웹 서버는 컨테이너 내부의 80번 포트를 사용하며, 로컬 컴퓨터의 5050번 포트로 공개됩니다. `depends_on` 설정으로 PostgreSQL이 정상 상태가 된 뒤 pgAdmin을 시작합니다.

pgAdmin 컨테이너에서 PostgreSQL에 접속할 때는 `localhost`가 아니라 Compose 서비스 이름인 `postgres`를 사용합니다. 컨테이너의 `localhost`는 해당 컨테이너 자신을 의미하기 때문입니다. 이 서버 연결 정보는 `docker/pgadmin/servers.json`에 등록되어 있습니다.

### 볼륨

```yaml
volumes:
  payment-service-postgres-data:
  payment-service-pgadmin-data:
```

- `payment-service-postgres-data`: 실제 PostgreSQL 데이터를 보존합니다.
- `payment-service-pgadmin-data`: pgAdmin의 로그인과 사용자 설정을 보존합니다.

`docker compose down`으로 컨테이너를 종료해도 이 볼륨은 유지되므로 다음 실행에서 기존 데이터를 다시 사용할 수 있습니다.

## 두 파일이 연결되는 방식

```text
브라우저
  │
  ├─ http://127.0.0.1:8080
  │         │
  │         ▼
  │   로컬 Spring Boot
  │         │
  │         │ application.yml의 datasource 설정
  │         ▼
  │   localhost:5432
  │         │
  │         ▼
  │   Docker PostgreSQL
  │
  └─ http://127.0.0.1:5050
            │
            ▼
      Docker pgAdmin ── postgres:5432 ──▶ Docker PostgreSQL
```

두 파일의 데이터베이스 설정은 다음과 같이 일치해야 합니다.

| 설정 | `compose.yaml` | `application.yml` |
|---|---|---|
| 주소와 포트 | 로컬 `5432` 포트 공개 | `localhost:5432`로 접속 |
| 데이터베이스 | `payment_service` 생성 | `payment_service` 사용 |
| 사용자 | `payment` 생성 | `payment` 사용 |
| 비밀번호 | `payment_local` 지정 | `payment_local` 사용 |

`compose.yaml`은 Spring Boot가 사용할 PostgreSQL과 관리 도구를 실행합니다. `application.yml`은 Spring Boot가 실행된 인프라에 어떻게 접속하고 서버 자체를 어떻게 동작시킬지 설정합니다.

## 실행과 종료

전체 프로젝트 실행:

```powershell
docker compose up -d
.\gradlew.bat bootRun
```

Spring Boot는 `bootRun`을 실행한 터미널에서 `Ctrl+C`를 눌러 종료합니다. PostgreSQL과 pgAdmin을 종료하려면 다음 명령을 실행합니다.

```powershell
docker compose down
```

실행 상태는 다음 명령으로 확인할 수 있습니다.

```powershell
docker compose ps
```
