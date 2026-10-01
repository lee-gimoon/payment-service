package com.example.payment.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 상담 메시지 한 개. 상담 기록으로 보존하며 수정·삭제하지 않는다. */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    // DB가 매기는 증가 번호. 같은 방 안에서는 대화 순서와 같다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String roomId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChatSender senderType;

    // 보낸 회원의 Keycloak ID. 어느 관리자가 답했는지 남기며 고객에게는 보이지 않는다.
    @Column(nullable = false, length = 64)
    private String senderId;

    // 브라우저가 만든 메시지 ID. 같은 방에서 같은 값으로 다시 보내면 새로 저장하지 않는다.
    @Column(nullable = false, length = 36)
    private String clientMessageId;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false)
    private Instant createdAt;

    protected ChatMessage() {}

    public ChatMessage(String roomId, ChatSender senderType, String senderId, String clientMessageId, String content) {
        this.roomId = roomId;
        this.senderType = senderType;
        this.senderId = senderId;
        this.clientMessageId = clientMessageId;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getRoomId() { return roomId; }
    public ChatSender getSenderType() { return senderType; }
    public String getClientMessageId() { return clientMessageId; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }

    /** 재전송인지 판단한다. 같은 메시지 ID로 다른 사람이나 다른 내용을 보냈다면 재전송이 아니다. */
    public boolean isResendOf(ChatSender senderType, String senderId, String content) {
        return this.senderType == senderType && this.senderId.equals(senderId) && this.content.equals(content);
    }
}
