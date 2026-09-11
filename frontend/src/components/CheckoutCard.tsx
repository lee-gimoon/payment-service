import type { Order, PaymentConfig } from "../types/payment";
import { formatAmount } from "../lib/formatters";

interface CheckoutCardProps {
  order: Order | null;
  paymentConfig: PaymentConfig | null;
  busy: boolean;
  onCreateOrder: () => void;
  onPay: () => void;
}

const IN_PROGRESS_STATUSES = ["READY", "PROCESSING", "UNKNOWN"];

export function CheckoutCard({
  order,
  paymentConfig,
  busy,
  onCreateOrder,
  onPay
}: CheckoutCardProps) {
  const showCreateOrder =
    !order || !IN_PROGRESS_STATUSES.includes(order.payment.status);
  const showPaymentButton = order?.payment.status === "READY";
  const paymentAmount = formatAmount(order?.amount ?? 10_000);

  const configMessage = paymentConfig
    ? paymentConfig.enabled
      ? "테스트 카드 결제를 사용할 수 있습니다."
      : "테스트 결제 준비 중입니다. 주문 생성과 결과 조회는 사용할 수 있습니다."
    : "결제 설정을 확인하고 있습니다.";

  return (
    <section className="checkout-card" aria-labelledby="checkout-title">
      <p className="eyebrow">YOUR ORDER</p>
      <h2 id="checkout-title">주문하기</h2>

      <dl className="summary">
        <div>
          <dt>상품</dt>
          <dd>티셔츠</dd>
        </div>
        <div>
          <dt>수량</dt>
          <dd>1장</dd>
        </div>
        <div className="total">
          <dt>결제 금액</dt>
          <dd>{paymentAmount}</dd>
        </div>
      </dl>

      <p className="subtle">배송비와 할인은 적용하지 않습니다.</p>

      {showCreateOrder && (
        <button type="button" disabled={busy} onClick={onCreateOrder}>
          {order ? "새 주문 만들기" : "주문 만들기"}
          <span aria-hidden="true">↗</span>
        </button>
      )}

      {showPaymentButton && (
        <button
          type="button"
          disabled={busy || !paymentConfig?.enabled}
          onClick={onPay}
        >
          {paymentAmount} 테스트 결제
        </button>
      )}

      <p className="notice" role="status">
        {configMessage}
      </p>
      <p className="subtle">
        토스페이먼츠 테스트 환경으로 연결됩니다.
        <br />
        테스트 키로 진행하며 실제 금액은 청구되지 않습니다.
      </p>
    </section>
  );
}
