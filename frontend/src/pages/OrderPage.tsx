import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { getOrder } from "../api/paymentApi";
import { useAuth } from "../auth/auth";
import { AppShell } from "../components/AppShell";
import { OrderLookup } from "../components/OrderLookup";
import { OrderResultCard } from "../components/OrderResultCard";
import { getLastOrderId, saveLastOrderId } from "../lib/orderStorage";
import type { Order } from "../types/payment";

export function OrderPage() {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const { status: authStatus, customer, login } = useAuth();
  const customerId = authStatus === "signedIn" ? customer?.id ?? null : null;
  const [lookupOrderId, setLookupOrderId] = useState(orderId ?? "");
  const [loadedOrder, setLoadedOrder] = useState<{ customerId: string; order: Order } | null>(null);
  const order = loadedOrder?.customerId === customerId && loadedOrder.order.orderId === orderId ? loadedOrder.order : null;
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    document.title = "주문 확인 · MODO CLUB";
    window.scrollTo(0, 0);
    setLookupOrderId(orderId ?? getLastOrderId(customerId) ?? "");
    setError("");
    setBusy(false);
    if (!orderId || !customerId) { setLoadedOrder(null); return; }
    let active = true;
    setBusy(true);
    getOrder(orderId).then(result => {
      if (active) {
        setLoadedOrder({ customerId, order: result });
        setLookupOrderId(result.orderId);
        saveLastOrderId(customerId, result.orderId);
      }
    }).catch(requestError => {
      if (active) setError(requestError instanceof Error ? requestError.message : "주문을 조회하지 못했습니다.");
    }).finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [orderId, customerId, refreshVersion]);

  function handleLookup() {
    const id = lookupOrderId.trim();
    if (id) navigate(`/orders/${encodeURIComponent(id)}`);
  }

  function handleRefresh() {
    if (!order) return;
    setRefreshVersion(value => value + 1);
  }

  return <AppShell footerText="저장된 주문·결제 결과" mainClassName="result-page">
    <nav className="breadcrumb" aria-label="현재 위치"><Link to="/">스토어</Link><span aria-hidden="true">/</span><span>주문 확인</span></nav>
    <p className="eyebrow">ORDER DETAILS</p>
    <h1>주문 확인하기</h1>
    <p>내 계정으로 주문한 주문번호로 서버에 저장된 결제 상태를 확인할 수 있습니다.</p>
    {authStatus === "checking" && <p role="status">로그인 상태를 확인하고 있습니다.</p>}
    {authStatus === "signedOut" && <section className="lookup-card" aria-labelledby="sign-in-title">
      <div>
        <p className="eyebrow">SIGN IN</p>
        <h2 id="sign-in-title">로그인이 필요합니다</h2>
        <p className="subtle">주문은 주문한 회원만 확인할 수 있습니다.</p>
      </div>
      <button className="primary-button" type="button" onClick={() => login()}>로그인하고 주문 확인하기</button>
    </section>}
    {authStatus === "signedIn" && <OrderLookup orderId={lookupOrderId} busy={busy} onOrderIdChange={setLookupOrderId} onSubmit={handleLookup} />}
    {busy && !order && <p role="status">주문을 불러오고 있습니다.</p>}
    {order && <OrderResultCard order={order} busy={busy} onRefresh={handleRefresh} />}
    {error && <p className="error" role="alert">{error}</p>}
  </AppShell>;
}
