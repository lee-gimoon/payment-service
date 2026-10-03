package com.example.payment.customer;

/** @param phone 숫자만 담는다. 화면에서 하이픈을 넣어 보여준다. */
public record AddressResponse(String id, String label, String recipientName, String phone, String postalCode,
                              String address, String addressDetail, boolean defaultAddress) {
    static AddressResponse of(CustomerAddress address) {
        return new AddressResponse(address.getId(), address.getLabel(), address.getRecipientName(),
                address.getPhone(), address.getPostalCode(), address.getAddress(), address.getAddressDetail(),
                address.isDefaultAddress());
    }
}
