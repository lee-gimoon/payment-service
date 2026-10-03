package com.example.payment.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.payment.chat.api.ChatMessageRequest;
import com.example.payment.chat.application.ChatService;
import com.example.payment.config.SecurityConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"payment.recovery.enabled=false", "order.unpaid-expiry.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class ChatIntegrationTest {
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

    @Autowired MockMvc mvc;
    @Autowired ChatService chat;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE chat_messages, chat_rooms");
    }

    @Test
    void firstMessageCreatesTheCustomersOnlyRoom() throws Exception {
        mvc.perform(get("/chat/messages").with(customer(BUYER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(0))
                .andExpect(jsonPath("$.unreadCount").value(0));
        assertThat(roomCount()).isZero();

        send(BUYER, "M이랑 L 중 뭐가 커요?")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sender").value("CUSTOMER"));
        send(BUYER, "  길이도 궁금해요 \n")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("길이도 궁금해요"));

        assertThat(roomCount()).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT customer_name, customer_email, last_sender_type FROM chat_rooms"))
                .containsEntry("customer_name", "김모도").containsEntry("customer_email", "buyer-1@modo.test")
                .containsEntry("last_sender_type", "CUSTOMER");
        mvc.perform(get("/chat/messages").with(customer(BUYER)))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].content").value("M이랑 L 중 뭐가 커요?"))
                .andExpect(jsonPath("$.messages[1].content").value("길이도 궁금해요"))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void customersOnlySeeTheirOwnConversation() throws Exception {
        send(BUYER, "내 문의").andExpect(status().isCreated());
        mvc.perform(get("/chat/messages").with(customer(OTHER)))
                .andExpect(jsonPath("$.messages.length()").value(0));

        send(OTHER, "다른 문의").andExpect(status().isCreated());
        assertThat(roomCount()).isEqualTo(2);
        mvc.perform(get("/chat/messages").with(customer(OTHER)))
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].content").value("다른 문의"));
    }

    @Test
    void resentMessageIsStoredOnce() throws Exception {
        String clientMessageId = UUID.randomUUID().toString();
        long id = longAt(send(BUYER, clientMessageId, "주문 문의").andExpect(status().isCreated()), "$.id");

        long resent = longAt(send(BUYER, clientMessageId, "주문 문의").andExpect(status().isOk()), "$.id");
        assertThat(resent).isEqualTo(id);
        send(BUYER, clientMessageId, "다른 내용")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAT_MESSAGE_CONFLICT"));
        assertThat(messageCount()).isEqualTo(1);
    }

    @Test
    void adminSeesWaitingRoomsRepliesAndTracksReads() throws Exception {
        long question = longAt(send(BUYER, "결제했는데 대기로 나와요"), "$.id");
        send(OTHER, "사이즈 문의");
        String roomId = roomIdOf(BUYER);

        mvc.perform(get("/admin/chat/rooms").param("waiting", "true").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].customerEmail").value("buyer-2@modo.test"))
                .andExpect(jsonPath("$[1].roomId").value(roomId))
                .andExpect(jsonPath("$[1].customerName").value("김모도"))
                .andExpect(jsonPath("$[1].lastMessage.content").value("결제했는데 대기로 나와요"))
                .andExpect(jsonPath("$[1].waiting").value(true))
                .andExpect(jsonPath("$[1].unreadCount").value(1));
        mvc.perform(get("/admin/chat/rooms/{roomId}/messages", roomId).with(admin()))
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.unreadCount").value(1));
        mvc.perform(post("/admin/chat/rooms/{roomId}/read", roomId).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(readJson(question)))
                .andExpect(status().isNoContent());

        reply(roomId, "확인해 보겠습니다.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sender").value("ADMIN"));

        // 답장한 방은 답변 대기에서 빠지고, 최근 메시지 순으로 맨 위에 온다.
        mvc.perform(get("/admin/chat/rooms").param("waiting", "true").with(admin()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].customerEmail").value("buyer-2@modo.test"));
        mvc.perform(get("/admin/chat/rooms").with(admin()))
                .andExpect(jsonPath("$[0].roomId").value(roomId))
                .andExpect(jsonPath("$[0].waiting").value(false))
                .andExpect(jsonPath("$[0].unreadCount").value(0))
                .andExpect(jsonPath("$[0].lastMessage.sender").value("ADMIN"));

        // 고객에게는 새 답변 하나가 보이고, 답한 관리자의 ID는 내보내지 않는다.
        long answer = longAt(mvc.perform(get("/chat/messages").with(customer(BUYER)))
                .andExpect(jsonPath("$.messages[1].sender").value("ADMIN"))
                .andExpect(jsonPath("$.messages[1].senderId").doesNotExist())
                .andExpect(jsonPath("$.unreadCount").value(1)), "$.messages[1].id");
        mvc.perform(post("/chat/read").with(customer(BUYER))
                        .contentType(MediaType.APPLICATION_JSON).content(readJson(answer)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/chat/messages").with(customer(BUYER)))
                .andExpect(jsonPath("$.unreadCount").value(0));
        assertThat(jdbc.queryForObject("SELECT sender_id FROM chat_messages WHERE id = ?", String.class, answer))
                .isEqualTo(ADMIN);
    }

    @Test
    void waitingFilterFindsUnansweredRoomsOutsideTheLatestHundred() throws Exception {
        send(BUYER, "아직 답변을 기다리는 오래된 문의").andExpect(status().isCreated());
        String waitingRoomId = roomIdOf(BUYER);
        for (int number = 1; number <= 100; number++) {
            String customerId = "answered-buyer-" + number;
            ChatService.Customer buyer = new ChatService.Customer(customerId, "고객 " + number, null);
            chat.sendAsCustomer(buyer, new ChatMessageRequest(UUID.randomUUID().toString(), "최근 문의"));
            chat.sendAsAdmin(roomIdOf(customerId), ADMIN,
                    new ChatMessageRequest(UUID.randomUUID().toString(), "답변 완료"));
        }

        String allRooms = mvc.perform(get("/admin/chat/rooms").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(100))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonPath.<List<String>>read(allRooms, "$[*].roomId")).doesNotContain(waitingRoomId);
        mvc.perform(get("/admin/chat/rooms").param("waiting", "true").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].roomId").value(waitingRoomId))
                .andExpect(jsonPath("$[0].waiting").value(true))
                .andExpect(jsonPath("$[0].unreadCount").value(1));
    }

    @Test
    void readMarkCannotRunAheadOfExistingMessages() throws Exception {
        mvc.perform(post("/chat/read").with(customer(BUYER))
                        .contentType(MediaType.APPLICATION_JSON).content(readJson(1)))
                .andExpect(status().isNoContent());

        send(BUYER, "문의");
        mvc.perform(post("/chat/read").with(customer(BUYER))
                        .contentType(MediaType.APPLICATION_JSON).content(readJson(Long.MAX_VALUE)))
                .andExpect(status().isNoContent());
        reply(roomIdOf(BUYER), "답변");

        mvc.perform(get("/chat/messages").with(customer(BUYER)))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void olderMessagesArePagedWithBefore() throws Exception {
        ChatService.Customer buyer = new ChatService.Customer(BUYER, "김모도", "buyer-1@modo.test");
        for (int number = 1; number <= 51; number++) {
            chat.sendAsCustomer(buyer, new ChatMessageRequest(UUID.randomUUID().toString(), "메시지 " + number));
        }

        long oldestShown = longAt(mvc.perform(get("/chat/messages").with(customer(BUYER)))
                .andExpect(jsonPath("$.messages.length()").value(50))
                .andExpect(jsonPath("$.messages[0].content").value("메시지 2"))
                .andExpect(jsonPath("$.messages[49].content").value("메시지 51"))
                .andExpect(jsonPath("$.hasMore").value(true)), "$.messages[0].id");
        mvc.perform(get("/chat/messages").param("before", String.valueOf(oldestShown)).with(customer(BUYER)))
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].content").value("메시지 1"))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void messagesAfterAnIdFillTheGapAfterReconnecting() throws Exception {
        long first = longAt(send(BUYER, "첫째"), "$.id");
        send(BUYER, "둘째");
        send(BUYER, "셋째");

        mvc.perform(get("/chat/messages").param("after", String.valueOf(first)).with(customer(BUYER)))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].content").value("둘째"))
                .andExpect(jsonPath("$.messages[1].content").value("셋째"))
                .andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(get("/admin/chat/rooms/{roomId}/messages", roomIdOf(BUYER)).param("after", String.valueOf(first))
                        .with(admin()))
                .andExpect(jsonPath("$.messages.length()").value(2));
        mvc.perform(get("/chat/messages").param("after", "1").param("before", "5").with(customer(BUYER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void concurrentSendsShareOneRoomAndStoreResentMessageOnce() throws Exception {
        ChatService.Customer buyer = new ChatService.Customer(BUYER, "김모도", "buyer-1@modo.test");
        String resentId = UUID.randomUUID().toString();
        List<ChatMessageRequest> requests = new ArrayList<>();
        for (int number = 1; number <= 4; number++) {
            requests.add(new ChatMessageRequest(UUID.randomUUID().toString(), "동시 메시지 " + number));
        }
        for (int copy = 0; copy < 3; copy++) {
            requests.add(new ChatMessageRequest(resentId, "재전송 메시지"));
        }

        CountDownLatch start = new CountDownLatch(1);
        List<Future<ChatService.Sent>> sends = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (ChatMessageRequest request : requests) {
                sends.add(executor.submit(() -> {
                    start.await();
                    return chat.sendAsCustomer(buyer, request);
                }));
            }
            start.countDown();
            long created = 0;
            for (Future<ChatService.Sent> send : sends) {
                created += send.get(10, TimeUnit.SECONDS).created() ? 1 : 0;
            }
            assertThat(created).isEqualTo(5);
        }

        assertThat(roomCount()).isEqualTo(1);
        assertThat(messageCount()).isEqualTo(5);
        // 방 요약이 나중에 저장된 메시지를 덮어쓰지 않았다.
        assertThat(jdbc.queryForObject("SELECT last_message_id = (SELECT max(id) FROM chat_messages) FROM chat_rooms",
                Boolean.class)).isTrue();
    }

    @Test
    void adminApisRequireShopAdminRole() throws Exception {
        send(BUYER, "문의");
        String roomId = roomIdOf(BUYER);

        mvc.perform(get("/chat/messages")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/chat/rooms")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/chat/rooms").with(customer(BUYER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/admin/chat/rooms/{roomId}/messages", roomId).with(customer(BUYER)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/chat/rooms/{roomId}/messages", roomId).with(customer(BUYER))
                        .contentType(MediaType.APPLICATION_JSON).content(messageJson(UUID.randomUUID().toString(), "답변")))
                .andExpect(status().isForbidden());
        assertThat(messageCount()).isEqualTo(1);
    }

    @Test
    void shopAdminsAnswerFromTheAdminScreenNotTheCustomerChannel() throws Exception {
        mvc.perform(get("/chat/messages").with(admin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/chat/messages").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(messageJson(UUID.randomUUID().toString(), "문의")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/chat/read").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(readJson(1)))
                .andExpect(status().isForbidden());
        assertThat(roomCount()).isZero();
    }

    @Test
    void invalidMessagesAndUnknownRoomsAreRejected() throws Exception {
        String id = UUID.randomUUID().toString();
        for (String body : List.of(messageJson(id, ""), messageJson(id, "   "), messageJson(id, "가".repeat(1001)),
                messageJson("not-a-uuid", "문의"), "{\"content\":\"문의\"}")) {
            mvc.perform(post("/chat/messages").with(customer(BUYER))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        assertThat(roomCount()).isZero();
        send(BUYER, "가".repeat(1000)).andExpect(status().isCreated());

        reply("missing-room", "답변")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_ROOM_NOT_FOUND"));
        mvc.perform(get("/admin/chat/rooms/{roomId}/messages", "missing-room").with(admin()))
                .andExpect(status().isNotFound());
    }

    private ResultActions send(String customerId, String content) throws Exception {
        return send(customerId, UUID.randomUUID().toString(), content);
    }

    private ResultActions send(String customerId, String clientMessageId, String content) throws Exception {
        return mvc.perform(post("/chat/messages").with(customer(customerId))
                .contentType(MediaType.APPLICATION_JSON).content(messageJson(clientMessageId, content)));
    }

    private ResultActions reply(String roomId, String content) throws Exception {
        return mvc.perform(post("/admin/chat/rooms/{roomId}/messages", roomId).with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(messageJson(UUID.randomUUID().toString(), content)));
    }

    private static RequestPostProcessor customer(String customerId) {
        return jwt().jwt(token -> token.subject(customerId).claim("email", customerId + "@modo.test")
                .claim("family_name", "김").claim("given_name", "모도"));
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject(ADMIN))
                .authorities(new SimpleGrantedAuthority("ROLE_" + SecurityConfiguration.SHOP_ADMIN));
    }

    private static String messageJson(String clientMessageId, String content) {
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "{\"clientMessageId\":\"" + clientMessageId + "\",\"content\":\"" + escaped + "\"}";
    }

    private static String readJson(long lastReadMessageId) {
        return "{\"lastReadMessageId\":" + lastReadMessageId + "}";
    }

    private static long longAt(ResultActions result, String path) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, path)).longValue();
    }

    private String roomIdOf(String customerId) {
        return jdbc.queryForObject("SELECT id FROM chat_rooms WHERE customer_id = ?", String.class, customerId);
    }

    private long roomCount() {
        return jdbc.queryForObject("SELECT count(*) FROM chat_rooms", Long.class);
    }

    private long messageCount() {
        return jdbc.queryForObject("SELECT count(*) FROM chat_messages", Long.class);
    }
}
