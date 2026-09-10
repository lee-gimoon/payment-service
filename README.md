# payment-service

Java 21과 Spring Boot 4.1 기반의 독립 결제 서비스입니다.

## 개발 계획과 결제 도메인 문서

[결제 서비스 개발 계획과 기본 개념](docs/payment-domain.md)은 SDLC와 대표적인 개발 방식, 애자일의 의미부터 설명합니다. 이어서 결제 도메인을 포함해 기획 → 요구사항 분석 → 반복 계획 → 설계 → 구현 → 테스트 → 배포·운영 → 피드백 순서로 프로젝트 진행 방법을 정리합니다. 문서는 한 파일로 관리하며, 각 반복에서 확인한 내용을 갱신합니다.

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
