# 프로젝트 문서

프로젝트 소개와 실행 방법은 [루트 README](../README.md)에 있습니다. 이 서비스는 토스 테스트 키로 실행하는 로컬 개발용이며, 운영 배포와 결제 취소·환불은 제공하지 않습니다.

| 문서 | 읽을 때 |
| --- | --- |
| [아키텍처](architecture.md) | 결제 규칙, 상태, 재고, 복구 정책, 설계 결정을 볼 때 ([도메인 그림](payment-domain-target.png) 포함) |
| [중복 결제 방지](duplicate-payment-prevention.md) | 같은 주문의 중복 승인을 막는 단계와 각 단계의 역할을 볼 때 |
| [API](api.md) | 클라이언트를 연동하거나 요청·응답 계약을 바꿀 때 |
| [1:1 상담](chat.md) | 고객·관리자 상담의 테이블, 메시지 저장 순서, 중복·읽음 규칙을 볼 때 |
| [WebSocket 주소와 STOMP 프레임](websocket-basics.md) | `/ws` 연결, 구독 채널, 프레임이 무엇이고 어떤 순서로 오가는지 그림으로 볼 때 |
| [Spring이 WebSocket 설정을 읽는 방식](websocket-configuration.md) | `@EnableWebSocketMessageBroker`와 `WebSocketMessageBrokerConfigurer`로 상담 알림 브로커가 만들어지는 순서를 Spring 빈(Bean) 개념과 함께 익힐 때 |
| [로그인과 회원](authentication.md) | 로그인 기술(OAuth 2.0·OIDC·JWT)을 처음 익히거나, 로그인 흐름, 테스트 계정, realm 설정을 볼 때 |
| [설정](configuration.md) | 토스 키, DB 연결, Keycloak, 복구 작업, 개발 서버 주소를 설정할 때 |
| [개발 가이드](development.md) | 테스트·빌드·마이그레이션을 하고 변경을 제출할 때 |
| [UI 디자인 기준](../DESIGN.md) | 화면 스타일과 결제 문구를 바꿀 때 |

- [상품·주문·구매 항목 예시](shop-order-tables.png): 초기 상품 5종으로 주문과 구매 항목의 관계를 보여 줍니다. 주문 데이터는 설명용 예시입니다.
- API 필드 스키마는 실행 중인 서버의 [Swagger UI](http://127.0.0.1:8080/swagger-ui.html)에서 확인합니다.
- 구현을 바꾸면 관련 문서도 같은 변경에서 갱신합니다.
