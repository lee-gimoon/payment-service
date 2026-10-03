package com.example.payment.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 주문할 때 고른 배송지를 주문에 복사해 둔 값. 마이페이지에서 배송지를 고치거나 지워도 바뀌지 않는다.
 *
 * @param phone 숫자만 담는다.
 * @param memo 배송 메모. 없으면 null이다.
 */
@Embeddable
public record ShippingAddress(
        @Column(name = "shipping_recipient_name", length = 50) String recipientName,
        @Column(name = "shipping_phone", length = 11) String phone,
        @Column(name = "shipping_postal_code", length = 5) String postalCode,
        @Column(name = "shipping_address", length = 200) String address,
        @Column(name = "shipping_address_detail", length = 100) String addressDetail,
        @Column(name = "shipping_memo", length = 50) String memo) {
}
