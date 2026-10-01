# 개발 가이드

실행 방법은 [README](../README.md), 설정값은 [설정 문서](configuration.md)에 있습니다.

## 코드 위치

| 경로 | 역할 |
| --- | --- |
| `src/main/java/.../config/` | 로그인 토큰 검증(Spring Security), Swagger 설정 |
| `src/main/java/.../product/` | 상품 카탈로그 |
| `src/main/java/.../order/` | 주문 생성, 구매 항목, 조회 |
| `src/main/java/.../chat/api/` | 상담 HTTP 요청·응답 (고객 `/chat`, 관리자 `/admin/chat`) |
| `src/main/java/.../chat/application/` | 상담방 만들기, 메시지 저장 순서·트랜잭션 경계, 읽음 표시, 새 메시지 이벤트 |
| `src/main/java/.../chat/domain/` | 상담방, 메시지, 보낸 쪽 |
| `src/main/java/.../chat/persistence/` | 상담방·메시지 조회와 잠금 |
| `src/main/java/.../chat/infrastructure/websocket/` | WebSocket(STOMP) 설정, 연결 인증·구독 권한, 커밋 후 새 메시지 알림 |
| `src/main/java/.../payment/api/` | 결제 HTTP 요청·응답 |
| `src/main/java/.../payment/application/` | 승인 순서, 트랜잭션 경계, 미확정 승인 복구 |
| `src/main/java/.../payment/domain/` | 결제 시도, 승인 상태, 결제 기록 |
| `src/main/java/.../payment/infrastructure/toss/` | 토스 설정과 HTTP 연동 |
| `src/main/resources/db/migration/` | Flyway 마이그레이션 |
| `docker/keycloak/` | Keycloak realm·client·테스트 회원 설정 |
| `docker/keycloak-themes/` | 스토어와 같은 디자인의 Keycloak 로그인·회원가입 화면 테마 |
| `frontend/src/auth/` | Keycloak 로그인, 토큰 보관·갱신, 로그인 상태 |
| `frontend/src/pages/` | 스토어, 상품, 장바구니, 결제 결과, 주문, 관리자 상담 관리 화면 |
| `frontend/src/payments/` | 결제창, 결제수단 제한, 인증 복귀 처리 |
| `frontend/src/chat/` | 상담 WebSocket 연결, 한 대화의 메시지·보내는 중·안 읽은 수 관리 |
| `frontend/src/components/ChatWidget.tsx`·`ChatThread.tsx` | 고객 문의 창, 고객·관리자가 함께 쓰는 대화 목록과 입력란 |
| `frontend/tests/` | SDK 대역을 쓰는 결제창·인증 복귀 테스트, 상담 메시지 합치기·재연결 복구·비동기 읽음 처리 테스트 |

`...`는 `com/example/payment`입니다. 도메인 규칙은 [아키텍처](architecture.md)에 있습니다.

## 백엔드 검증

Java 21과 실행 중인 Docker가 필요합니다. 개발 DB, Keycloak, 토스 키는 필요 없습니다. 통합 테스트는 로그인한 회원의 토큰을 테스트 도구로 만들어 요청합니다.

```powershell
.\gradlew.bat test bootJar
```

macOS / Linux에서는 `./gradlew test bootJar`를 사용합니다.

| 테스트 | 검증 범위 |
| --- | --- |
| [PaymentIntegrationTest](../src/test/java/com/example/payment/PaymentIntegrationTest.java) | 로그인 요구와 주문 소유자, 주문 금액, 트랜잭션 원자성, 승인 슬롯과 DB 제약, 동시 요청, 미확정 복구, 결제 기록, 재시도 |
| [ChatIntegrationTest](../src/test/java/com/example/payment/chat/ChatIntegrationTest.java) | 고객당 상담방 하나, 자기 대화만 조회, 재전송 중복 방지, 동시 전송, 관리자 역할, 읽음 수, 이전·이후 대화 불러오기, 최근 100개 밖의 미답변 상담 조회 |
| [ChatWebSocketTest](../src/test/java/com/example/payment/chat/ChatWebSocketTest.java) | 실제 포트로 STOMP 연결: 토큰 없는 연결 거부, 관리자 주소·다른 회원 주소 구독 거부, `SEND` 거부, 커밋 후 본인·관리자에게만 알림 |
| [TossPaymentClientTest](../src/test/java/com/example/payment/payment/infrastructure/toss/TossPaymentClientTest.java) | 토스 요청의 인증·멱등키·금액, 응답 분류(성공·거절·오류·불일치) |
| [MigrationUpgradeTest](../src/test/java/com/example/payment/MigrationUpgradeTest.java) | V1 데이터가 최신 스키마로 올바르게 옮겨지는지 |

- 통합 테스트는 Testcontainers의 PostgreSQL에 모든 마이그레이션을 적용하고, 토스 API는 대역을 씁니다.
- 복구 작업의 주기 실행은 끄고(`payment.recovery.enabled=false`) 테스트에서 직접 호출합니다.
- 결과 보고서는 `build/reports/tests/test/index.html`, 실행 파일은 `build/libs/`에 생깁니다.

## 프런트엔드 검증

Node.js 22.18 이상을 권장합니다. `frontend/`에서 실행합니다.

```sh
npm ci
npm test
npm run build
```

- `npm test`는 SDK 대역으로 결제창 중복 실행·닫기, 지원 결제수단, 인증 복귀 URL, 임시 승인 정보 처리를 확인하고, 상담 메시지 합치기·중복 제거·입력 검증, 재연결 중 전송·조회 실패·겹친 조회, 늦게 완료된 읽음 요청·조회 이후의 새 답변 배지, 최근 100개 밖의 미답변 상담 목록·관리자 배지, 서버에 연결하지 못했을 때의 오류 안내를 확인합니다. 상담 테스트는 실제 훅·컴포넌트 소스에 제어 가능한 상태·효과 실행기를 연결해 응답 순서를 재현합니다. 실제 브라우저 E2E 테스트는 없습니다.
- `npm run build`는 타입 검사 후 `frontend/dist/`를 만듭니다. 백엔드 JAR에는 포함되지 않습니다.
- 화면을 바꾸면 [DESIGN.md](../DESIGN.md) 기준으로 PC·모바일, 키보드 포커스, 처리 중·오류 상태를 확인합니다.

## 개발 서버의 경로

Vite 프록시 경로 `/products`, `/orders`, `/admin`이 화면 경로와 겹칩니다. 브라우저가 페이지를 요청할 때(`Accept: text/html`)는 [vite.config.ts](../frontend/vite.config.ts)가 API 대신 React 화면을 돌려주므로, `/orders/:orderId`나 `/admin/chat` 같은 주소를 새로고침하거나 로그인 후 돌아와도 화면이 열립니다. 상담 실시간 알림 `/ws`는 WebSocket 프록시(`ws: true`)로 백엔드에 연결합니다.

## 실제 상담 확인

자동 테스트는 토큰을 테스트 도구로 만들므로, Keycloak 로그인과 화면 연동은 직접 확인합니다. 한 브라우저의 탭들은 로그인을 공유하므로, 고객과 관리자를 동시에 보려면 다른 브라우저나 시크릿 창을 씁니다.

1. 한쪽에서 [테스트 회원](authentication.md#로컬-계정)으로 로그인하고 오른쪽 아래 **문의하기**로 메시지를 보냅니다. 쇼핑몰 관리자 계정에는 문의하기 대신 **상담 관리** 버튼이 나오므로, 고객 쪽은 꼭 일반 회원으로 확인합니다.
2. 다른 쪽에서 쇼핑몰 관리자로 로그인해 헤더의 **상담 관리**를 열면, 새로고침 없이 상담이 **답변 대기**로 나타납니다.
3. 관리자가 답하면 고객 창에 바로 보이고, 창을 닫아 두었으면 **새 답변 1**이 뜹니다.
4. 백엔드를 껐다 켜면 두 화면이 **다시 연결하고 있습니다**를 거쳐 자동으로 다시 연결됩니다. 꺼진 동안 보낸 메시지는 **다시 보내기**로 보냅니다.
5. 테스트 회원으로 `/admin/chat`을 열면 관리자 전용 안내가 나오는지 확인합니다.

## 실제 결제 확인

자동 테스트는 토스 대역을 쓰므로, 실제 SDK 연동은 테스트 키로 직접 확인합니다. Keycloak을 실행하고 [테스트 회원](authentication.md#로컬-계정)으로 로그인한 뒤 진행합니다.

1. 주문을 만들고 화면 금액과 DB 주문 금액을 비교합니다.
2. 결제창을 닫은 뒤 같은 주문으로 다시 결제할 수 있는지 확인합니다.
3. 카드·간편결제로 결제한 뒤 서버 결과, DB, 토스 상점 결제내역을 비교합니다.
4. 같은 결제 정보를 다시 보내도 새 승인이 생기지 않는지 확인합니다.
5. 로그아웃하거나 다른 회원으로 로그인하면 그 주문을 조회할 수 없는지 확인합니다.

결제 키나 시크릿 키는 이슈·문서·로그에 남기지 않습니다.

## 데이터베이스와 마이그레이션

| 파일 | 내용 |
| --- | --- |
| [V1](../src/main/resources/db/migration/V1__initial_schema.sql) | 초기 테이블과 상품 10종 |
| [V2](../src/main/resources/db/migration/V2__merge_payments_into_attempts.sql) | 승인 상태를 결제 시도로 합치고 주문 승인 슬롯·DB 제약 추가 |
| [V3](../src/main/resources/db/migration/V3__add_completed_payments.sql) | 성공한 결제만 담는 `payments` 테이블 추가 |
| [V4](../src/main/resources/db/migration/V4__align_attempt_finished_at.sql) | 종료 상태에만 완료 시각을 두도록 데이터 정리·제약 추가 |
| [V5](../src/main/resources/db/migration/V5__add_order_customer.sql) | 주문한 회원(`customer_id`) 컬럼 추가. 기존 주문은 비어 있음 |
| [V6](../src/main/resources/db/migration/V6__add_chat.sql) | 1:1 상담 테이블 `chat_rooms`·`chat_messages` 추가 |

- 서버를 시작하면 Flyway가 아직 적용하지 않은 버전만 차례로 적용합니다. Hibernate는 스키마를 검증만 합니다.
- 이미 적용한 파일은 수정하지 않고 다음 버전을 추가합니다. 추가할 때는 `PaymentIntegrationTest`의 버전 목록과 `MigrationUpgradeTest`도 갱신합니다.

## 변경할 때

- 변경한 동작에 맞춰 테스트와 문서를 같은 커밋에서 갱신합니다.
- API 계약을 바꾸면 요청 검증, 응답 타입, 프런트엔드 호출, [API 문서](api.md)를 함께 맞춥니다.
- 화면의 시각 기준을 바꾸면 `DESIGN.md`를 갱신합니다.
