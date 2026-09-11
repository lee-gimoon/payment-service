import type { FormEvent } from "react";

interface OrderLookupProps {
  orderId: string;
  busy: boolean;
  onOrderIdChange: (orderId: string) => void;
  onSubmit: () => void;
}

export function OrderLookup({
  orderId,
  busy,
  onOrderIdChange,
  onSubmit
}: OrderLookupProps) {
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit();
  }

  return (
    <section className="lookup-card" aria-labelledby="lookup-title">
      <div>
        <p className="eyebrow">ORDER STATUS</p>
        <h2 id="lookup-title">결제 결과 다시 확인하기</h2>
        <p className="subtle">주문번호로 저장된 결과를 언제든 확인하세요.</p>
      </div>

      <form onSubmit={handleSubmit}>
        <label htmlFor="order-id">주문번호</label>
        <div className="input-row">
          <input
            id="order-id"
            name="orderId"
            required
            minLength={6}
            maxLength={64}
            pattern="[a-zA-Z0-9_-]{6,64}"
            placeholder="주문번호를 입력하세요"
            autoComplete="off"
            value={orderId}
            disabled={busy}
            onChange={(event) => onOrderIdChange(event.target.value)}
          />
          <button className="secondary" type="submit" disabled={busy}>
            조회
          </button>
        </div>
      </form>
    </section>
  );
}
