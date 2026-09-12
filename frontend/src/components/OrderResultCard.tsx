/** 파일 역할: 스토어에서 주문의 결제 결과를 보여주고 저장 결과 조회·PG 재확인 버튼을 제공한다. */
import {
  formatAmount,
  formatDateTime,
  paymentStatusLabel
} from "../lib/formatters";
import type { Order } from "../types/payment";

/** 표시할 주문과 작업 여부, 부모 화면이 실행할 두 종류의 조회 콜백이다. */
interface OrderResultCardProps {
  order: Order;
  busy: boolean;
  onRefresh: () => void;
  onReconcile: () => void;
}

/** 주문 내용·결제 상태·처리 시각을 표시하고, 서버가 허용한 경우 PG 결과 재확인 버튼을 보여준다. */
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
