import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { createAddress, deleteAddress, getAddresses, getMyOrders, makeDefaultAddress, updateAddress } from "../api/myPageApi";
import { useAuth } from "../auth/auth";
import { accountUrl } from "../auth/keycloak";
import { AddressForm } from "../components/AddressForm";
import { AppShell } from "../components/AppShell";
import { emptyAddressInput, formatPhone, MAX_ADDRESSES, toAddressInput } from "../lib/addresses";
import { formatAmount, formatDateTime, orderStatusLabel } from "../lib/formatters";
import type { Address, AddressInput, OrderSummary } from "../types/myPage";

function errorText(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback;
}

// 주문·배송지는 쇼핑몰 데이터라 여기서 관리하고, 이름·비밀번호 같은 계정 정보는 Keycloak 계정 화면에서 바꾼다.
export function MyPage() {
  const { status, customer, login } = useAuth();

  useEffect(() => {
    document.title = "마이페이지 · MODO CLUB";
    window.scrollTo(0, 0);
  }, []);

  return <AppShell footerText="마이페이지 · 주문 내역과 배송지" mainClassName="mypage">
    <nav className="breadcrumb" aria-label="현재 위치"><Link to="/">스토어</Link><span aria-hidden="true">/</span><span>마이페이지</span></nav>
    <div className="page-intro">
      <p className="eyebrow">MY PAGE</p>
      <h1>마이페이지</h1>
      <p>{status === "signedIn" && customer && !customer.isShopAdmin ? `${customer.name}님의 주문 내역과 배송지를 관리합니다.` : "주문 내역과 배송지를 관리합니다."}</p>
    </div>
    {status === "checking" && <p role="status">로그인 상태를 확인하고 있습니다.</p>}
    {status === "signedOut" && <section className="lookup-card" aria-labelledby="mypage-sign-in-title">
      <div>
        <p className="eyebrow">SIGN IN</p>
        <h2 id="mypage-sign-in-title">로그인이 필요합니다</h2>
        <p className="subtle">주문 내역과 배송지는 본인만 볼 수 있습니다.</p>
      </div>
      <button className="primary-button" type="button" onClick={() => login()}>로그인하고 마이페이지 보기</button>
    </section>}
    {status === "signedIn" && customer?.isShopAdmin && <section className="lookup-card" aria-labelledby="mypage-admin-title">
      <div>
        <p className="eyebrow">STAFF ACCOUNT</p>
        <h2 id="mypage-admin-title">관리자 계정은 마이페이지를 쓰지 않습니다</h2>
        <p className="subtle">주문·결제·배송지·고객 상담은 일반 회원 기능입니다. 상품을 직접 사 보려면 일반 회원 계정으로 로그인해주세요.</p>
      </div>
      <div className="actions">
        <Link className="primary-button" to="/admin/chat">상담 관리로 이동</Link>
        <a className="secondary-link" href={accountUrl("/mypage")}>계정 설정</a>
      </div>
    </section>}
    {status === "signedIn" && customer && !customer.isShopAdmin && <>
      <MyOrders key={`orders-${customer.id}`} />
      <MyAddresses key={`addresses-${customer.id}`} />
      <section className="mypage-section" aria-labelledby="account-title">
        <div className="mypage-heading"><h2 id="account-title">계정 설정</h2></div>
        <p className="subtle">이름, 이메일, 비밀번호, 2단계 인증은 로그인 서비스(Keycloak)의 계정 화면에서 바꿉니다.</p>
        <a className="secondary-link" href={accountUrl("/mypage")}>계정 설정 열기 <span aria-hidden="true">↗</span></a>
      </section>
    </>}
  </AppShell>;
}

function MyOrders() {
  const [orders, setOrders] = useState<OrderSummary[] | null>(null);
  const [error, setError] = useState("");
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let active = true;
    setError("");
    getMyOrders()
      .then(result => { if (active) setOrders(result); })
      .catch(requestError => { if (active) setError(errorText(requestError, "주문 내역을 불러오지 못했습니다.")); });
    return () => { active = false; };
  }, [version]);

  return <section className="mypage-section" aria-labelledby="orders-title">
    <div className="mypage-heading">
      <h2 id="orders-title">내 주문 내역</h2>
      {orders && orders.length > 0 && <span className="subtle">최근 {orders.length}건</span>}
    </div>
    {error ? <div className="error" role="alert">{error} <button className="secondary" type="button" onClick={() => setVersion(value => value + 1)}>다시 시도</button></div>
      : orders === null ? <p role="status">주문 내역을 불러오고 있습니다.</p>
      : orders.length === 0 ? <p className="mypage-empty">아직 주문이 없습니다. <Link to="/#products">상품 보러 가기</Link></p>
      : <ul className="order-history">{orders.map(order => <li key={order.orderId}>
        <Link to={`/orders/${encodeURIComponent(order.orderId)}`}>
          <span className="order-history-main">
            <strong>{order.productName}</strong>
            <small>{formatDateTime(order.createdAt)} · {order.quantity}장</small>
          </span>
          <span className="order-history-side">
            <strong>{formatAmount(order.amount)}</strong>
            <span className="status-badge">{orderStatusLabel(order.status)}</span>
          </span>
        </Link>
      </li>)}</ul>}
    <p className="subtle">최근 50건까지 보여줍니다. 결제 상태와 상품별 내역은 주문을 눌러 확인하세요.</p>
  </section>;
}

type Editing = { mode: "create" } | { mode: "edit"; address: Address } | null;

function MyAddresses() {
  const [addresses, setAddresses] = useState<Address[] | null>(null);
  const [loadError, setLoadError] = useState("");
  const [actionError, setActionError] = useState("");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [editing, setEditing] = useState<Editing>(null);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let active = true;
    setLoadError("");
    getAddresses()
      .then(result => { if (active) setAddresses(result); })
      .catch(requestError => { if (active) setLoadError(errorText(requestError, "배송지를 불러오지 못했습니다.")); });
    return () => { active = false; };
  }, [version]);

  function reload() {
    setVersion(value => value + 1);
  }

  async function save(input: AddressInput) {
    if (editing?.mode === "edit") await updateAddress(editing.address.id, input);
    else await createAddress(input);
    setEditing(null);
    setActionError("");
    reload();
  }

  async function act(addressId: string, work: () => Promise<unknown>) {
    setBusyId(addressId);
    setActionError("");
    try {
      await work();
      reload();
    } catch (requestError) {
      setActionError(errorText(requestError, "배송지를 바꾸지 못했습니다."));
    } finally {
      setBusyId(null);
    }
  }

  function handleDelete(address: Address) {
    if (!window.confirm(`'${address.label}' 배송지를 삭제할까요?`)) return;
    void act(address.id, () => deleteAddress(address.id));
  }

  const full = addresses !== null && addresses.length >= MAX_ADDRESSES;

  return <section className="mypage-section" aria-labelledby="addresses-title">
    <div className="mypage-heading">
      <h2 id="addresses-title">내 배송지</h2>
      {addresses && <span className="subtle">{addresses.length}/{MAX_ADDRESSES}개</span>}
      {addresses && !editing && <button className="secondary" type="button" disabled={full || busyId !== null}
        onClick={() => { setActionError(""); setEditing({ mode: "create" }); }}>+ 배송지 추가</button>}
    </div>
    {full && !editing && <p className="subtle">배송지는 최대 {MAX_ADDRESSES}개까지 저장할 수 있습니다. 쓰지 않는 배송지를 삭제한 뒤 추가해주세요.</p>}
    {editing && <AddressForm
      key={editing.mode === "edit" ? editing.address.id : "new"}
      title={editing.mode === "edit" ? `'${editing.address.label}' 배송지 수정` : "새 배송지"}
      initial={editing.mode === "edit" ? toAddressInput(editing.address) : emptyAddressInput()}
      firstAddress={editing.mode === "create" && addresses?.length === 0}
      alreadyDefault={editing.mode === "edit" && editing.address.defaultAddress}
      onSubmit={save}
      onCancel={() => setEditing(null)} />}
    {loadError ? <div className="error" role="alert">{loadError} <button className="secondary" type="button" onClick={reload}>다시 시도</button></div>
      : addresses === null ? <p role="status">배송지를 불러오고 있습니다.</p>
      : addresses.length === 0 ? !editing && <p className="mypage-empty">저장한 배송지가 없습니다. 자주 쓰는 배송지를 추가해 두세요.</p>
      : <ul className="address-list">{addresses.map(address => <li key={address.id} className={address.defaultAddress ? "address-card default" : "address-card"}>
        <div className="address-card-title">
          <strong>{address.label}</strong>
          {address.defaultAddress && <span className="status-badge">기본 배송지</span>}
        </div>
        <p>{address.recipientName} · {formatPhone(address.phone)}</p>
        <p>({address.postalCode}) {address.address}{address.addressDetail && `, ${address.addressDetail}`}</p>
        <div className="address-actions">
          {!address.defaultAddress && <button className="text-button" type="button" disabled={busyId !== null}
            onClick={() => void act(address.id, () => makeDefaultAddress(address.id))}>기본 배송지로 지정</button>}
          <button className="text-button" type="button" disabled={busyId !== null}
            onClick={() => { setActionError(""); setEditing({ mode: "edit", address }); }}>수정</button>
          <button className="text-button" type="button" disabled={busyId !== null}
            onClick={() => handleDelete(address)}>삭제</button>
        </div>
      </li>)}</ul>}
    {actionError && <p className="error" role="alert">{actionError}</p>}
  </section>;
}
