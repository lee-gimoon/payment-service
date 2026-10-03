import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { createAddress, getAddresses } from "../api/myPageApi";
import { emptyAddressInput, MAX_ADDRESSES } from "../lib/addresses";
import type { Address, AddressInput } from "../types/myPage";
import { AddressForm } from "./AddressForm";
import { AddressLines } from "./AddressLines";

interface ShippingPickerProps {
  selectedId: string | null;
  onSelect: (addressId: string | null) => void;
  memo: string;
  onMemoChange: (memo: string) => void;
  disabled: boolean;
}

/**
 * 결제 전에 마이페이지에 저장한 배송지 중 하나를 고른다. 처음에는 기본 배송지가 골라져 있다.
 * 여기서 추가한 배송지는 마이페이지 주소록에도 저장된다.
 */
export function ShippingPicker({ selectedId, onSelect, memo, onMemoChange, disabled }: ShippingPickerProps) {
  const [addresses, setAddresses] = useState<Address[] | null>(null);
  const [error, setError] = useState("");
  const [choosing, setChoosing] = useState(false);
  const [adding, setAdding] = useState(false);
  const [version, setVersion] = useState(0);
  const selected = addresses?.find(address => address.id === selectedId) ?? null;

  useEffect(() => {
    let active = true;
    setError("");
    getAddresses()
      .then(result => { if (active) setAddresses(result); })
      .catch(requestError => {
        if (active) setError(requestError instanceof Error ? requestError.message : "배송지를 불러오지 못했습니다.");
      });
    return () => { active = false; };
  }, [version]);

  // 고른 배송지가 없거나 목록에서 사라졌으면 기본 배송지(목록의 첫 번째)를 고른다.
  useEffect(() => {
    if (addresses !== null && !addresses.some(address => address.id === selectedId)) {
      onSelect(addresses[0]?.id ?? null);
    }
  }, [addresses, selectedId, onSelect]);

  async function add(input: AddressInput) {
    const created = await createAddress(input);
    setAddresses(previous => [...(previous ?? []), created]);
    onSelect(created.id);
    setAdding(false);
    setChoosing(false);
    // 기본 배송지가 바뀌었을 수 있으니 목록을 다시 받는다.
    setVersion(value => value + 1);
  }

  return <section className="checkout-card shipping-card" aria-labelledby="shipping-title">
    <div className="card-heading">
      <h2 id="shipping-title">배송지</h2>
      {addresses && addresses.length > 0 && !adding && <button className="secondary" type="button" disabled={disabled}
        onClick={() => setChoosing(value => !value)}>{choosing ? "닫기" : "변경"}</button>}
    </div>
    {error ? <div className="error" role="alert">{error} <button className="secondary" type="button" onClick={() => setVersion(value => value + 1)}>다시 시도</button></div>
      : addresses === null ? <p role="status">배송지를 불러오고 있습니다.</p>
      : adding ? <AddressForm title="새 배송지" initial={emptyAddressInput()} firstAddress={addresses.length === 0}
        alreadyDefault={false} onSubmit={add} onCancel={() => setAdding(false)} />
      : addresses.length === 0 ? <div>
        <p>저장한 배송지가 없습니다. 배송지를 추가해야 결제할 수 있으며, 추가한 배송지는 마이페이지에도 저장됩니다.</p>
        <button className="secondary" type="button" disabled={disabled} onClick={() => setAdding(true)}>+ 배송지 추가</button>
      </div>
      : choosing ? <fieldset className="address-choices">
        <legend>배송지 고르기</legend>
        {addresses.map(address => <label key={address.id} className={address.id === selectedId ? "address-choice selected" : "address-choice"}>
          <input type="radio" name="shipping-address" checked={address.id === selectedId} disabled={disabled}
            onChange={() => { onSelect(address.id); setChoosing(false); }} />
          <span><AddressLines address={address} /></span>
        </label>)}
        <div className="address-choice-actions">
          {addresses.length < MAX_ADDRESSES
            ? <button className="text-button" type="button" disabled={disabled} onClick={() => setAdding(true)}>+ 새 배송지 추가</button>
            : <span className="subtle">배송지는 최대 {MAX_ADDRESSES}개까지 저장할 수 있습니다.</span>}
          <Link className="text-link" to="/mypage">마이페이지에서 배송지 관리</Link>
        </div>
      </fieldset>
      : selected && <div className="address-card"><AddressLines address={selected} /></div>}
    {addresses && addresses.length > 0 && !adding && <div className="memo-field">
      <label htmlFor="delivery-memo">배송 메모 (선택)</label>
      <input id="delivery-memo" maxLength={50} placeholder="예: 문 앞에 놓아주세요" value={memo} disabled={disabled}
        onChange={event => onMemoChange(event.target.value)} />
    </div>}
    <p className="subtle">주문할 때 배송지를 복사해 저장합니다. 마이페이지에서 배송지를 고쳐도 이미 한 주문의 배송지는 바뀌지 않습니다.</p>
  </section>;
}
