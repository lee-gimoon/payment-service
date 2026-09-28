# 중복 결제 방지

같은 주문에서 승인이 두 번 일어나지 않도록 서버는 다섯 단계로 막는다. 적용 범위는 **같은 주문번호**다. 전체 흐름과 상태는 [아키텍처](architecture.md)를 참고한다.

## 방어 단계

| # | 단계 | 막는 것 | 구현 위치 |
| --- | --- | --- | --- |
| 1 | **`FOR UPDATE` (비관적 잠금)** | 같은 주문의 요청이 동시에 와서 "슬롯이 비었나?"를 함께 확인하는 것. 요청을 한 줄로 세운다. | [OrderRepository](../src/main/java/com/example/payment/order/OrderRepository.java) `@Lock(PESSIMISTIC_WRITE)` |
| 2 | **승인 슬롯 + 상태 규칙** | 승인 중이거나 성공한 승인이 있는데 두 번째 승인을 요청하는 것. `STARTED`가 아닌 시도의 승인과 확정된 결과의 변경도 막는다. | [PurchaseOrder](../src/main/java/com/example/payment/order/PurchaseOrder.java) `claimApproval`, [PaymentAttempt](../src/main/java/com/example/payment/payment/domain/PaymentAttempt.java) `requestApproval` |
| 3 | **멱등 처리** | 같은 요청의 재전송(새로고침, 네트워크 재시도). 같은 결제 키는 토스를 부르지 않고 저장된 결과를 돌려주며, 토스 호출에는 `Idempotency-Key`를 보낸다. | [PaymentPreparationService](../src/main/java/com/example/payment/payment/application/PaymentPreparationService.java), [TossPaymentClient](../src/main/java/com/example/payment/payment/infrastructure/toss/TossPaymentClient.java) |
| 4 | **DB 제약** | 코드에 버그가 있어도 중복이 저장되는 것 | [V2](../src/main/resources/db/migration/V2__merge_payments_into_attempts.sql)·[V3](../src/main/resources/db/migration/V3__add_completed_payments.sql)·[V4](../src/main/resources/db/migration/V4__align_attempt_finished_at.sql) 마이그레이션 |
| 5 | **`@Version` (낙관적 잠금)** | 두 트랜잭션이 같은 행을 동시에 고쳐 한쪽 변경이 덮어써지는 것 | `PurchaseOrder`, `PaymentAttempt`의 `version` 필드 |

**승인 슬롯**은 주문의 `approval_attempt_id` 컬럼이다. 토스에 승인을 요청하기 **직전에** 시도 ID를 넣고, 실패가 확정되면 비우며, 성공하면 그대로 둔다. 결과가 나오기 전에 자리를 먼저 차지하므로 그사이에 들어온 두 번째 요청을 막을 수 있다.

## DB 제약

| 제약 | 막는 것 |
| --- | --- |
| `payment_attempts(order_id)` 부분 유니크 인덱스 (승인 중·결과 불명·성공 상태만 대상) | 한 주문에 살아 있는 승인이 2개 |
| `payment_attempts.payment_key` UNIQUE | 결제 키 하나가 시도 두 개에 연결됨 |
| `payments.order_id` UNIQUE | 한 주문에 결제 기록이 2개 |
| 슬롯 외래 키·CHECK 제약 | 슬롯이 다른 주문의 시도를 가리킴, 주문 상태와 슬롯이 어긋남 |

## 결제 버튼을 연타했을 때

```text
요청 A와 요청 B가 동시에 옴
 ① FOR UPDATE : B는 A가 커밋할 때까지 기다림
 ② 슬롯       : B는 슬롯에 A가 있는 걸 보고 거부됨 (보통 여기서 끝)
 ④ DB 제약    : 코드 버그로 ②가 빠져도 B를 APPROVING으로 저장하는 순간 실패
                → 토스는 APPROVING 커밋 후에만 부르므로 B의 토스 호출도 나가지 않음
 ③ 멱등 처리  : 같은 요청이 다시 오면 저장된 결과를 돌려줌
 ⑤ @Version   : 잠금 없이 같은 행을 바꾸는 경로가 있으면 충돌로 감지
```

## 단계별 역할

- **`FOR UPDATE` + 슬롯**이 평소에 중복 승인을 막는 주 장치다.
- **DB 제약**은 코드가 잘못됐을 때의 마지막 안전망이다. 승인은 저장이 먼저이고 토스 호출이 나중이라, DB가 저장을 거부하면 토스 호출도 일어나지 않는다.
- **멱등 처리**는 동시 요청이 아니라 같은 요청이 **다시 올 때**를 처리한다.
- **`@Version`**은 중복 결제를 직접 막기보다 데이터가 덮어써지는 것을 막는 보조 장치다.

## 시간 차를 두고 생기는 중복

승인 응답이 유실되면 결과를 모르는 채(`UNKNOWN`) 남는다. 이때 실패로 보고 새 결제를 열면, 실제로는 이미 승인된 경우 두 번 결제된다. 그래서 결과를 모르는 동안 슬롯을 유지해 새 결제를 막고, [복구 작업](architecture.md#실패와-복구)이 토스 조회로 성공·실패를 확정한 뒤에만 슬롯을 비운다. 복구 작업은 조회만 하고 승인을 다시 요청하지 않는다.

## 범위와 한계

- 장바구니로 주문을 따로 두 번 만들면 주문마다 슬롯이 따로 있어 서로를 막지 않는다. 주문 생성에는 멱등키가 없다.
- PG 승인과 DB 저장은 한 트랜잭션이 아니므로, DB 제약만으로 외부 청구까지 보장하지는 않는다. 불일치는 복구 작업이 토스 조회로 해소한다.
- 프런트엔드도 처리 중에는 버튼을 비활성화하고 결제창이 두 번 열리지 않게 막는다. 다만 우회할 수 있으므로 안전장치가 아니라 사용자 편의 기능이다.
