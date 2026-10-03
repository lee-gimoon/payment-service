package com.example.payment.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.example.payment.chat.api.ChatMessageRequest;
import com.example.payment.chat.application.ChatService;
import com.example.payment.chat.infrastructure.websocket.ChatWebSocketConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.AbstractBrokerMessageHandler;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 실제 포트로 STOMP 연결을 열어 인증, 구독 권한, 커밋 후 알림을 확인한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"payment.recovery.enabled=false", "order.unpaid-expiry.enabled=false"})
@Testcontainers
class ChatWebSocketTest {
    private static final String BUYER = "buyer-1";
    private static final String OTHER = "buyer-2";
    private static final String ADMIN = "admin-1";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort int port;
    @MockitoBean JwtDecoder jwtDecoder;
    @Autowired ChatService chat;
    @Autowired JdbcTemplate jdbc;
    @Autowired SimpUserRegistry users;
    @Autowired @Qualifier("simpleBrokerMessageHandler") AbstractBrokerMessageHandler broker;

    private final WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
    private final List<StompSession> sessions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE chat_messages, chat_rooms");
        doThrow(new BadJwtException("invalid token")).when(jwtDecoder).decode(anyString());
        doReturn(token("buyer-token", BUYER)).when(jwtDecoder).decode("buyer-token");
        doReturn(token("other-token", OTHER)).when(jwtDecoder).decode("other-token");
        doReturn(token("admin-token", ADMIN, "shop-admin")).when(jwtDecoder).decode("admin-token");
    }

    @AfterEach
    void disconnect() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    }

    @Test
    void newMessagesReachTheCustomerAndAdminsAfterCommit() throws Exception {
        BlockingQueue<String> buyerInbox = subscribe(connect("buyer-token").session(),
                ChatWebSocketConfiguration.CUSTOMER_SUBSCRIPTION);
        BlockingQueue<String> otherInbox = subscribe(connect("other-token").session(),
                ChatWebSocketConfiguration.CUSTOMER_SUBSCRIPTION);
        BlockingQueue<String> adminInbox = subscribe(connect("admin-token").session(),
                ChatWebSocketConfiguration.ADMIN_TOPIC);
        awaitCustomerSubscribed(BUYER);
        awaitCustomerSubscribed(OTHER);
        awaitSubscribed(ChatWebSocketConfiguration.ADMIN_TOPIC);

        ChatMessageRequest question = new ChatMessageRequest(UUID.randomUUID().toString(), "결제했는데 대기로 나와요");
        ChatService.Sent sent = chat.sendAsCustomer(buyer(), question);
        String roomId = jdbc.queryForObject("SELECT id FROM chat_rooms WHERE customer_id = ?", String.class, BUYER);

        String toBuyer = buyerInbox.poll(5, TimeUnit.SECONDS);
        assertThat(toBuyer).isNotNull();
        assertThat(((Number) JsonPath.read(toBuyer, "$.id")).longValue()).isEqualTo(sent.message().id());
        assertThat(JsonPath.<String>read(toBuyer, "$.content")).isEqualTo("결제했는데 대기로 나와요");
        assertThat(Instant.parse(JsonPath.read(toBuyer, "$.createdAt"))).isEqualTo(sent.message().createdAt());
        String toAdmin = adminInbox.poll(5, TimeUnit.SECONDS);
        assertThat(toAdmin).isNotNull();
        assertThat(JsonPath.<String>read(toAdmin, "$.roomId")).isEqualTo(roomId);
        assertThat(JsonPath.<String>read(toAdmin, "$.message.sender")).isEqualTo("CUSTOMER");

        chat.sendAsAdmin(roomId, ADMIN, new ChatMessageRequest(UUID.randomUUID().toString(), "확인해 보겠습니다."));
        String reply = buyerInbox.poll(5, TimeUnit.SECONDS);
        assertThat(reply).isNotNull();
        assertThat(JsonPath.<String>read(reply, "$.sender")).isEqualTo("ADMIN");
        assertThat(JsonPath.<String>read(adminInbox.poll(5, TimeUnit.SECONDS), "$.message.content"))
                .isEqualTo("확인해 보겠습니다.");

        // 재전송은 다시 알리지 않고, 다른 고객에게는 아무것도 가지 않는다.
        assertThat(chat.sendAsCustomer(buyer(), question).created()).isFalse();
        assertThat(buyerInbox.poll(500, TimeUnit.MILLISECONDS)).isNull();
        assertThat(adminInbox).isEmpty();
        assertThat(otherInbox).isEmpty();
    }

    @Test
    void connectionsWithoutValidTokenAreRejected() throws Exception {
        for (String token : new String[] {null, "forged-token"}) {
            Connection connection = connectAsync(token);
            connection.rejected().get(5, TimeUnit.SECONDS);
            assertThat(connection.sessionFuture()).isNotCompleted();
        }
    }

    @Test
    void subscriptionsStayOnEachSideAndSendIsRejected() throws Exception {
        BlockingQueue<String> adminInbox = subscribe(connect("admin-token").session(),
                ChatWebSocketConfiguration.ADMIN_TOPIC);
        awaitSubscribed(ChatWebSocketConfiguration.ADMIN_TOPIC);

        Connection adminTopic = connect("buyer-token");
        subscribe(adminTopic.session(), ChatWebSocketConfiguration.ADMIN_TOPIC);
        adminTopic.rejected().get(5, TimeUnit.SECONDS);

        // 다른 회원의 개인 주소를 직접 구독할 수도 없다.
        Connection otherQueue = connect("buyer-token");
        subscribe(otherQueue.session(), ChatWebSocketConfiguration.CUSTOMER_QUEUE + "-usersomeone-else");
        otherQueue.rejected().get(5, TimeUnit.SECONDS);

        // 브로커 주소로 직접 보내 관리자에게 가짜 메시지를 뿌릴 수 없다.
        Connection forged = connect("buyer-token");
        forged.session().send(ChatWebSocketConfiguration.ADMIN_TOPIC,
                "{\"roomId\":\"fake\"}".getBytes(StandardCharsets.UTF_8));
        forged.rejected().get(5, TimeUnit.SECONDS);

        // 쇼핑몰 관리자는 고객 주소를 구독하지 않는다. 답변은 관리자 주소로 받는다.
        Connection adminAsCustomer = connect("admin-token");
        subscribe(adminAsCustomer.session(), ChatWebSocketConfiguration.CUSTOMER_SUBSCRIPTION);
        adminAsCustomer.rejected().get(5, TimeUnit.SECONDS);

        List<StompSession> rejected = List.of(adminTopic.session(), otherQueue.session(), forged.session(),
                adminAsCustomer.session());
        await(() -> rejected.stream().anyMatch(StompSession::isConnected) ? Optional.empty() : Optional.of(true));
        assertThat(adminInbox.poll(300, TimeUnit.MILLISECONDS)).isNull();
    }

    private ChatService.Customer buyer() {
        return new ChatService.Customer(BUYER, "김모도", "buyer-1@modo.test");
    }

    private static Jwt token(String value, String subject, String... realmRoles) {
        return Jwt.withTokenValue(value).header("alg", "RS256").subject(subject)
                .claim("realm_access", Map.of("roles", List.of(realmRoles))).build();
    }

    /** 연결에 성공한 세션과, 서버가 ERROR 프레임을 보내거나 연결을 닫으면 완료되는 future. */
    private record Connection(CompletableFuture<StompSession> sessionFuture, CompletableFuture<Void> rejected) {
        StompSession session() throws Exception {
            return sessionFuture.get(5, TimeUnit.SECONDS);
        }
    }

    private Connection connect(String token) throws Exception {
        Connection connection = connectAsync(token);
        sessions.add(connection.session());
        return connection;
    }

    private Connection connectAsync(String token) {
        CompletableFuture<Void> rejected = new CompletableFuture<>();
        StompHeaders headers = new StompHeaders();
        if (token != null) {
            headers.add("Authorization", "Bearer " + token);
        }
        CompletableFuture<StompSession> session = client.connectAsync("ws://127.0.0.1:{port}/ws",
                new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {
                    @Override
                    public Type getPayloadType(StompHeaders frameHeaders) {
                        return byte[].class;
                    }

                    @Override
                    public void handleFrame(StompHeaders frameHeaders, Object payload) {
                        rejected.complete(null);
                    }

                    @Override
                    public void handleException(StompSession stompSession, StompCommand command,
                                                StompHeaders frameHeaders, byte[] payload, Throwable exception) {
                        rejected.complete(null);
                    }

                    @Override
                    public void handleTransportError(StompSession stompSession, Throwable exception) {
                        rejected.complete(null);
                    }
                }, port);
        return new Connection(session, rejected);
    }

    private static BlockingQueue<String> subscribe(StompSession session, String destination) {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        return received;
    }

    // 구독 프레임은 비동기로 처리되므로, 메시지를 보내기 전에 브로커에 구독이 등록될 때까지 기다린다.
    private void awaitCustomerSubscribed(String customerId) throws InterruptedException {
        String sessionId = await(() -> Optional.ofNullable(users.getUser(customerId))
                .flatMap(user -> user.getSessions().stream().findFirst()).map(session -> session.getId()));
        awaitSubscribed(ChatWebSocketConfiguration.CUSTOMER_QUEUE + "-user" + sessionId);
    }

    private void awaitSubscribed(String brokerDestination) throws InterruptedException {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setDestination(brokerDestination);
        Message<byte[]> probe = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        await(() -> ((SimpleBrokerMessageHandler) broker).getSubscriptionRegistry().findSubscriptions(probe).isEmpty()
                ? Optional.empty() : Optional.of(true));
    }

    private static <T> T await(Supplier<Optional<T>> condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Optional<T> value = condition.get();
            if (value.isPresent()) {
                return value.get();
            }
            Thread.sleep(20);
        }
        throw new AssertionError("5초 안에 조건을 만족하지 않았습니다.");
    }
}
