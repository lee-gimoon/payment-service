/** 파일 역할: 주문번호를 입력하고 저장된 주문 조회를 요청하는 폼을 제공한다. */
import type { FormEvent } from "react";

/** 부모가 관리하는 입력값·작업 상태와 입력 변경·조회 요청 콜백을 전달받는다. */
interface OrderLookupProps {
  orderId: string;
  busy: boolean;
  onOrderIdChange: (orderId: string) => void;
  onSubmit: () => void;
}

/** 주문번호 입력값을 부모와 공유하고 조회 버튼이나 Enter 입력을 처리하는 화면 컴포넌트다. */
export function OrderLookup({
  orderId,
  busy,
  onOrderIdChange,
  onSubmit
}: OrderLookupProps) {
  /** 폼 제출의 기본 페이지 이동을 막고 부모 화면의 주문 조회 함수를 호출한다. */
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
