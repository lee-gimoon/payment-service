import { useEffect, useRef, useState, type FormEvent } from "react";
import { PHONE_PATTERN, postcodeAddress } from "../lib/addresses";
import { embedPostcodeSearch } from "../lib/postcode";
import type { AddressInput } from "../types/myPage";

interface AddressFormProps {
  title: string;
  initial: AddressInput;
  /** 첫 배송지는 고르지 않아도 기본 배송지가 되므로 선택란 대신 안내를 보여준다. */
  firstAddress: boolean;
  /** 이미 기본 배송지인 것을 고칠 때는 기본 배송지 선택란을 보여주지 않는다. */
  alreadyDefault: boolean;
  /** 저장에 실패하면 예외를 던지고, 폼은 입력값을 그대로 둔 채 오류를 보여준다. */
  onSubmit: (input: AddressInput) => Promise<void>;
  onCancel: () => void;
}

export function AddressForm({ title, initial, firstAddress, alreadyDefault, onSubmit, onCancel }: AddressFormProps) {
  const [input, setInput] = useState(initial);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState("");
  const searchArea = useRef<HTMLDivElement>(null);
  const detailInput = useRef<HTMLInputElement>(null);

  function change<K extends keyof AddressInput>(key: K, value: AddressInput[K]) {
    setInput(previous => ({ ...previous, [key]: value }));
  }

  useEffect(() => {
    if (!searching || !searchArea.current) return;
    let active = true;
    embedPostcodeSearch(searchArea.current, result => {
      if (!active) return;
      setInput(previous => ({ ...previous, ...postcodeAddress(result) }));
      setSearching(false);
      detailInput.current?.focus();
    }).catch(() => {
      if (!active) return;
      setSearching(false);
      setSearchError("우편번호 찾기를 불러오지 못했습니다. 우편번호와 주소를 직접 입력해주세요.");
    });
    return () => { active = false; };
  }, [searching]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      // 성공하면 부모가 폼을 닫는다.
      await onSubmit(input);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "배송지를 저장하지 못했습니다.");
      setBusy(false);
    }
  }

  return <form className="address-form" onSubmit={handleSubmit} aria-labelledby="address-form-title">
    <h3 id="address-form-title">{title}</h3>
    <div className="form-grid">
      <div>
        <label htmlFor="address-label">배송지 이름</label>
        <input id="address-label" required maxLength={20} placeholder="예: 집, 회사" value={input.label}
          disabled={busy} onChange={event => change("label", event.target.value)} />
      </div>
      <div>
        <label htmlFor="address-recipient">받는 분</label>
        <input id="address-recipient" required maxLength={50} autoComplete="name" value={input.recipientName}
          disabled={busy} onChange={event => change("recipientName", event.target.value)} />
      </div>
      <div>
        <label htmlFor="address-phone">연락처</label>
        <input id="address-phone" type="tel" inputMode="tel" required pattern={PHONE_PATTERN} maxLength={13}
          placeholder="010-1234-5678" autoComplete="tel" title="0으로 시작하는 전화번호를 입력해주세요. 예: 010-1234-5678"
          value={input.phone} disabled={busy} onChange={event => change("phone", event.target.value)} />
      </div>
      <div className="form-wide">
        <label htmlFor="address-postal">우편번호</label>
        <div className="input-row postal-row">
          <input id="address-postal" required pattern="\d{5}" inputMode="numeric" maxLength={5} autoComplete="postal-code"
            title="숫자 5자리 우편번호" value={input.postalCode} disabled={busy}
            onChange={event => change("postalCode", event.target.value)} />
          <button className="secondary" type="button" disabled={busy}
            onClick={() => { setSearchError(""); setSearching(value => !value); }}>
            {searching ? "찾기 닫기" : "우편번호 찾기"}
          </button>
        </div>
      </div>
      {searching && <div className="postcode-frame form-wide" ref={searchArea} aria-label="우편번호 찾기" />}
      {searchError && <p className="error form-wide" role="alert">{searchError}</p>}
      <div className="form-wide">
        <label htmlFor="address-road">주소</label>
        <input id="address-road" required maxLength={200} autoComplete="address-line1" value={input.address}
          disabled={busy} onChange={event => change("address", event.target.value)} />
      </div>
      <div className="form-wide">
        <label htmlFor="address-detail">상세 주소</label>
        <input id="address-detail" ref={detailInput} maxLength={100} placeholder="동·호수 등 (선택)"
          autoComplete="address-line2" value={input.addressDetail} disabled={busy}
          onChange={event => change("addressDetail", event.target.value)} />
      </div>
    </div>
    {firstAddress
      ? <p className="subtle">첫 배송지는 기본 배송지로 저장됩니다.</p>
      : !alreadyDefault && <label className="checkbox-label">
        <input type="checkbox" checked={input.makeDefault} disabled={busy}
          onChange={event => change("makeDefault", event.target.checked)} />
        기본 배송지로 지정
      </label>}
    <div className="actions">
      <button className="primary-button" type="submit" disabled={busy}>{busy ? "저장 중…" : "저장"}</button>
      <button className="secondary" type="button" disabled={busy} onClick={onCancel}>취소</button>
    </div>
    {error && <p className="error" role="alert">{error}</p>}
  </form>;
}
