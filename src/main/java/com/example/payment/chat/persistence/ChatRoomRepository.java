package com.example.payment.chat.persistence;

import com.example.payment.chat.domain.ChatRoom;
import com.example.payment.chat.domain.ChatSender;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, String> {
    // 첫 메시지를 동시에 두 번 보내도 방은 하나만 생긴다. 이미 있으면 아무것도 하지 않아 트랜잭션이 깨지지 않는다.
    @Modifying
    @Query(value = """
            INSERT INTO chat_rooms (id, customer_id, customer_email, customer_name, created_at)
            VALUES (:id, :customerId, :customerEmail, :customerName, :createdAt)
            ON CONFLICT (customer_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("id") String id, @Param("customerId") String customerId,
                        @Param("customerEmail") String customerEmail, @Param("customerName") String customerName,
                        @Param("createdAt") Instant createdAt);

    Optional<ChatRoom> findByCustomerId(String customerId);

    // 같은 방의 메시지 저장과 읽음 표시를 직렬화한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChatRoom r where r.customerId = :customerId")
    Optional<ChatRoom> findByCustomerIdForUpdate(@Param("customerId") String customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChatRoom r where r.id = :id")
    Optional<ChatRoom> findByIdForUpdate(@Param("id") String id);

    List<ChatRoom> findByLastMessageIdNotNullOrderByLastMessageIdDesc(Limit limit);

    List<ChatRoom> findByLastSenderTypeOrderByLastMessageIdDesc(ChatSender lastSenderType, Limit limit);
}
