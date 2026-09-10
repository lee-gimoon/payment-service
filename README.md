# payment-service

Java 21과 Spring Boot 4.1 기반의 독립 결제 서비스입니다.

## 개발 계획과 결제 도메인 문서

[결제 서비스 개발 계획과 기본 개념](docs/payment-domain.md)은 SDLC와 애자일의 의미를 설명하고, 주문부터 결제 결과 조회까지 연결되는 초기 MVP의 목표·범위·도메인·설계·완료 기준을 정리합니다. MVP를 사용해본 피드백에 따라 개선할 내용을 정하며, 문서는 한 파일로 관리하고 진행에 맞춰 보충합니다.

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
