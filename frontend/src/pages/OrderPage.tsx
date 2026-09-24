import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { getOrder } from "../api/paymentApi";
import { AppShell } from "../components/AppShell";
import { OrderLookup } from "../components/OrderLookup";
import { OrderResultCard } from "../components/OrderResultCard";
import { readLocalValue, writeLocalValue } from "../lib/storage";
import type { Order } from "../types/payment";

const LAST_ORDER_ID_KEY = "lastOrderId";

export function OrderPage() {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const [lookupOrderId, setLookupOrderId] = useState(orderId ?? readLocalValue(LAST_ORDER_ID_KEY) ?? "");
  const [order, setOrder] = useState<Order | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    document.title = "주문 확인 · MODO CLUB";
    window.scrollTo(0, 0);
    if (!orderId) { setOrder(null); return; }
    let active = true;
    setOrder(null);
    setBusy(true);
    setError("");
    getOrder(orderId).then(result => {
      if (active) {
        setOrder(result);
        setLookupOrderId(result.orderId);
        writeLocalValue(LAST_ORDER_ID_KEY, result.orderId);
      }
    }).catch(requestError => {
      if (active) setError(requestError instanceof Error ? requestError.message : "주문을 조회하지 못했습니다.");
    }).finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [orderId]);

  function handleLookup() {
    const id = lookupOrderId.trim();
    if (id) navigate(`/orders/${encodeURIComponent(id)}`);
  }

  function handleRefresh() {
    if (!order) return;
    setBusy(true);
    setError("");
    getOrder(order.orderId).then(setOrder)
      .catch(requestError => setError(requestError instanceof Error ? requestError.message : "주문을 조회하지 못했습니다."))
      .finally(() => setBusy(false));
  }

  return <AppShell footerText="저장된 주문·결제 결과" mainClassName="result-page">
    <nav className="breadcrumb" aria-label="현재 위치"><Link to="/">스토어</Link><span aria-hidden="true">/</span><span>주문 확인</span></nav>
    <p className="eyebrow">ORDER DETAILS</p>
    <h1>주문 확인하기</h1>
    <p>주문번호로 서버에 저장된 결제 상태를 확인할 수 있습니다.</p>
    <OrderLookup orderId={lookupOrderId} busy={busy} onOrderIdChange={setLookupOrderId} onSubmit={handleLookup} />
    {busy && !order && <p role="status">주문을 불러오고 있습니다.</p>}
    {order && <OrderResultCard order={order} busy={busy} onRefresh={handleRefresh} />}
    {error && <p className="error" role="alert">{error}</p>}
  </AppShell>;
}
