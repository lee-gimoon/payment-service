package com.example.payment.chat.infrastructure.websocket;

import com.example.payment.chat.api.ChatMessageResponse;

/** 관리자 구독 주소로 보내는 새 메시지. 어느 상담방의 메시지인지 함께 알린다. */
public record ChatAdminEvent(String roomId, ChatMessageResponse message) {}
