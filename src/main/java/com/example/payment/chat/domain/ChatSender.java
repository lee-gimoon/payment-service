package com.example.payment.chat.domain;

/** 메시지를 보낸 쪽. 고객 화면에는 관리자 개인 대신 쇼핑몰 이름으로 표시한다. */
public enum ChatSender {
    CUSTOMER, ADMIN;

    public ChatSender other() {
        return this == CUSTOMER ? ADMIN : CUSTOMER;
    }
}
