package com.example.payment.chat.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 앞뒤 공백은 지우고 저장한다. 같은 clientMessageId로 다시 보내면 새로 저장하지 않는다. */
public record ChatMessageRequest(
        @NotBlank @Pattern(regexp = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}") String clientMessageId,
        @NotBlank @Size(max = 1000) String content) {}
