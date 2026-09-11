# 환경변수 이해하기

환경변수는 **프로그램을 실행할 때 프로그램 밖에서 전달하는 이름과 값**입니다.

```text
이름: PAYMENT_DB_URL
값:   jdbc:postgresql://localhost:5432/payment_service
```

Java 변수가 코드 안에 있다면, 환경변수는 PowerShell, IntelliJ, Docker 또는 운영 서버가 프로그램을 시작할 때 새 프로세스에 전달하는 값입니다.

## 이름은 누가 정하나요?

`PAYMENT_DB_URL`은 이 프로젝트의 개발자가 알아보기 쉽게 정한 이름입니다.

```text
PAYMENT  DB            URL
결제     데이터베이스   접속 주소
```

대문자와 밑줄은 환경변수에서 흔히 사용하는 작성 방식입니다. 이름 자체에 특별한 기능은 없지만, 값을 넣는 곳과 읽는 곳에서 정확히 같은 이름을 사용해야 합니다.

현재 `application.yml`에서는 다음과 같이 읽습니다.

```yaml
url: ${PAYMENT_DB_URL:jdbc:postgresql://localhost:5432/payment_service}
```

구조는 다음과 같습니다.

```text
${환경변수 이름:환경변수가 없을 때 사용할 기본값}
```

Spring Boot의 처리 순서:

1. `PAYMENT_DB_URL` 환경변수를 찾습니다.
2. 값이 있으면 그 값을 사용합니다.
3. 값이 없으면 `jdbc:postgresql://localhost:5432/payment_service`를 사용합니다.

현재 로컬 설정에는 기본값이 있으므로 `PAYMENT_DB_*` 환경변수를 직접 입력하지 않아도 실행됩니다. 이 경우 환경변수가 자동으로 만들어지는 것이 아니라, Spring Boot가 환경변수를 찾지 못해 `application.yml`의 기본값을 사용하는 것입니다.

## 현재 프로젝트의 환경변수

| 이름 | 용도 | 로컬 기본값 |
|---|---|---|
| `PAYMENT_DB_URL` | 접속할 PostgreSQL 주소 | `jdbc:postgresql://localhost:5432/payment_service` |
| `PAYMENT_DB_USERNAME` | PostgreSQL 사용자 | `payment` |
| `PAYMENT_DB_PASSWORD` | PostgreSQL 비밀번호 | `payment_local` |
| `TOSS_CLIENT_KEY` | 토스페이먼츠 클라이언트 키 | 빈 값 |
| `TOSS_SECRET_KEY` | 토스페이먼츠 시크릿 키 | 빈 값 |
| `PAYMENT_BIND_ADDRESS` | Spring Boot가 요청을 받을 주소 | `127.0.0.1` |
| `PORT` | Spring Boot 포트 | `8080` |

## PowerShell에서 설정하기

현재 프로젝트의 로컬 DB 설정은 `application.yml`의 기본값과 `compose.yaml`의 PostgreSQL 설정이 일치합니다. 따라서 평소에는 다음 명령만 실행하면 됩니다.

```powershell
.\gradlew.bat bootRun
```

이때 실제 처리 순서는 다음과 같습니다.

```text
1. PowerShell에서 `.\gradlew.bat bootRun` 실행
2. Gradle Wrapper가 Gradle의 `bootRun` 작업 실행
3. `bootRun`이 Spring Boot 애플리케이션용 Java 프로세스 시작
4. Spring Boot가 `application.yml`을 읽음
5. Spring Boot가 `PAYMENT_DB_URL`, `PAYMENT_DB_USERNAME`, `PAYMENT_DB_PASSWORD`를 찾음
6. 해당 환경변수가 없으므로 `application.yml`의 콜론 뒤 기본값 사용
7. 기본값으로 Docker PostgreSQL에 접속
```

즉, 다음 환경변수들이 자동으로 설정되는 것은 아닙니다.

```text
PAYMENT_DB_URL      → 없음 → application.yml 기본값 사용
PAYMENT_DB_USERNAME → 없음 → application.yml 기본값 사용
PAYMENT_DB_PASSWORD → 없음 → application.yml 기본값 사용
```

환경변수 설정은 기본값을 다른 값으로 바꿔 실행할 때만 필요합니다. 예를 들어 다른 PostgreSQL에 연결하려면 Spring Boot를 실행하기 전에 같은 PowerShell에서 설정합니다.

```powershell
$env:PAYMENT_DB_URL = 'jdbc:postgresql://다른-DB-주소:5432/payment_service'
$env:PAYMENT_DB_USERNAME = '다른_DB_사용자'
$env:PAYMENT_DB_PASSWORD = '다른_DB_비밀번호'

.\gradlew.bat bootRun
```

이 경우의 처리 순서는 다음과 같습니다.

```text
1. PowerShell에 환경변수 설정
2. PowerShell에서 `.\gradlew.bat bootRun` 실행
3. Gradle Wrapper가 Gradle의 `bootRun` 작업 실행
4. `bootRun`이 현재 환경을 전달하여 Spring Boot용 Java 프로세스 시작
5. Spring Boot가 `application.yml`을 읽고 환경변수를 찾음
6. 환경변수가 있으므로 콜론 뒤 기본값 대신 환경변수 값 사용
7. 환경변수로 지정한 PostgreSQL에 접속
```

환경변수 설정과 실행 명령을 한 줄에 적어도 실행 순서는 같습니다.

```powershell
$env:PAYMENT_DB_URL = 'jdbc:postgresql://다른-DB-주소:5432/payment_service'; .\gradlew.bat bootRun
```

Spring Boot가 이미 실행된 다음 PowerShell의 환경변수를 변경해도 실행 중인 Spring Boot 설정은 바뀌지 않습니다. 변경한 값을 적용하려면 Spring Boot를 다시 시작해야 합니다.

값 확인:

```powershell
$env:PAYMENT_DB_URL
```

값 삭제:

```powershell
Remove-Item Env:PAYMENT_DB_URL
```

이 방식으로 설정한 값은 현재 PowerShell 창에만 적용되며 창을 닫으면 사라집니다. IntelliJ에서는 `Run → Edit Configurations → Environment variables`에서 같은 값을 지정할 수 있습니다.

## 사용하는 이유

환경변수를 사용하면 코드를 수정하지 않고 실행 환경마다 설정을 바꿀 수 있습니다.

```text
같은 Spring Boot 프로그램
  ├─ 로컬 환경변수  → 로컬 데이터베이스
  ├─ 테스트 환경변수 → 테스트 데이터베이스
  └─ 운영 환경변수  → 운영 데이터베이스
```

데이터베이스 비밀번호와 외부 API 키처럼 Git에 저장하면 안 되는 값도 환경변수로 전달할 수 있습니다.

## Docker의 environment와 차이

### 언제 읽나요?

다음 명령을 실행할 때 Docker Compose가 `compose.yaml`을 읽습니다.

```powershell
docker compose up -d
```

그리고 `environment`에 적힌 값을 해당 컨테이너에 전달한 뒤 PostgreSQL과 pgAdmin을 실행합니다.

### PowerShell에 직접 입력해야 하나요?

값이 파일에 직접 적혀 있으면 입력하지 않아도 됩니다.

```yaml
environment:
  POSTGRES_DB: payment_service
  POSTGRES_USER: payment
  POSTGRES_PASSWORD: payment_local
```

`docker compose up -d`를 실행하면 Docker Compose가 위 값을 PostgreSQL 컨테이너에 전달합니다. PostgreSQL은 처음 데이터 공간을 만들 때 이 값으로 데이터베이스와 사용자를 생성합니다.

다음처럼 `${이름:-기본값}`으로 작성된 경우에는 PowerShell 값을 먼저 확인합니다.

```yaml
PGADMIN_DEFAULT_EMAIL: ${PGADMIN_DEFAULT_EMAIL:-admin@payment-service.com}
```

- PowerShell에 `PGADMIN_DEFAULT_EMAIL`이 있으면 그 값을 사용합니다.
- 없으면 `admin@payment-service.com`을 사용합니다.

따라서 기본값을 그대로 사용할 때는 PowerShell에 아무것도 입력하지 않습니다. 값을 바꾸고 싶을 때만 다음처럼 입력합니다.

```powershell
$env:PGADMIN_DEFAULT_EMAIL = 'my-admin@example.com'
docker compose up -d
```

### Spring Boot 설정과의 차이

| 환경변수 | 읽는 프로그램 | 역할 |
|---|---|---|
| `POSTGRES_*` | PostgreSQL 컨테이너 | DB와 사용자 초기화 |
| `PGADMIN_*` | pgAdmin 컨테이너 | pgAdmin 계정 설정 |
| `PAYMENT_DB_*` | Spring Boot | 접속할 DB 정보 설정 |

이 값들은 자동으로 서로 연결되지 않습니다. 현재는 `compose.yaml`과 `application.yml`에 같은 DB 이름, 사용자, 비밀번호를 개발자가 맞춰 작성해 놓았습니다.

```powershell
# compose.yaml을 읽어 PostgreSQL과 pgAdmin 실행
docker compose up -d

# application.yml을 읽어 Spring Boot 실행
.\gradlew.bat bootRun
```