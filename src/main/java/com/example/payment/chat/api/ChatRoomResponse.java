package com.example.payment.chat.api;

/**
 * 관리자 상담 목록의 한 줄.
 *
 * @param waiting 마지막 메시지를 고객이 보내 관리자 답변을 기다리는지
 * @param unreadCount 관리자가 아직 읽지 않은 고객 메시지 수
 */
public record ChatRoomResponse(String roomId, String customerName, String customerEmail,
                               ChatMessageResponse lastMessage, boolean waiting, long unreadCount) {}
