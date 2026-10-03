import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { getAdminOrder, getAdminOrderCounts, getAdminOrders, markOrderDelivered, shipOrder } from "../api/adminOrderApi";
import { AdminGate } from "../components/AdminGate";
import { AppShell } from "../components/AppShell";
import { formatPhone } from "../lib/addresses";
import { CARRIER_CODES, CARRIERS, deliveryStatusLabel, normalizeTrackingNumber, TRACKING_NUMBER_PATTERN } from "../lib/delivery";
import { formatAmount, formatDateTime, orderStatusLabel } from "../lib/formatters";
import type { AdminOrder, AdminOrderCounts, AdminOrderSummary } from "../types/admin";
import type { Carrier, DeliveryStatus } from "../types/payment";

const FILTERS: { value: DeliveryStatus | null; label: string }[] = [
  { value: null, label: "전체" },
  { value: "PREPARING", label: "상품 준비 중" },
  { value: "SHIPPED", label: "배송 중" },
  { value: "DELIVERED", label: "배송 완료" }
];

function readFilter(value: string | null): DeliveryStatus | null {
  return value === "PREPARING" || value === "SHIPPED" || value === "DELIVERED" ? value : null;
}

function errorText(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback;
}

// 결제 완료 주문에 송장을 등록하고 배송 완료로 바꾸는 관리자 화면. 상담 관리처럼 목록과 상세를 나란히 둔다.
export function AdminOrdersPage() {
  useEffect(() => {
    document.title = "주문 관리 · MODO CLUB";
  }, []);

  return <AppShell footerText="결제 완료 주문의 배송 처리" mainClassName="admin-page">
    <nav className="breadcrumb" aria-label="현재 위치">
      <Link to="/">스토어</Link><span aria-hidden="true">/</span><Link to="/admin">관리자 홈</Link><span aria-hidden="true">/</span><span>주문 관리</span>
    </nav>
    <p className="eyebrow">ORDERS</p>
    <h1 className="admin-title">주문 관리</h1>
    <AdminGate><AdminOrdersConsole /></AdminGate>
  </AppShell>;
}

function AdminOrdersConsole() {
  const { orderId } = useParams();
  const [params, setParams] = useSearchParams();
  const filter = readFilter(params.get("delivery"));
  const query = filter ? `?delivery=${filter}` : "";
  const [orders, setOrders] = useState<AdminOrderSummary[] | null>(null);
  const [counts, setCounts] = useState<AdminOrderCounts | null>(null);
  const [error, setError] = useState("");
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let active = true;
    setError("");
    Promise.all([getAdminOrders(filter), getAdminOrderCounts()])
      .then(([list, nextCounts]) => {
        if (!active) return;
        setOrders(list);
        setCounts(nextCounts);
      })
      .catch(requestError => { if (active) setError(errorText(requestError, "주문 목록을 불러오지 못했습니다.")); });
    return () => { active = false; };
  }, [filter, version]);

  const reload = useCallback(() => setVersion(value => value + 1), []);

  function chooseFilter(value: DeliveryStatus | null) {
    setOrders(null);
    setParams(value ? { delivery: value } : {});
  }

  function filterCount(value: DeliveryStatus | null): string {
    if (!counts) return "";
    if (value === "PREPARING") return ` ${counts.preparing}`;
    if (value === "SHIPPED") return ` ${counts.shipping}`;
    return "";
  }

  return <div className={`admin-orders${orderId ? " has-selection" : ""}`}>
    <section className="admin-panel admin-order-list" aria-labelledby="admin-orders-title">
      <div className="admin-panel-header">
        <h2 id="admin-orders-title">결제 완료 주문</h2>
        <small>최대 100건</small>
      </div>
      <div className="chat-filter pill-filter" role="group" aria-label="배송 단계로 보기">
        {FILTERS.map(option => <button key={option.label} type="button" aria-pressed={filter === option.value}
          onClick={() => chooseFilter(option.value)}>{option.label}{filterCount(option.value)}</button>)}
      </div>
      {error && <p className="error admin-empty" role="alert">{error}</p>}
      {!error && orders === null && <p className="admin-empty" role="status">주문 목록을 불러오고 있습니다.</p>}
      {orders?.length === 0 && <p className="admin-empty">{filter === "PREPARING" ? "송장을 기다리는 주문이 없습니다." : "해당하는 주문이 없습니다."}</p>}
      {orders && orders.length > 0 && <ul className="admin-order-rows">{orders.map(order => <li key={order.orderId}>
        <Link className={`admin-order-row${order.orderId === orderId ? " selected" : ""}`}
          to={`/admin/orders/${encodeURIComponent(order.orderId)}${query}`}
          aria-current={order.orderId === orderId ? "true" : undefined}>
          <span className="admin-order-row-top">
            <strong>{order.productName}</strong>
            {order.delivery && <span className="status-badge">{deliveryStatusLabel(order.delivery.status)}</span>}
          </span>
          <small>{order.recipientName ?? "배송지 없음"} · {order.quantity}장 · {formatAmount(order.amount)}</small>
          <small>결제 {formatDateTime(order.paidAt)}</small>
        </Link>
      </li>)}</ul>}
    </section>
    <section className="admin-panel admin-order-detail" aria-labelledby="admin-order-title">
      {orderId
        ? <AdminOrderDetail key={orderId} orderId={orderId} backTo={`/admin/orders${query}`} onChanged={reload} />
        : <>
          <h2 className="visually-hidden" id="admin-order-title">주문 상세</h2>
          <p className="admin-empty">왼쪽 목록에서 주문을 고르면 상품과 배송지를 보고 송장을 등록할 수 있습니다.</p>
        </>}
    </section>
  </div>;
}

interface AdminOrderDetailProps {
  orderId: string;
  backTo: string;
  onChanged: () => void;
}

function AdminOrderDetail({ orderId, backTo, onChanged }: AdminOrderDetailProps) {
  const [order, setOrder] = useState<AdminOrder | null>(null);
  const [loadError, setLoadError] = useState("");
  const [carrier, setCarrier] = useState<Carrier>("CJ");
  const [trackingNumber, setTrackingNumber] = useState("");
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState("");

  useEffect(() => {
    let active = true;
    getAdminOrder(orderId).then(result => {
      if (!active) return;
      setOrder(result);
      if (result.delivery?.carrier) setCarrier(result.delivery.carrier);
      setTrackingNumber(result.delivery?.trackingNumber ?? "");
    }).catch(requestError => { if (active) setLoadError(errorText(requestError, "주문을 불러오지 못했습니다.")); });
    return () => { active = false; };
  }, [orderId]);

  async function run(work: () => Promise<AdminOrder>) {
    setBusy(true);
    setActionError("");
    try {
      setOrder(await work());
      onChanged();
    } catch (requestError) {
      setActionError(errorText(requestError, "배송 상태를 바꾸지 못했습니다."));
    } finally {
      setBusy(false);
    }
  }

  function handleShip(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!busy) void run(() => shipOrder(orderId, carrier, trackingNumber));
  }

  function handleDelivered() {
    if (busy || !window.confirm("배송 완료로 바꾸면 송장을 더 고칠 수 없습니다. 배송 완료로 처리할까요?")) return;
    void run(() => markOrderDelivered(orderId));
  }

  if (loadError) return <><Link className="chat-back" to={backTo}>← 목록으로</Link><p className="error" role="alert">{loadError}</p></>;
  if (!order) return <p className="admin-empty" role="status">주문을 불러오고 있습니다.</p>;

  const delivery = order.delivery;
  const shippable = order.status === "PAID" && order.shipping !== null && delivery?.status !== "DELIVERED";

  return <>
    <Link className="chat-back" to={backTo}>← 목록으로</Link>
    <div className="result-heading">
      <h2 id="admin-order-title">주문 상세</h2>
      <span className="status-badge">{delivery ? deliveryStatusLabel(delivery.status) : orderStatusLabel(order.status)}</span>
    </div>
    <dl className="result-details">
      <div><dt>주문번호</dt><dd>{order.orderId}</dd></div>
      <div><dt>결제 시각</dt><dd>{formatDateTime(order.paidAt)}</dd></div>
      <div><dt>결제 금액</dt><dd>{formatAmount(order.amount)}</dd></div>
      {order.items.map((item, index) => <div key={`${item.productId}-${item.size}`}>
        <dt>상품 {index + 1}</dt>
        <dd>{item.productName} · {item.size} · {item.quantity}장</dd>
      </div>)}
      {order.shipping ? <>
        <div><dt>받는 분</dt><dd>{order.shipping.recipientName} · {formatPhone(order.shipping.phone)}</dd></div>
        <div><dt>배송지</dt><dd>({order.shipping.postalCode}) {order.shipping.address}{order.shipping.addressDetail && `, ${order.shipping.addressDetail}`}</dd></div>
        {order.shipping.memo && <div><dt>배송 메모</dt><dd>{order.shipping.memo}</dd></div>}
      </> : <div><dt>배송지</dt><dd>배송지 없음 (배송지를 받기 전에 만든 주문)</dd></div>}
      {delivery?.carrier && delivery.trackingNumber && <>
        <div><dt>송장</dt><dd>{CARRIERS[delivery.carrier].name} {delivery.trackingNumber} · <a href={CARRIERS[delivery.carrier].trackingUrl(delivery.trackingNumber)} target="_blank" rel="noreferrer">배송 조회</a></dd></div>
        <div><dt>출고 시각</dt><dd>{formatDateTime(delivery.shippedAt)}</dd></div>
        {delivery.deliveredAt && <div><dt>배송 완료 시각</dt><dd>{formatDateTime(delivery.deliveredAt)}</dd></div>}
      </>}
    </dl>
    {order.status !== "PAID" && <p className="subtle">결제가 완료되지 않은 주문이라 배송할 수 없습니다.</p>}
    {order.status === "PAID" && !order.shipping && <p className="shipping-warning">배송지 없이 만든 이전 주문이라 송장을 등록할 수 없습니다.</p>}
    {shippable && <form className="ship-form" onSubmit={handleShip}>
      <div>
        <label htmlFor="ship-carrier">택배사</label>
        <select id="ship-carrier" value={carrier} disabled={busy} onChange={event => setCarrier(event.target.value as Carrier)}>
          {CARRIER_CODES.map(code => <option key={code} value={code}>{CARRIERS[code].name}</option>)}
        </select>
      </div>
      <div>
        <label htmlFor="ship-tracking">송장번호</label>
        <input id="ship-tracking" required inputMode="numeric" pattern={TRACKING_NUMBER_PATTERN} maxLength={24}
          placeholder="숫자만 입력" title="하이픈 없이 숫자 8~20자리" value={trackingNumber} disabled={busy}
          onChange={event => setTrackingNumber(normalizeTrackingNumber(event.target.value))} />
      </div>
      <button className="primary-button" type="submit" disabled={busy}>
        {busy ? "저장 중…" : delivery?.status === "SHIPPED" ? "송장 수정" : "송장 등록"}
      </button>
    </form>}
    {delivery?.status === "SHIPPED" && <div className="actions admin-detail-actions">
      <button className="secondary" type="button" disabled={busy} onClick={handleDelivered}>배송 완료 처리</button>
      <span className="subtle">배송 완료로 바꾸면 송장을 더 고칠 수 없습니다.</span>
    </div>}
    {actionError && <p className="error" role="alert">{actionError}</p>}
  </>;
}
