# 상품·주문·결제 도메인

현재 프로젝트는 티셔츠 스토어의 **상품 선택 → 주문 항목 기록 → 테스트 결제**를 구현한다. 회원, 배송, 재고 예약·차감, 관리자 상품 수정 기능은 아직 없다.

```mermaid
erDiagram
    direction TB
    products ||..o{ purchase_order_items : "주문 항목에 담긴다"
    purchase_order_items }o..|| purchase_orders : "주문에 속한다"
    purchase_orders ||--o| payments : "결제로 이어진다"

    products {
        string id PK
        string name
        long price
        boolean active
    }
    purchase_order_items {
        string id PK
        string product_id FK
        string order_id FK
        string size
        int quantity
        long unit_price
    }
    purchase_orders {
        string id PK
        int quantity
        long amount
    }
    payments {
        string order_id PK,FK
        long amount
        string status
    }
```

<img src="shop-order-tables.png" alt="상품 5종, 주문 2건, 주문 항목 5행의 예시" width="700">

## 왜 주문 안에 `List<OrderItem>`이 있나요?

위 예시에서 `purchase_orders`의 `O-100`은 한 행이고, `purchase_order_items`에는 `order_id = O-100`인 두 행이 있다. `O-200`에는 세 행이 연결된다. **외래 키는 항목 행 각각에 하나씩** 들어 있다. Java에서 같은 주문의 항목들을 목록으로 보는 것이 `PurchaseOrder.items`다. `@OneToMany(mappedBy = "order")`의 `order`는 `OrderItem.order` 필드를 가리킨다.

반대로 `Product`에는 모든 구입 내역을 담는 `List<OrderItem>`을 두지 않았다. 한 상품의 과거 주문이 계속 늘어나므로 상품을 조회할 때 전체 구입 내역을 읽을 이유가 없기 때문이다.

주문 생성 시에만 항목을 함께 저장한다. 결제 기록인 항목을 목록에서 제거했다는 이유로 삭제하지 않는다. 평소에는 항목을 지연 로딩하고, 주문 응답을 만들 때만 항목과 상품을 함께 조회한다.

## 현재 상품과 구입 당시 값

`OrderItem.product_id`는 `products.id`를 참조한다. 상품 가격·이름이 바뀌어도 과거 주문의 `OrderItem.unit_price`와 `product_name`은 그대로다. 상품을 판매 중지하려면 `products.active = false`로 표시한다. 과거 주문이 참조하는 상품 행을 삭제하는 것은 DB 외래 키가 막는다.

`PurchaseOrder`는 항목의 수량과 단가를 합산해 주문 총수량·금액을 만든다. React가 보낸 가격은 사용하지 않는다. `payments.order_id`도 `purchase_orders.id`를 참조한다. 결제 API가 주문번호로 결제를 다루므로 `Payment`에는 주문 ID만 두고 JPA 양방향 연관관계는 만들지 않았다.

## 운영 범위

이 구조는 **결제 학습용 쇼핑몰의 상품·주문·결제 기반**이다. 실제 판매를 시작하려면 사이즈별 SKU와 재고 예약·차감·해제, 주문 만료, 구매자 인증과 주문 접근 권한, 배송·반품·환불, 관리자 상품 관리, 결제 미확정 건의 운영 대조가 추가로 필요하다. 재고 수치만 넣고 결제 확정 전후 처리 없이 판매 가능하다고 표시하지 않는다.
