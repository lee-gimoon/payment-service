import {
  formatAmount,
  formatDateTime,
  paymentAttemptStatusLabel,
  paymentStatusLabel
} from "../lib/formatters";
import type { Order } from "../types/payment";
interface OrderResultCardProps {
  order: Order;
  busy: boolean;
  onRefresh: () => void;
}
export function OrderResultCard({
  order,
  busy,
  onRefresh
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
          <dt>주문 상태</dt>
          <dd>{order.status === "CONFIRMED" ? "주문 확정" : "결제 대기"}</dd>
        </div>
        {order.latestAttempt && <div>
          <dt>최근 결제 시도</dt>
          <dd>{paymentAttemptStatusLabel(order.latestAttempt.status)} · {formatDateTime(order.latestAttempt.startedAt ?? null)}</dd>
        </div>}
        <div>
          <dt>주문 내용</dt>
          <dd>
            {order.productName} {order.quantity}장 · {formatAmount(order.amount)}
          </dd>
        </div>
        {order.items?.map((item, index) => <div key={`${item.productId}-${item.size}-${index}`}>
          <dt>상품 {index + 1}</dt>
          <dd>{item.productName} · {item.size} · {item.quantity}장 · {formatAmount(item.unitPrice * item.quantity)}</dd>
        </div>)}
        <div>
          <dt>승인 시각</dt>
          <dd>{formatDateTime(order.payment.approvedAt)}</dd>
        </div>
        <div>
          <dt>실제 승인 금액</dt>
          <dd>{order.payment.paidAmount == null ? "—" : `${order.payment.paidAmount.toLocaleString("ko-KR")} ${order.payment.paidCurrency}`}</dd>
        </div>
        <div>
          <dt>결과 확인 시각</dt>
          <dd>{formatDateTime(order.payment.checkedAt)}</dd>
        </div>
        {order.payment.errorCode && (
          <div>
            <dt>오류 코드</dt>
            <dd>{order.payment.errorCode}</dd>
          </div>
        )}
      </dl>

      <div className="actions">
        <button className="secondary" type="button" disabled={busy} onClick={onRefresh}>
          주문 내역 새로고침
        </button>
      </div>
    </section>
  );
}
