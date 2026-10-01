package com.example.payment.chat.infrastructure.websocket;

import com.example.payment.chat.application.ChatMessageSaved;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋된 새 메시지를 WebSocket으로 알린다. 커밋 전에 알리면 받은 쪽이 아직 조회되지 않는 메시지를 보거나,
 * 롤백된 메시지를 받을 수 있다. 알림이 유실돼도 메시지는 DB에 있으므로 다시 연결할 때 조회로 채운다.
 */
@Component
class ChatNotifier {
    private final SimpMessagingTemplate messaging;

    ChatNotifier(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener
    void onSaved(ChatMessageSaved saved) {
        messaging.convertAndSendToUser(saved.customerId(), ChatWebSocketConfiguration.CUSTOMER_QUEUE, saved.message());
        messaging.convertAndSend(ChatWebSocketConfiguration.ADMIN_TOPIC,
                new ChatAdminEvent(saved.roomId(), saved.message()));
    }
}
