import {
  formatAmount,
  formatDateTime,
  paymentStatusLabel
} from "../lib/formatters";
import type { Order } from "../types/payment";

interface OrderResultCardProps {
  order: Order;
  busy: boolean;
  onRefresh: () => void;
  onReconcile: () => void;
}

export function OrderResultCard({
  order,
  busy,
  onRefresh,
  onReconcile
}: OrderResultCardProps) {
  const statusLabel = paymentStatusLabel(order.payment.status);

  return (
    <section className="result-card" aria-live="polite" aria-busy={busy}>
      <div className="result-heading">
        <h2>{statusLabel}</h2>
        <span className="status-badge">{statusLabel}</span>
      </div>

      <p>{order.payment.message}</p>

      <dl className="result-details">
        <div>
          <dt>주문번호</dt>
          <dd>{order.orderId}</dd>
        </div>
        <div>
          <dt>주문 내용</dt>
          <dd>
            {order.productName} {order.quantity}장 · {formatAmount(order.amount)}
          </dd>
        </div>
        <div>
          <dt>승인 시각</dt>
          <dd>{formatDateTime(order.payment.approvedAt)}</dd>
        </div>
        <div>
          <dt>결과 확인 시각</dt>
          <dd>{formatDateTime(order.payment.checkedAt)}</dd>
        </div>
      </dl>

      <div className="actions">
        <button className="secondary" type="button" disabled={busy} onClick={onRefresh}>
          저장된 결과 조회
        </button>
        {order.payment.canReconcile && (
          <button type="button" disabled={busy} onClick={onReconcile}>
            PG 결과 재확인
          </button>
        )}
      </div>
    </section>
  );
}
