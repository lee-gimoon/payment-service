package com.example.payment.chat.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 화면에 보여 준 마지막 메시지 번호. 이 번호까지 상대 메시지를 읽은 것으로 표시한다. */
public record ChatReadRequest(@NotNull @PositiveOrZero Long lastReadMessageId) {}
