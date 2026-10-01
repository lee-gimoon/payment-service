package com.example.payment.chat.application;

import com.example.payment.chat.api.ChatMessageResponse;

/**
 * 새로 저장한 상담 메시지. 커밋된 뒤 실시간 알림이 상담방 고객(Keycloak sub)과 쇼핑몰 관리자에게 전한다.
 * 재전송으로 저장하지 않은 메시지는 발행하지 않는다.
 */
public record ChatMessageSaved(String roomId, String customerId, ChatMessageResponse message) {}
