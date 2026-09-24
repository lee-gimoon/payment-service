/** 파일 역할: 서버에 저장된 주문·결제·취소 결과를 보여준다. */
import {
  formatAmount,
  formatDateTime,
  paymentStatusLabel
} from "../lib/formatters";
import type { Order } from "../types/payment";

/** 표시할 주문과 작업 여부, 저장된 주문을 새로 읽는 콜백이다. */
interface OrderResultCardProps {
  order: Order;
  busy: boolean;
  onRefresh: () => void;
}

/** 주문과 승인·취소 내역을 표시한다. */
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
          <dt>취소 시각</dt>
          <dd>{formatDateTime(order.payment.canceledAt)}</dd>
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
