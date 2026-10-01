package com.example.payment.chat.api;

import java.util.List;

/**
 * @param messages 오래된 것부터, 한 번에 50개까지. 기본 조회는 최근 메시지, before 조회는 그 번호 이전,
 *                 after 조회는 그 번호 이후 메시지를 준다.
 * @param hasMore 기본·before 조회면 더 이전 메시지가, after 조회면 더 이후 메시지가 있는지.
 *                같은 방향으로 이어서 조회한다(before는 받은 첫 메시지 id, after는 받은 마지막 메시지 id).
 * @param unreadCount 조회한 쪽이 아직 읽지 않은 상대 메시지 수
 */
public record ChatHistoryResponse(List<ChatMessageResponse> messages, boolean hasMore, long unreadCount) {
    public static final ChatHistoryResponse EMPTY = new ChatHistoryResponse(List.of(), false, 0);
}
