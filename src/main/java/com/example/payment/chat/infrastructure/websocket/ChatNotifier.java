package com.example.payment.chat.infrastructure.websocket;

import com.example.payment.chat.application.ChatMessageSaved;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 새 상담 메시지가 DB에 확실히 저장(커밋)된 뒤, 열린 WebSocket 연결로 알린다. 누가 보냈든 두 곳에 모두 알린다.
 * <ul>
 *   <li>고객: 그 고객의 채널(/user/queue/chat)로 메시지를 보낸다.</li>
 *   <li>쇼핑몰 관리자: 관리자 채널(/topic/admin/chat)로 어느 상담방의 메시지인지 함께 보낸다.</li>
 * </ul>
 * onSaved가 어떻게 불리는지는 메서드 주석에 있다.
 * 커밋 전에 알리면 알림을 받은 화면이 서버에 조회해도 아직 메시지가 안 보이거나, 저장이 취소(롤백)된 메시지를 화면에 띄울 수 있다.
 * 연결이 끊겨 알림을 놓쳐도 메시지는 DB에 있으므로, 화면이 다시 연결할 때 조회로 채운다. 11
 */
@Component
class ChatNotifier {
    private final SimpMessagingTemplate messaging;

    ChatNotifier(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    // @TransactionalEventListener: 이벤트가 발행되면 Spring이 이 메서드를 자동으로 호출하게 한다. 호출 시점은 트랜잭션 커밋 뒤다.
    //  - 어떤 이벤트에 호출되나: 매개변수 타입(ChatMessageSaved)과 같은 타입의 이벤트가 발행될 때. 메서드 이름은 상관없다.
    //  - 언제 호출되나: publishEvent를 실행한 트랜잭션이 커밋된 뒤(기본값 AFTER_COMMIT). 롤백되면 호출되지 않는다.
    //  - 무엇이 넘어오나: publishEvent(event)에 넘긴 바로 그 객체가 매개변수 saved로 들어온다.
    //  - 어떻게 이어지나: 앱 시작 때 Spring이 빈을 훑어 이 어노테이션이 붙은 메서드를 명단에 올리고,
    //    이벤트가 발행되면 타입으로 찾아 커밋 뒤에 호출한다. 그래서 이 메서드를 직접 부르는 코드가 없다.
    //  - 조건: 이 클래스가 빈(@Component)이어야 하고, 트랜잭션 안에서 발행해야 한다. 트랜잭션 밖에서 발행하면 기본값으로는 호출되지 않는다.
    @TransactionalEventListener
    void onSaved(ChatMessageSaved saved) {
        // 고객에게: 방 주인 고객(sub)으로 연결된 모든 탭·기기에 보낸다. 고객이 보낸 메시지면 다른 탭·기기도 이걸로 맞춰진다.
        // 보낸 탭은 HTTP 응답과 이 알림을 둘 다 받지만, 화면이 메시지 번호로 합쳐 한 번만 보여 준다.
        messaging.convertAndSendToUser(saved.customerId(), ChatWebSocketConfiguration.CUSTOMER_QUEUE, saved.message());
        // 관리자에게: 관리자 채널을 구독한 모든 관리자에게 보낸다. 어느 상담방의 메시지인지 알도록 방 번호를 함께 담는다.
        messaging.convertAndSend(ChatWebSocketConfiguration.ADMIN_TOPIC,
                new ChatAdminEvent(saved.roomId(), saved.message()));
    }
}
