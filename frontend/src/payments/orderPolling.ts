import type { Order, PaymentStatus } from "../types/payment";

export function isPaymentPending(status: PaymentStatus): boolean {
  return status === "PROCESSING" || status === "UNKNOWN" || status === "CANCEL_PENDING";
}

/** 화면은 저장된 주문만 읽는다. 토스 재조회와 취소는 서버 작업이 독립적으로 실행한다. */
export function watchOrder(
  load: (signal: AbortSignal) => Promise<Order>,
  onOrder: (order: Order) => void,
  delayMs = 3000
): () => void {
  const controller = new AbortController();
  let timer: ReturnType<typeof setTimeout>;
  async function poll() {
    let pending = true;
    try {
      const order = await load(controller.signal);
      if (controller.signal.aborted) return;
      pending = isPaymentPending(order.payment.status);
      onOrder(order);
    } catch {
      // 일시적인 주문 조회 실패도 결제 실패로 표시하지 않고 다음 조회를 기다린다.
    }
    if (!controller.signal.aborted && pending) timer = setTimeout(poll, delayMs);
  }
  timer = setTimeout(poll, delayMs);
  return () => { controller.abort(); clearTimeout(timer); };
}
