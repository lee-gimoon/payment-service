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

현재 로컬 설정에는 기본값이 있으므로 환경변수를 직접 입력하지 않아도 실행됩니다.

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

환경변수는 Spring Boot를 실행하기 전에 같은 PowerShell에서 설정합니다.

```powershell
$env:PAYMENT_DB_URL = 'jdbc:postgresql://localhost:5432/payment_service'
$env:PAYMENT_DB_USERNAME = 'payment'
$env:PAYMENT_DB_PASSWORD = 'payment_local'

.\gradlew.bat bootRun
```

`bootRun`으로 시작된 Spring Boot는 PowerShell에서 환경변수를 전달받습니다.

```text
1. PowerShell에 환경변수 설정
2. bootRun 실행
3. Gradle 프로세스가 환경변수를 전달받음
4. Gradle이 시작한 Spring Boot도 환경변수를 전달받음
```

두 명령을 한 줄에 적어도 실행 순서는 같습니다.

```powershell
$env:PAYMENT_DB_URL = 'jdbc:postgresql://localhost:5432/payment_service'; .\gradlew.bat bootRun
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

`compose.yaml`에도 환경변수가 있습니다.

```yaml
environment:
  POSTGRES_DB: payment_service
  POSTGRES_USER: payment
  POSTGRES_PASSWORD: payment_local
```

이 값은 PostgreSQL 컨테이너에 전달되어 데이터베이스와 사용자를 초기화합니다.

```text
PowerShell 또는 IntelliJ의 PAYMENT_DB_* → Spring Boot가 읽음
compose.yaml의 POSTGRES_*              → PostgreSQL 컨테이너가 읽음
```

둘 다 이름과 값의 형태지만 값을 읽는 프로그램과 목적이 다릅니다.
