package com.example.payment.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 고객 한 명과 쇼핑몰 관리자의 1:1 상담 대화. 고객당 하나이며 첫 메시지를 보낼 때 만든다.
 * 메시지 저장과 읽음 표시는 이 행을 잠근 뒤 바꾼다.
 */
@Entity
@Table(name = "chat_rooms")
public class ChatRoom {
    @Id
    @Column(length = 36)
    private String id;

    // 상담을 시작한 회원의 Keycloak ID(access token의 sub).
    @Column(nullable = false, unique = true, length = 64)
    private String customerId;

    // 관리자 목록에 보일 고객 정보. 고객이 메시지를 보낼 때 토큰 값으로 갱신한다.
    @Column(length = 255)
    private String customerEmail;

    @Column(nullable = false, length = 100)
    private String customerName;

    @Column(nullable = false)
    private Instant createdAt;

    private Long lastMessageId;
    private Instant lastMessageAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ChatSender lastSenderType;

    // 각자 마지막으로 읽은 메시지 번호. 상대가 이 번호 뒤에 보낸 메시지가 안 읽은 메시지다.
    @Column(nullable = false)
    private long customerReadMessageId;

    @Column(nullable = false)
    private long adminReadMessageId;

    protected ChatRoom() {}

    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getCustomerEmail() { return customerEmail; }
    public String getCustomerName() { return customerName; }
    public Long getLastMessageId() { return lastMessageId; }

    /** 마지막 메시지를 고객이 보냈으면 관리자가 답할 차례다. */
    public boolean isWaitingForAdmin() {
        return lastSenderType == ChatSender.CUSTOMER;
    }

    public long readMessageId(ChatSender reader) {
        return reader == ChatSender.CUSTOMER ? customerReadMessageId : adminReadMessageId;
    }

    public void updateCustomer(String name, String email) {
        this.customerName = name;
        this.customerEmail = email;
    }

    // 보낸 사람은 자기 메시지까지 읽은 것으로 본다.
    public void recordMessage(ChatMessage message) {
        this.lastMessageId = message.getId();
        this.lastMessageAt = message.getCreatedAt();
        this.lastSenderType = message.getSenderType();
        markRead(message.getSenderType(), message.getId());
    }

    // 읽음 표시는 앞으로만 움직이고, 아직 없는 메시지 번호까지 앞서가지 않는다.
    public void markRead(ChatSender reader, long messageId) {
        if (lastMessageId == null) {
            return;
        }
        long upTo = Math.min(messageId, lastMessageId);
        if (reader == ChatSender.CUSTOMER) {
            customerReadMessageId = Math.max(customerReadMessageId, upTo);
        } else {
            adminReadMessageId = Math.max(adminReadMessageId, upTo);
        }
    }
}
