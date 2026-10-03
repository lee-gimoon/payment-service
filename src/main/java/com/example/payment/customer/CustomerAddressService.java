package com.example.payment.customer;

import com.example.payment.api.error.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 마이페이지 배송지. 쓰기는 회원 단위로 잠가 개수 제한과 "기본 배송지 하나" 규칙을 함께 지킨다. */
@Service
public class CustomerAddressService {
    static final int MAX_ADDRESSES = 10;

    private final CustomerAddressRepository addresses;

    public CustomerAddressService(CustomerAddressRepository addresses) {
        this.addresses = addresses;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(String customerId) {
        return addresses.findByCustomerIdOrderByDefaultAddressDescCreatedAtDesc(customerId).stream()
                .map(AddressResponse::of).toList();
    }

    /** 주문에 복사할 내 배송지. 다른 회원의 배송지는 없는 배송지처럼 거부한다. */
    @Transactional(readOnly = true)
    public AddressResponse forOrder(String customerId, String addressId) {
        return addresses.findById(addressId)
                .filter(address -> address.getCustomerId().equals(customerId))
                .map(AddressResponse::of)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "ADDRESS_NOT_FOUND",
                        "배송지를 찾을 수 없습니다. 배송지를 다시 골라주세요."));
    }

    /** 첫 배송지는 고르지 않아도 기본 배송지가 된다. */
    @Transactional
    public AddressResponse create(String customerId, AddressRequest request) {
        List<CustomerAddress> mine = lockAndLoad(customerId);
        if (mine.size() >= MAX_ADDRESSES) {
            throw new ApiException(HttpStatus.CONFLICT, "ADDRESS_LIMIT_EXCEEDED",
                    "배송지는 최대 " + MAX_ADDRESSES + "개까지 저장할 수 있습니다.");
        }
        boolean makeDefault = mine.isEmpty() || request.makeDefault();
        if (makeDefault) {
            clearDefault(mine);
        }
        return AddressResponse.of(addresses.save(new CustomerAddress(customerId, request, makeDefault)));
    }

    @Transactional
    public AddressResponse update(String customerId, String addressId, AddressRequest request) {
        List<CustomerAddress> mine = lockAndLoad(customerId);
        CustomerAddress target = find(mine, addressId);
        target.update(request);
        if (request.makeDefault() && !target.isDefaultAddress()) {
            clearDefault(mine);
            target.markDefault();
        }
        return AddressResponse.of(target);
    }

    @Transactional
    public AddressResponse makeDefault(String customerId, String addressId) {
        List<CustomerAddress> mine = lockAndLoad(customerId);
        CustomerAddress target = find(mine, addressId);
        if (!target.isDefaultAddress()) {
            clearDefault(mine);
            target.markDefault();
        }
        return AddressResponse.of(target);
    }

    /** 기본 배송지를 지우면 남은 배송지 중 가장 최근에 추가한 것이 기본이 된다. */
    @Transactional
    public void delete(String customerId, String addressId) {
        List<CustomerAddress> mine = lockAndLoad(customerId);
        CustomerAddress target = find(mine, addressId);
        addresses.delete(target);
        // 삭제를 먼저 반영해야 다른 배송지를 기본으로 바꿀 때 기본 배송지가 잠시 둘이 되지 않는다.
        addresses.flush();
        if (target.isDefaultAddress()) {
            mine.stream().filter(address -> address != target).findFirst().ifPresent(CustomerAddress::markDefault);
        }
    }

    private List<CustomerAddress> lockAndLoad(String customerId) {
        addresses.lockCustomer(customerId);
        return addresses.findByCustomerIdOrderByDefaultAddressDescCreatedAtDesc(customerId);
    }

    // 다른 배송지를 기본으로 바꾸기 전에 기존 기본 배송지 해제를 먼저 반영해 DB의 "기본 하나" 제약을 지킨다.
    private void clearDefault(List<CustomerAddress> mine) {
        mine.stream().filter(CustomerAddress::isDefaultAddress).forEach(CustomerAddress::unmarkDefault);
        addresses.flush();
    }

    /** 다른 회원의 배송지는 있는지 알리지 않고 없는 배송지처럼 응답한다. */
    private static CustomerAddress find(List<CustomerAddress> mine, String addressId) {
        return mine.stream().filter(address -> address.getId().equals(addressId)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "배송지를 찾을 수 없습니다."));
    }
}
