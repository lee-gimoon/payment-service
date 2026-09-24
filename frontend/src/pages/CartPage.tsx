import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { createOrder, getOrder, getPaymentConfig, recordAuthenticationResult, startPaymentAttempt } from "../api/paymentApi";
import { AppShell } from "../components/AppShell";
import { TeeArtwork } from "../components/TeeArtwork";
import { formatAmount, paymentStatusLabel } from "../lib/formatters";
import { useShop } from "../lib/shop";
import { readLocalValue, writeLocalValue } from "../lib/storage";
import { openTossPayment } from "../payments/tossPayments";
import type { Order, PaymentConfig } from "../types/payment";

const PENDING_ORDER_ID_KEY = "pendingOrderId";
const LAST_ORDER_ID_KEY = "lastOrderId";

export function CartPage() {
  const { products, loading, catalogError, cart, cartCount, changeQuantity, removeFromCart, clearCart } = useShop();
  const [paymentConfig, setPaymentConfig] = useState<PaymentConfig | null>(null);
  const [pendingOrder, setPendingOrder] = useState<Order | null>(null);
  const [pendingLoading, setPendingLoading] = useState(Boolean(readLocalValue(PENDING_ORDER_ID_KEY)));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const busyRef = useRef(false);
  const abortRef = useRef(new AbortController());
  const productById = new Map(products.map(product => [product.id, product]));
  const total = cart.reduce((sum, item) => sum + (productById.get(item.productId)?.price ?? 0) * item.quantity, 0);
  const showPending = cart.length === 0 && pendingOrder !== null;

  useEffect(() => {
    let active = true;
    document.title = "장바구니 · MODO CLUB";
    window.scrollTo(0, 0);
    getPaymentConfig().then(config => { if (active) setPaymentConfig(config); })
      .catch(() => { if (active) setPaymentConfig({ enabled: false, clientKey: "", paymentMethodVariantKey: "", agreementVariantKey: "" }); });
    const savedId = readLocalValue(PENDING_ORDER_ID_KEY);
    if (savedId) {
      getOrder(savedId).then(order => { if (active) setPendingOrder(order); })
        .catch(() => { if (active) setPendingOrder(null); })
        .finally(() => { if (active) setPendingLoading(false); });
    }
    return () => { active = false; abortRef.current.abort(); };
  }, []);

  async function handlePay() {
    if (busyRef.current || !paymentConfig?.enabled || (cart.length === 0 && !pendingOrder) || (cart.length > 0 && (catalogError || cart.some(item => !productById.has(item.productId))))) return;
    busyRef.current = true;
    setBusy(true);
    setError("");
    try {
      let order: Order;
      if (cart.length > 0) {
        order = await createOrder(cart);
        writeLocalValue(PENDING_ORDER_ID_KEY, order.orderId);
        writeLocalValue(LAST_ORDER_ID_KEY, order.orderId);
        setPendingOrder(order);
        clearCart();
      } else {
        order = await getOrder((pendingOrder as Order).orderId);
        setPendingOrder(order);
      }
      if (abortRef.current.signal.aborted) return;
      if (order.status !== "PENDING_PAYMENT" || !["READY", "FAILED"].includes(order.payment.status)) {
        setError("이 주문은 결제 대기 상태가 아닙니다. 저장된 주문 상태를 확인해주세요.");
        return;
      }
      const attempt = await startPaymentAttempt(order.orderId);
      await openTossPayment(order, paymentConfig, abortRef.current.signal, attempt.id,
        async () => { await recordAuthenticationResult(attempt.id, "AUTH_CANCELED", "WINDOW_CLOSED"); });
      if (!abortRef.current.signal.aborted) setPendingOrder(await getOrder(order.orderId));
    } catch (actionError) {
      if (!abortRef.current.signal.aborted) {
        setError(actionError instanceof Error ? actionError.message : "결제창을 열지 못했습니다. 주문 상태를 확인해주세요.");
      }
    } finally {
      if (!abortRef.current.signal.aborted) {
        busyRef.current = false;
        setBusy(false);
      }
    }
  }

  return <AppShell footerText="장바구니 · 토스페이먼츠 테스트 결제" mainClassName="cart-page">
    <nav className="breadcrumb" aria-label="현재 위치"><Link to="/">스토어</Link><span aria-hidden="true">/</span><span>장바구니</span></nav>
    <div className="page-intro"><p className="eyebrow">YOUR CART</p><h1>장바구니 <span>{cartCount}</span></h1><p>선택한 상품을 확인하고 결제를 진행하세요.</p></div>

    {pendingLoading && cart.length === 0 ? <p role="status">결제 대기 주문을 확인하고 있습니다.</p> :
    cart.length === 0 && !showPending ? <div className="empty-state"><h2>장바구니가 비어 있습니다.</h2><p>마음에 드는 티셔츠를 골라 아바타에 입혀보세요.</p><Link className="primary-button" to="/#products">상품 보러 가기</Link></div> :
      <div className="cart-layout">
        <section className="checkout-card" aria-label={showPending ? "결제 대기 주문" : "담은 상품"}>
          {showPending ? <>
            <p className="eyebrow">SAVED ORDER</p>
            <h2>{paymentStatusLabel(pendingOrder.payment.status)}</h2>
            <p>{pendingOrder.productName} · {pendingOrder.quantity}장</p>
            <p className="subtle">주문번호 {pendingOrder.orderId}</p>
            {pendingOrder.latestAttempt?.status === "AUTH_CANCELED" && <p>이전 결제창을 닫았습니다. 다시 결제할 수 있습니다.</p>}
            {pendingOrder.payment.status !== "READY" && <Link className="back-link" to={`/orders/${pendingOrder.orderId}`}>주문 상태 확인하기 →</Link>}
          </> : <>
            <h2>담은 상품</h2>
            <ul className="cart-lines">{cart.map(item => {
              const product = productById.get(item.productId);
              if (!product) return null;
              return <li key={`${item.productId}-${item.size}`}>
                <Link className="cart-swatch" to={`/products/${product.id}`} style={{ backgroundColor: product.stage }} aria-label={`${product.name} 상품 보기`}><TeeArtwork product={product} /></Link>
                <div className="cart-line-info"><Link to={`/products/${product.id}`}><strong>{product.name}</strong></Link><small>사이즈 {item.size} · {formatAmount(product.price)}</small>
                  <button className="text-button" type="button" onClick={() => removeFromCart(item.productId, item.size)} disabled={busy}>삭제</button>
                </div>
                <div className="quantity-control" aria-label={`${product.name} ${item.size} 수량`}>
                  <button type="button" aria-label="수량 줄이기" onClick={() => changeQuantity(item.productId, item.size, -1)} disabled={busy}>−</button>
                  <span>{item.quantity}</span>
                  <button type="button" aria-label="수량 늘리기" onClick={() => changeQuantity(item.productId, item.size, 1)} disabled={busy || item.quantity >= 10}>+</button>
                </div>
              </li>;
            })}</ul>
            <Link className="back-link" to="/#products">← 쇼핑 계속하기</Link>
          </>}
        </section>

        <aside className="checkout-card cart-payment" aria-label="결제 금액">
          <h2>결제 금액</h2>
          <dl className="cart-summary">
            <div><dt>상품 {showPending ? pendingOrder.quantity : cartCount}장</dt><dd>{formatAmount(showPending ? pendingOrder.amount : total)}</dd></div>
            <div><dt>배송비</dt><dd>0원</dd></div>
            <div className="total"><dt>총 결제 금액</dt><dd>{formatAmount(showPending ? pendingOrder.amount : total)}</dd></div>
          </dl>
          <button className="primary-button wide" type="button" onClick={handlePay} disabled={busy || loading || Boolean(catalogError) || cart.some(item => !productById.has(item.productId)) || !paymentConfig?.enabled || (showPending && (pendingOrder.status !== "PENDING_PAYMENT" || !["READY", "FAILED"].includes(pendingOrder.payment.status)))}>
            {busy ? "처리 중…" : "결제하기"}
          </button>
          <p className="notice">{paymentConfig === null ? "결제 설정을 확인하고 있습니다." : paymentConfig.enabled ? "결제 시 서버가 상품 가격으로 주문 금액을 확정하고 토스 결제창을 엽니다." : "테스트 결제 키가 없어 결제창을 열 수 없습니다."}</p>
          <p className="subtle">토스페이먼츠 테스트 환경이며 실제 금액은 청구되지 않습니다.</p>
        </aside>
      </div>}
    {error && <p className="error" role="alert">{error}</p>}
    {catalogError && cart.length > 0 && <p className="error" role="alert">상품 가격을 불러오지 못했습니다. 스토어에서 상품을 다시 불러와주세요.</p>}
  </AppShell>;
}
