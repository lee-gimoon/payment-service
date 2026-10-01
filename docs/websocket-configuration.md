# Spring이 WebSocket 설정을 읽는 방식

[ChatWebSocketConfiguration](../src/main/java/com/example/payment/chat/infrastructure/websocket/ChatWebSocketConfiguration.java)에는 `registerStompEndpoints()` 같은 메서드가 세 개 있지만, 프로젝트 어디에도 이 메서드를 부르는 코드가 없습니다. 이 메서드는 Spring이 부릅니다. 이 문서는 Spring이 이 클래스를 찾아 읽고 상담 알림용 WebSocket 메시지 브로커를 만드는 순서를 설명합니다. 상담 알림의 주소와 STOMP 프레임은 [1:1 상담](chat.md#실시간-전달)에 있습니다.

한 문장으로 줄이면 **`@EnableWebSocketMessageBroker`가 Spring의 엔진 제작자를 빈(Bean) 명단에 추가하고, 그 제작자가 `WebSocketMessageBrokerConfigurer`를 구현한 빈(Bean)을 타입으로 찾아 답을 읽은 뒤, 그 답대로 WebSocket 메시지 브로커를 만듭니다.**

Spring 내부 클래스와 메서드 이름은 이 프로젝트가 쓰는 Spring Framework 7.0.9(Spring Boot 4.1.1) 기준입니다. 버전이 바뀌면 이름이 달라질 수 있습니다.

## 먼저 알아 둘 용어

| 용어 | 뜻 |
| --- | --- |
| 빈(Bean) | Spring 컨테이너가 만들고 관리하는 객체 |
| Spring 컨테이너 | 앱이 시작될 때 빈(Bean) 명단을 만들고, 명단대로 객체를 만들고, 필요한 곳에 넣어 주는 Spring의 관리자 |
| 빈(Bean) 명단 | 컨테이너가 만들 객체의 목록. 명단에 오르는 것과 객체로 만들어지는 것은 다른 단계 |
| `@Configuration` | 빈(Bean)을 만드는 방법을 담은 설정 클래스라는 표시 |
| `@Bean` | 설정 클래스 안에서 컨테이너가 호출해 빈(Bean)을 만드는 메서드라는 표시 |
| `@Autowired` | "이 타입의 빈(Bean)을 넣어 달라"는 요청. 생성자가 하나뿐이면 생략한다([ChatNotifier](../src/main/java/com/example/payment/chat/infrastructure/websocket/ChatNotifier.java)) |
| 인터페이스 | 메서드 이름과 모양만 정해 둔 양식 |
| 구현체 | 인터페이스를 `implements`하고 메서드 내용을 채운 클래스 |

## 등장인물

카페 프랜차이즈에 비유하면, 본사(Spring)는 장비와 설치 기사를 갖추고 신청서 양식을 나눠 줍니다. 점주(우리)는 그 양식에 우리 매장에 맞는 답을 적어 냅니다.

| 비유 | 실제 이름 | 누가 만들었나 |
| --- | --- | --- |
| 신청서 양식 (인터페이스) | `WebSocketMessageBrokerConfigurer` | Spring |
| 작성한 신청서 (구현체) | `ChatWebSocketConfiguration` | 우리 |
| 명단 추가 표시 | `@EnableWebSocketMessageBroker` | Spring (우리가 붙임) |
| 엔진 제작자 (설정 클래스) | `DelegatingWebSocketMessageBrokerConfiguration` | Spring |
| 엔진 부품 | `/ws` 연결, 브로커, 채널, `SimpMessagingTemplate` | 제작자가 만듦 |
| 부품 사용자 | `ChatNotifier` | 우리 |

여기서 엔진은 WebSocket 메시지 브로커입니다. 브라우저의 STOMP 연결을 받고, `/topic/...`, `/user/queue/...` 같은 주소별 구독자에게 메시지를 배달합니다.

## 1단계: 빈(Bean) 명단 작성

이 단계에서는 아직 객체가 없습니다.

```text
Spring ──우리 패키지 훑기──▶ 명단: ChatWebSocketConfiguration, ChatNotifier, ...
Spring ──@EnableWebSocketMessageBroker 발견──▶ 명단에 추가: DelegatingWebSocketMessageBrokerConfiguration
```

1. `@SpringBootApplication`이 `com.example.payment` 패키지 아래를 훑습니다.
2. `@Configuration`, `@Component` 같은 표시가 붙은 우리 클래스가 명단에 오릅니다.
3. 엔진 제작자는 spring-websocket jar 안(`org.springframework...` 패키지)에 있어서 훑는 범위 밖입니다.
4. `@EnableWebSocketMessageBroker`가 엔진 제작자를 명단에 추가합니다.

이 어노테이션 안에 적힌 핵심은 `@Import(DelegatingWebSocketMessageBrokerConfiguration.class)` 한 줄입니다. 어노테이션이 자기가 붙은 클래스를 해석하는 것이 아니므로, [PaymentServiceApplication](../src/main/java/com/example/payment/PaymentServiceApplication.java) 같은 다른 설정 클래스로 옮겨도 똑같이 동작합니다. `ChatWebSocketConfiguration`에 붙인 것은 WebSocket 설정을 한곳에 모으려는 관례입니다.

## 2단계: 제작자를 만들고 신청서 넣어 주기

```text
컨테이너 ──생성──▶ 제작자 객체
                    │ @Autowired setConfigurers(List<WebSocketMessageBrokerConfigurer>)
                    │   = "이 타입의 빈(Bean)을 전부 주세요"
                    │
컨테이너 ──타입으로 검색──▶ ChatWebSocketConfiguration 발견 (아직 객체가 없으면 이때 만듦)
컨테이너 ──넣어 줌──▶ 제작자.configurers = [ChatWebSocketConfiguration, Spring Boot의 configurer]
```

1. 컨테이너가 명단대로 제작자를 빈(Bean)으로 만듭니다.
2. 제작자 코드에는 `WebSocketMessageBrokerConfigurer` 타입의 빈(Bean)을 달라는 요청(`@Autowired`)이 적혀 있습니다.
3. 컨테이너가 그 타입의 빈(Bean)을 전부 찾습니다.
4. 컨테이너가 찾은 빈(Bean)을 제작자의 `configurers` 목록에 넣어 줍니다.

컨테이너는 이름이 아니라 **타입**으로 찾습니다. `implements WebSocketMessageBrokerConfigurer`가 없으면 메서드 이름이 같아도 찾지 못합니다.

목록에는 우리 클래스만 있지 않습니다. Spring Boot의 `WebSocketMessagingAutoConfiguration`은 엔진 제작자가 명단에 있을 때만 켜지고, 자기 configurer 빈(Bean)을 추가합니다. Boot의 configurer는 메시지를 JSON으로 바꾸는 변환기와 채널 작업 실행기 같은 기본 설정을 채웁니다. 제작자는 목록에 있는 신청서를 모두 차례로 읽습니다.

## 3단계: 엔진 부품 만들기

`/ws` 부품 하나를 예로 들면 다음과 같습니다.

```text
컨테이너 ──호출──▶ 제작자.stompWebSocketHandlerMapping()          (@Bean 메서드)
                    │ registry = 빈 종이
                    │
                    ├──호출──▶ ChatWebSocketConfiguration.registerStompEndpoints(registry)
                    │            registry.addEndpoint("/ws")      ← 종이에 /ws를 적음
                    │◀──끝────┘
                    │
                    │ 종이에 적힌 /ws를 읽고 부품 완성
컨테이너 ◀──반환──┘ → 빈(Bean)으로 등록
```

1. 컨테이너가 제작자의 `@Bean` 메서드 `stompWebSocketHandlerMapping()`을 호출합니다.
2. 이 메서드가 빈 종이 역할을 하는 `registry` 객체를 만듭니다.
3. 이 메서드가 종이를 건네며 `ChatWebSocketConfiguration.registerStompEndpoints(registry)`를 호출합니다.
4. 우리 메서드가 `registry.addEndpoint("/ws")`로 종이에 `/ws`를 적습니다.
5. 우리 메서드가 끝나면 실행이 다시 `@Bean` 메서드로 돌아옵니다.
6. `@Bean` 메서드가 종이에 적힌 `/ws`를 읽고 접속 주소 연결 부품을 완성해 반환합니다.
7. 컨테이너가 반환된 부품을 빈(Bean)으로 등록합니다.

제작자가 우리가 적은 내용을 읽을 수 있는 건 우리 메서드에 **같은 `registry` 객체**를 건넸기 때문입니다. 엄밀하게는 `@Bean` 메서드가 제작자 자신의 `registerStompEndpoints()`를 먼저 부르고, 그 메서드가 `configurers` 목록을 `for`문으로 돌며 우리 메서드를 부릅니다([Spring 코드로 확인하기](#spring-코드로-확인하기)).

나머지 부품도 같은 방식으로 만들어집니다.

| 컨테이너가 호출하는 `@Bean` 메서드 | 그 안에서 호출되는 우리 메서드 | 우리가 적는 답 | 완성되는 부품 |
| --- | --- | --- | --- |
| `stompWebSocketHandlerMapping()` | `registerStompEndpoints()` | `/ws` | 접속 주소 연결 |
| `simpleBrokerMessageHandler()` | `configureMessageBroker()` | `/topic`·`/queue` 주소, heartbeat 10초, 회원별 주소 접두사 `/user` | 메모리 브로커 |
| `clientInboundChannel()` | `configureClientInboundChannel()` | [ChatSocketAuthorization](../src/main/java/com/example/payment/chat/infrastructure/websocket/ChatSocketAuthorization.java) | 브라우저에서 들어오는 메시지 채널 (인증 검사 포함) |

브로커 설정과 들어오는 채널 설정은 여러 부품이 함께 씁니다. 그래서 제작자는 그 설정이 처음 필요한 부품을 만들 때 한 번만 묻고, 받은 답을 보관해 다른 부품에도 씁니다.

## 4단계: 완성된 부품 사용

```text
컨테이너 ──생성──▶ ChatNotifier(SimpMessagingTemplate)     ← 3단계에서 만든 부품을 넣어 줌

채팅 메시지 저장 & 커밋
   └─▶ ChatNotifier.onSaved()
         ├─ convertAndSendToUser(고객 ID, "/queue/chat", ...)  → 고객이 /user/queue/chat으로 받음
         └─ convertAndSend("/topic/admin/chat", ...)          → 관리자가 받음
```

`ChatNotifier`는 `SimpMessagingTemplate`을 `new`로 만들지 않습니다. 생성자 매개변수에 적어 두면 컨테이너가 3단계에서 만든 부품을 넣어 줍니다.

## Spring 코드로 확인하기

Spring Framework 7.0.9 소스에서 필요한 부분만 남겼습니다.

### 신청서 양식

```java
public interface WebSocketMessageBrokerConfigurer {
    default void registerStompEndpoints(StompEndpointRegistry registry) { }
    default void configureClientInboundChannel(ChannelRegistration registration) { }
    default void configureMessageBroker(MessageBrokerRegistry registry) { }
    // ... 나머지도 모두 비어 있는 default 메서드
}
```

메서드가 모두 비어 있는 `default` 메서드라서 "아무것도 하지 않는다"는 기본 답이 정해져 있습니다. 구현체는 필요한 질문만 `@Override`해서 답하면 됩니다.

### 엔진 제작자는 클래스 세 개

```text
AbstractMessageBrokerConfiguration                (spring-messaging) 브로커, 채널, SimpMessagingTemplate을 만드는 @Bean 메서드
  └─ WebSocketMessageBrokerConfigurationSupport   (spring-websocket) /ws 같은 접속 주소 연결을 만드는 @Bean 메서드
       └─ DelegatingWebSocketMessageBrokerConfiguration          configurer 목록을 받아 차례로 묻는 역할
```

`@EnableWebSocketMessageBroker`가 명단에 넣는 건 맨 아래 클래스지만, 위 두 클래스를 상속하므로 부품을 만드는 `@Bean` 메서드도 함께 딸려 옵니다. 부모 클래스는 부품을 만들다가 "여기서 묻는다"는 자리(빈 메서드)만 만들어 둡니다. `Delegating...`은 그 자리를 "configurer 목록에 차례로 묻기"로 채웁니다. `Delegating`은 "맡긴다"는 뜻으로, 설정 질문을 configurer에게 맡긴다는 의미입니다.

### ① 받기

```java
// DelegatingWebSocketMessageBrokerConfiguration
@Autowired(required = false)
public void setConfigurers(List<WebSocketMessageBrokerConfigurer> configurers) {
    if (!CollectionUtils.isEmpty(configurers)) {
        this.configurers.addAll(configurers);
    }
}
```

`@Autowired`를 보고 컨테이너가 이 타입의 빈(Bean)을 전부 찾아 넣어 줍니다. 제작자가 직접 찾으러 다니지 않습니다.

### ② 부품 만들기

```java
// WebSocketMessageBrokerConfigurationSupport
@Bean
public HandlerMapping stompWebSocketHandlerMapping(...) {
    WebMvcStompEndpointRegistry registry = new WebMvcStompEndpointRegistry(...);
    registerStompEndpoints(registry);   // 접속 주소를 묻는 자리
    ...
}
```

### ③ 답 읽기

```java
// DelegatingWebSocketMessageBrokerConfiguration
@Override
protected void registerStompEndpoints(StompEndpointRegistry registry) {
    for (WebSocketMessageBrokerConfigurer configurer : this.configurers) {
        configurer.registerStompEndpoints(registry);
    }
}
```

`configureMessageBroker()`와 `configureClientInboundChannel()`도 같은 구조입니다.

## 하나씩 빼 보면

| 바꾼 것 | 결과 |
| --- | --- |
| `@EnableWebSocketMessageBroker`만 지움 | 엔진 제작자가 명단에 오르지 않으므로 우리 메서드를 부를 쪽이 없다. `SimpMessagingTemplate`도 만들어지지 않아 `ChatNotifier`에 넣을 빈(Bean)이 없고, 앱이 시작되지 않는다 |
| `implements WebSocketMessageBrokerConfigurer`만 지움 (`@Override`도 같이 지워야 컴파일됨) | 제작자는 만들어지지만 우리 클래스가 타입 검색에 걸리지 않는다. `/ws`, 인증 검사, heartbeat가 빠진 기본 엔진이 만들어져 브라우저가 접속할 주소가 없다 |

둘 다 필요합니다. 어노테이션은 제작자를 명단에 올리고, `implements`는 우리 신청서가 제작자에게 전달되게 합니다.

## heartbeat 스케줄러를 늦게 받는 이유

`ChatWebSocketConfiguration`은 heartbeat에 쓰는 `messageBrokerTaskScheduler`를 `@Lazy`로 받습니다. 이 스케줄러는 엔진 제작자의 `@Bean` 메서드가 만드는 부품입니다.

1. 제작자는 빈(Bean)이 되는 과정에서 `ChatWebSocketConfiguration`을 넣어 받아야 합니다(2단계).
2. `ChatWebSocketConfiguration`이 만들어질 때 스케줄러를 바로 달라고 하면, 스케줄러를 만들 제작자는 아직 우리 클래스를 기다리는 중입니다.
3. 서로 상대가 먼저 완성되기를 기다리는 순환 참조가 되어 앱이 시작되지 않습니다.

`@Lazy`는 진짜 스케줄러 대신 대리 객체를 먼저 넣어 두고, 처음 쓰일 때 진짜 스케줄러를 찾게 합니다. 그래서 순환이 끊깁니다.

## 같은 구조를 쓰는 다른 Spring 기능

| 엔진 제작자 | 무슨 엔진 | 명단에 들어가는 방법 | 걷는 양식 |
| --- | --- | --- | --- |
| `DelegatingWebSocketMessageBrokerConfiguration` | WebSocket 메시지 브로커 (상담 알림) | `@EnableWebSocketMessageBroker` | `WebSocketMessageBrokerConfigurer` |
| `DelegatingWebMvcConfiguration` | Spring MVC (HTTP API) | `spring-boot-starter-webmvc`가 있어서 Spring Boot가 이 클래스를 상속한 제작자를 자동으로 추가 | `WebMvcConfigurer` |

각 제작자는 자기 양식만 걷습니다. MVC 제작자도 명단에 있지만 `ChatWebSocketConfiguration`은 `WebMvcConfigurer`가 아니므로 읽지 않습니다. `Delegating○○Configuration`이라는 이름은 "`○○Configurer` 양식을 걷어서 읽는 제작자"로 읽으면 됩니다.

## 정리

1. `@EnableWebSocketMessageBroker`는 jar 안에 있던 엔진 제작자 `DelegatingWebSocketMessageBrokerConfiguration`을 빈(Bean) 명단에 추가합니다.
2. 컨테이너는 명단대로 제작자를 빈(Bean)으로 만듭니다.
3. 컨테이너는 `WebSocketMessageBrokerConfigurer`를 구현한 빈(Bean)을 타입으로 찾습니다.
4. 컨테이너는 찾은 `ChatWebSocketConfiguration`을 제작자에게 넣어 줍니다.
5. 컨테이너는 제작자의 `@Bean` 메서드를 호출합니다.
6. `@Bean` 메서드는 빈 종이(`registry`)를 만듭니다.
7. `@Bean` 메서드는 그 종이를 `ChatWebSocketConfiguration`의 메서드에 건넵니다.
8. 우리 메서드는 종이에 답을 적습니다.
9. `@Bean` 메서드는 적힌 답을 읽고 부품을 완성합니다.
10. 컨테이너는 완성된 부품을 빈(Bean)으로 등록합니다.
11. 컨테이너는 부품 중 `SimpMessagingTemplate`을 `ChatNotifier`에 넣어 줍니다.
12. `ChatNotifier`는 그 부품으로 고객과 관리자에게 새 메시지를 알립니다.

**명단 추가(`@Enable…`) → 제작자 생성 → 신청서 수집(타입으로) → `@Bean` 메서드가 종이를 건넴 → 우리가 답을 적음 → 제작자가 읽고 부품 완성 → 부품 주입 후 사용**
