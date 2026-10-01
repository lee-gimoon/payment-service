package com.example.payment.chat.application;

import com.example.payment.api.error.ApiException;
import com.example.payment.chat.api.ChatHistoryResponse;
import com.example.payment.chat.api.ChatMessageRequest;
import com.example.payment.chat.api.ChatMessageResponse;
import com.example.payment.chat.api.ChatRoomResponse;
import com.example.payment.chat.domain.ChatMessage;
import com.example.payment.chat.domain.ChatRoom;
import com.example.payment.chat.domain.ChatSender;
import com.example.payment.chat.persistence.ChatMessageRepository;
import com.example.payment.chat.persistence.ChatRoomRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고객과 쇼핑몰 관리자의 1:1 상담. 고객은 토큰의 회원 ID로 자기 방만 쓰므로 방 ID를 받지 않는다.
 * 메시지는 방 행을 잠근 뒤 저장해, 같은 방 메시지의 번호 순서가 저장 완료 순서와 같다.
 */
@Service
public class ChatService {
    static final int PAGE_SIZE = 50;
    static final int ROOM_LIST_SIZE = 100;

    private final ChatRoomRepository rooms;
    private final ChatMessageRepository messages;
    private final ApplicationEventPublisher events;

    public ChatService(ChatRoomRepository rooms, ChatMessageRepository messages, ApplicationEventPublisher events) {
        this.rooms = rooms;
        this.messages = messages;
        this.events = events;
    }

    /** 토큰에서 꺼낸 고객 정보. 이름은 관리자 목록에 표시한다. */
    public record Customer(String id, String name, String email) {
        public Customer {
            name = name.length() > 100 ? name.substring(0, 100) : name;
        }
    }

    /** @param created 새로 저장했으면 true, 같은 메시지의 재전송이면 false */
    public record Sent(ChatMessageResponse message, boolean created) {}

    /** 대화를 어디부터 읽을지. before는 이전 대화 더 보기, after는 다시 연결한 뒤 놓친 메시지 채우기에 쓴다. */
    public record Page(Long before, Long after) {
        public Page {
            if (before != null && after != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "before와 after 중 하나만 보내주세요.");
            }
        }
    }

    @Transactional(readOnly = true)
    public ChatHistoryResponse customerHistory(String customerId, Page page) {
        return rooms.findByCustomerId(customerId)
                .map(room -> history(room, ChatSender.CUSTOMER, page))
                .orElse(ChatHistoryResponse.EMPTY);
    }

    @Transactional
    public Sent sendAsCustomer(Customer customer, ChatMessageRequest request) {
        rooms.insertIfAbsent(UUID.randomUUID().toString(), customer.id(), customer.email(), customer.name(),
                Instant.now());
        ChatRoom room = rooms.findByCustomerIdForUpdate(customer.id()).orElseThrow();
        room.updateCustomer(customer.name(), customer.email());
        return send(room, ChatSender.CUSTOMER, customer.id(), request);
    }

    /** 방이 없거나 고객이 아직 메시지를 보내지 않았으면 아무것도 하지 않는다. */
    @Transactional
    public void markReadByCustomer(String customerId, long lastReadMessageId) {
        rooms.findByCustomerIdForUpdate(customerId)
                .ifPresent(room -> room.markRead(ChatSender.CUSTOMER, lastReadMessageId));
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> roomsForAdmin(boolean waitingOnly) {
        List<ChatRoom> found = waitingOnly
                ? rooms.findByLastSenderTypeOrderByLastMessageIdDesc(ChatSender.CUSTOMER, Limit.of(ROOM_LIST_SIZE))
                : rooms.findByLastMessageIdNotNullOrderByLastMessageIdDesc(Limit.of(ROOM_LIST_SIZE));
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, ChatMessage> lastMessages = messages.findAllById(found.stream().map(ChatRoom::getLastMessageId).toList())
                .stream().collect(Collectors.toMap(ChatMessage::getId, Function.identity()));
        Map<String, Long> unread = messages.countUnreadByAdmin(found.stream().map(ChatRoom::getId).toList())
                .stream().collect(Collectors.toMap(ChatMessageRepository.RoomUnread::getRoomId,
                        ChatMessageRepository.RoomUnread::getUnread));
        return found.stream().map(room -> new ChatRoomResponse(room.getId(), room.getCustomerName(),
                room.getCustomerEmail(), ChatMessageResponse.of(lastMessages.get(room.getLastMessageId())),
                room.isWaitingForAdmin(), unread.getOrDefault(room.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public ChatHistoryResponse adminHistory(String roomId, Page page) {
        return history(rooms.findById(roomId).orElseThrow(ChatService::roomNotFound), ChatSender.ADMIN, page);
    }

    @Transactional
    public Sent sendAsAdmin(String roomId, String adminId, ChatMessageRequest request) {
        ChatRoom room = rooms.findByIdForUpdate(roomId).orElseThrow(ChatService::roomNotFound);
        return send(room, ChatSender.ADMIN, adminId, request);
    }

    @Transactional
    public void markReadByAdmin(String roomId, long lastReadMessageId) {
        rooms.findByIdForUpdate(roomId).orElseThrow(ChatService::roomNotFound)
                .markRead(ChatSender.ADMIN, lastReadMessageId);
    }

    // 호출하는 쪽이 방 행을 잠근 상태여야 한다.
    private Sent send(ChatRoom room, ChatSender sender, String senderId, ChatMessageRequest request) {
        String content = request.content().strip();
        Optional<ChatMessage> sent = messages.findByRoomIdAndClientMessageId(room.getId(), request.clientMessageId());
        if (sent.isPresent()) {
            if (!sent.get().isResendOf(sender, senderId, content)) {
                throw new ApiException(HttpStatus.CONFLICT, "CHAT_MESSAGE_CONFLICT",
                        "이미 다른 내용으로 보낸 메시지 ID입니다. 새 메시지로 다시 보내주세요.");
            }
            return new Sent(ChatMessageResponse.of(sent.get()), false);
        }
        ChatMessage message = messages.save(new ChatMessage(room.getId(), sender, senderId,
                request.clientMessageId(), content));
        room.recordMessage(message);
        ChatMessageResponse response = ChatMessageResponse.of(message);
        // 커밋된 뒤에 실시간 알림이 보낸다. 재전송은 이미 알렸으므로 다시 알리지 않는다.
        events.publishEvent(new ChatMessageSaved(room.getId(), room.getCustomerId(), response));
        return new Sent(response, true);
    }

    // 결과는 항상 오래된 순이다. hasMore는 before면 더 이전, after면 더 이후 메시지가 있다는 뜻이다.
    private ChatHistoryResponse history(ChatRoom room, ChatSender viewer, Page page) {
        List<ChatMessage> found = page.after() != null
                ? messages.findByRoomIdAndIdGreaterThanOrderByIdAsc(room.getId(), page.after(), Limit.of(PAGE_SIZE + 1))
                : messages.findByRoomIdAndIdLessThanOrderByIdDesc(room.getId(),
                        page.before() == null ? Long.MAX_VALUE : page.before(), Limit.of(PAGE_SIZE + 1));
        List<ChatMessageResponse> shown = found.stream().limit(PAGE_SIZE).map(ChatMessageResponse::of).toList();
        long unread = messages.countByRoomIdAndSenderTypeAndIdGreaterThan(room.getId(), viewer.other(),
                room.readMessageId(viewer));
        return new ChatHistoryResponse(page.after() != null ? shown : shown.reversed(), found.size() > PAGE_SIZE,
                unread);
    }

    private static ApiException roomNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "CHAT_ROOM_NOT_FOUND", "상담방을 찾을 수 없습니다.");
    }
}
