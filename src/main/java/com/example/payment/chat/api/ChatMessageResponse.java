package com.example.payment.chat.api;

import com.example.payment.chat.domain.ChatMessage;
import com.example.payment.chat.domain.ChatSender;
import java.time.Instant;

/** 보낸 관리자의 ID는 내보내지 않는다. 고객 화면은 ADMIN 메시지를 쇼핑몰 이름으로 표시한다. */
public record ChatMessageResponse(long id, String clientMessageId, ChatSender sender, String content,
                                  Instant createdAt) {
    public static ChatMessageResponse of(ChatMessage message) {
        return new ChatMessageResponse(message.getId(), message.getClientMessageId(), message.getSenderType(),
                message.getContent(), message.getCreatedAt());
    }
}
