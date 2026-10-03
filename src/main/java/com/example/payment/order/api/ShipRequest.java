package com.example.payment.order.api;

import com.example.payment.order.domain.Carrier;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** @param trackingNumber 송장번호. 하이픈 없이 숫자 8~20자리다. */
public record ShipRequest(@NotNull Carrier carrier, @NotNull @Pattern(regexp = "[0-9]{8,20}") String trackingNumber) {
}
