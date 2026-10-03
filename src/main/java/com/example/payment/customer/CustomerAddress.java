package com.example.payment.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** 회원이 마이페이지에서 관리하는 배송지. 주문은 고른 배송지를 복사해 두므로 여기서 바꿔도 지난 주문은 그대로다. */
@Entity
@Table(name = "customer_addresses")
public class CustomerAddress {
    @Id
    @Column(length = 36)
    private String id;

    // Keycloak 회원 ID(access token의 sub). 주문과 같은 기준으로 주인을 구분한다.
    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(nullable = false, length = 20)
    private String label;

    @Column(name = "recipient_name", nullable = false, length = 50)
    private String recipientName;

    @Column(nullable = false, length = 11)
    private String phone;

    @Column(name = "postal_code", nullable = false, length = 5)
    private String postalCode;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(name = "address_detail", nullable = false, length = 100)
    private String addressDetail;

    // 회원당 하나뿐이다. DB 부분 유니크 인덱스가 두 번째 방어선이다.
    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected CustomerAddress() {}

    CustomerAddress(String customerId, AddressRequest request, boolean defaultAddress) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("배송지 주인을 확인해주세요.");
        }
        this.id = UUID.randomUUID().toString();
        this.customerId = customerId;
        this.createdAt = Instant.now();
        this.defaultAddress = defaultAddress;
        update(request);
    }

    void update(AddressRequest request) {
        label = request.label().strip();
        recipientName = request.recipientName().strip();
        phone = request.phone().replace("-", "");
        postalCode = request.postalCode();
        address = request.address().strip();
        addressDetail = request.addressDetail() == null ? "" : request.addressDetail().strip();
        updatedAt = Instant.now();
    }

    void markDefault() { defaultAddress = true; }
    void unmarkDefault() { defaultAddress = false; }

    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getLabel() { return label; }
    public String getRecipientName() { return recipientName; }
    public String getPhone() { return phone; }
    public String getPostalCode() { return postalCode; }
    public String getAddress() { return address; }
    public String getAddressDetail() { return addressDetail; }
    public boolean isDefaultAddress() { return defaultAddress; }
}
