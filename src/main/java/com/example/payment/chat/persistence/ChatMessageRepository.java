package com.example.payment.chat.persistence;

import com.example.payment.chat.domain.ChatMessage;
import com.example.payment.chat.domain.ChatSender;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Optional<ChatMessage> findByRoomIdAndClientMessageId(String roomId, String clientMessageId);

    List<ChatMessage> findByRoomIdAndIdLessThanOrderByIdDesc(String roomId, long id, Limit limit);

    List<ChatMessage> findByRoomIdAndIdGreaterThanOrderByIdAsc(String roomId, long id, Limit limit);

    long countByRoomIdAndSenderTypeAndIdGreaterThan(String roomId, ChatSender senderType, long id);

    // 관리자 목록의 방마다 관리자가 아직 읽지 않은 고객 메시지 수. 방마다 따로 세지 않고 한 번에 센다.
    @Query("""
            select m.roomId as roomId, count(m) as unread
            from ChatMessage m, ChatRoom r
            where r.id = m.roomId and r.id in :roomIds
              and m.senderType = com.example.payment.chat.domain.ChatSender.CUSTOMER and m.id > r.adminReadMessageId
            group by m.roomId
            """)
    List<RoomUnread> countUnreadByAdmin(@Param("roomIds") Collection<String> roomIds);

    interface RoomUnread {
        String getRoomId();
        Long getUnread();
    }
}
