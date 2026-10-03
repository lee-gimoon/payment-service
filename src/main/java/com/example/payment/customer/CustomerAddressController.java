package com.example.payment.customer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 마이페이지 배송지. /me/** 경로라 쇼핑몰 관리자가 아닌 회원만 호출할 수 있다. */
@RestController
@Tag(name = "My page")
public class CustomerAddressController {
    private final CustomerAddressService addresses;

    public CustomerAddressController(CustomerAddressService addresses) {
        this.addresses = addresses;
    }

    @GetMapping("/me/addresses")
    @Operation(summary = "내 배송지 목록")
    public List<AddressResponse> list(@AuthenticationPrincipal Jwt customer) {
        return addresses.list(customer.getSubject());
    }

    @PostMapping("/me/addresses")
    @Operation(summary = "배송지 추가")
    public ResponseEntity<AddressResponse> create(@AuthenticationPrincipal Jwt customer,
                                                  @Valid @RequestBody AddressRequest request) {
        AddressResponse created = addresses.create(customer.getSubject(), request);
        return ResponseEntity.created(URI.create("/me/addresses/" + created.id())).body(created);
    }

    @PutMapping("/me/addresses/{addressId}")
    @Operation(summary = "배송지 수정")
    public AddressResponse update(@AuthenticationPrincipal Jwt customer, @PathVariable String addressId,
                                  @Valid @RequestBody AddressRequest request) {
        return addresses.update(customer.getSubject(), addressId, request);
    }

    @PutMapping("/me/addresses/{addressId}/default")
    @Operation(summary = "기본 배송지 지정")
    public AddressResponse makeDefault(@AuthenticationPrincipal Jwt customer, @PathVariable String addressId) {
        return addresses.makeDefault(customer.getSubject(), addressId);
    }

    @DeleteMapping("/me/addresses/{addressId}")
    @Operation(summary = "배송지 삭제")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt customer, @PathVariable String addressId) {
        addresses.delete(customer.getSubject(), addressId);
        return ResponseEntity.noContent().build();
    }
}
