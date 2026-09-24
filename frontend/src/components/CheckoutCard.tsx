import type { CartItem, Order, PaymentConfig, Product, ShirtSize } from "../types/payment";
import { formatAmount, paymentStatusLabel } from "../lib/formatters";

interface CheckoutCardProps {
  cart: CartItem[];
  products: Product[];
  order: Order | null;
  paymentConfig: PaymentConfig | null;
  busy: boolean;
  onQuantityChange: (productId: string, size: ShirtSize, delta: number) => void;
  onRemove: (productId: string, size: ShirtSize) => void;
  onCreateOrder: () => void;
  onPay: () => void;
}

const FINISHED = new Set(["SUCCEEDED", "FAILED", "CANCELED"]);

export function CheckoutCard({ cart, products, order, paymentConfig, busy, onQuantityChange, onRemove, onCreateOrder, onPay }: CheckoutCardProps) {
  const productById = new Map(products.map(product => [product.id, product]));
  const total = cart.reduce((sum, item) => sum + (productById.get(item.productId)?.price ?? 0) * item.quantity, 0);
  const count = cart.reduce((sum, item) => sum + item.quantity, 0);
  const canCreate = cart.length > 0 && (!order || FINISHED.has(order.payment.status) || order.payment.status === "READY");

  return <section id="cart" className="checkout-card" aria-labelledby="cart-title">
    <div className="section-heading compact">
      <div><p className="eyebrow">YOUR CART</p><h2 id="cart-title">장바구니 <span>{count}</span></h2></div>
    </div>

    {cart.length === 0 ? <p className="cart-empty">마음에 드는 티셔츠를 골라 아바타에 입혀보고 장바구니에 담아보세요.</p> :
      <ul className="cart-lines">
        {cart.map(item => {
          const product = productById.get(item.productId);
          if (!product) return null;
          return <li key={`${item.productId}-${item.size}`}>
            <span className="cart-swatch" style={{ backgroundColor: product.stage }} aria-hidden="true"><span style={{ backgroundColor: product.color }} /></span>
            <div className="cart-line-info"><strong>{product.name}</strong><small>{item.size} · {formatAmount(product.price)}</small>
              <button className="text-button" type="button" onClick={() => onRemove(item.productId, item.size)} disabled={busy}>삭제</button>
            </div>
            <div className="quantity-control" aria-label={`${product.name} ${item.size} 수량`}>
              <button type="button" aria-label="수량 줄이기" onClick={() => onQuantityChange(item.productId, item.size, -1)} disabled={busy}>−</button>
              <span>{item.quantity}</span>
              <button type="button" aria-label="수량 늘리기" onClick={() => onQuantityChange(item.productId, item.size, 1)} disabled={busy || item.quantity >= 10}>+</button>
            </div>
          </li>;
        })}
      </ul>}

    <dl className="cart-summary">
      <div><dt>상품 {count}장</dt><dd>{formatAmount(total)}</dd></div>
      <div><dt>배송비</dt><dd>0원</dd></div>
      <div className="total"><dt>예상 결제 금액</dt><dd>{formatAmount(total)}</dd></div>
    </dl>
    <p className="subtle">주문 금액은 서버가 상품 가격으로 다시 계산합니다.</p>
    {canCreate && <button className="primary-button wide" type="button" disabled={busy} onClick={onCreateOrder}>주문 만들기 <span aria-hidden="true">↗</span></button>}
    {order && <div className="pending-order">
      <p className="eyebrow">SAVED ORDER</p>
      <strong>{paymentStatusLabel(order.payment.status)}</strong>
      <p>{order.productName} · {order.quantity}장</p>
      <p>서버 확정 금액 <b>{formatAmount(order.amount)}</b></p>
      <small>주문번호 {order.orderId}</small>
    </div>}
    {order?.payment.status === "READY" && <button className="primary-button wide" type="button" disabled={busy || !paymentConfig?.enabled} onClick={onPay}>{formatAmount(order.amount)} 테스트 결제</button>}
    {order && !FINISHED.has(order.payment.status) && order.payment.status !== "READY" && <p className="notice">기존 주문의 결과를 먼저 확인해주세요. 결과가 불확실하면 새 결제를 진행하지 마세요.</p>}
    {cart.length > 0 && order?.payment.status === "READY" && <p className="notice">새 주문을 만들면 현재 결제 대기 주문 대신 표시됩니다. 이전 주문은 주문번호로 다시 조회할 수 있습니다.</p>}
    <p className="notice" role="status">{paymentConfig?.enabled ? "테스트 카드·간편결제를 사용할 수 있습니다." : "테스트 결제 키가 없으면 상품 선택과 주문 생성·조회만 사용할 수 있습니다."}</p>
    <p className="subtle">토스페이먼츠 테스트 환경입니다. 실제 금액은 청구되지 않습니다.</p>
  </section>;
}
