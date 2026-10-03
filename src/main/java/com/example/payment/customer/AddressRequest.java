package com.example.payment.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 배송지 입력. 연락처는 하이픈을 넣어 보내도 되며 저장할 때 숫자만 남긴다.
 *
 * @param makeDefault true면 기본 배송지로 지정한다. false여도 이미 기본인 배송지를 해제하지는 않는다.
 */
public record AddressRequest(
        @NotBlank @Size(max = 20) String label,
        @NotBlank @Size(max = 50) String recipientName,
        @NotNull @Pattern(regexp = "0\\d{1,2}-?\\d{3,4}-?\\d{4}") String phone,
        @NotNull @Pattern(regexp = "\\d{5}") String postalCode,
        @NotBlank @Size(max = 200) String address,
        @Size(max = 100) String addressDetail,
        boolean makeDefault) {
}
