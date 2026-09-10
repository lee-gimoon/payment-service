# payment-service

Java 21과 Spring Boot 4.1 기반의 독립 결제 서비스입니다.

## 결제 도메인 문서

코드 구현 전에 온라인 결제의 업무와 정책을 이해하기 위한 기획 초안입니다. 토스페이먼츠 공식 동작과 프로젝트에 제안하는 정책을 구분했습니다.

1. [온라인 결제 도메인 이해와 실무 기획](docs/payment-domain.md): 주문·결제·취소·환불·정산의 개념과 책임, 상태, 첫 구현 범위
2. [결제 도메인 실무 시나리오](docs/payment-scenarios.md): 실제 금액 계산, 부분 환불, 중복 요청과 장애 상황의 기대 결과
3. [결제 기획 정책 결정표](docs/payment-policy-decisions.md): 사업에 맞게 정해야 할 정책과 출시 준비 항목

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
