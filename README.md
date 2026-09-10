# payment-service

Java 21과 Spring Boot 4.1 기반의 독립 결제 서비스입니다.

## 결제 도메인 문서

[처음 배우는 결제 서비스](docs/payment-domain.md)에서 티셔츠 한 장을 구매하는 예시로 기본 흐름을 살펴봅니다. 문서는 한 파일로 관리하며, 학습과 구현이 진행될 때 필요한 내용을 조금씩 추가합니다.

## 로컬 실행

PostgreSQL을 실행합니다.

```powershell
docker compose up -d
```

애플리케이션을 실행합니다.

```powershell
.\gradlew.bat bootRun
```

로컬 기본 연결 정보는 `application.yml`과 `compose.yaml`에 맞춰져 있습니다.
Railway 같은 배포 환경에서는 다음 환경변수로 실제 DB 연결 정보를 주입합니다.

- `PAYMENT_DB_URL`
- `PAYMENT_DB_USERNAME`
- `PAYMENT_DB_PASSWORD`
