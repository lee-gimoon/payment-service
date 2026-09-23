import { useEffect, useRef } from "react";
import { getOrder } from "../api/paymentApi";
import type { Order } from "../types/payment";
import { isPaymentPending, watchOrder } from "./orderPolling";

/** 처리 중인 주문을 화면이 열려 있는 동안 갱신하고, 완료되거나 화면을 떠나면 멈춘다. */
export function useOrderPolling(order: Order | null, onOrder: (order: Order) => void, fallbackId?: string | null) {
  const callback = useRef(onOrder);
  useEffect(() => { callback.current = onOrder; });
  const id = order?.orderId ?? fallbackId;
  const status = order?.payment.status;
  useEffect(() => {
    if (!id || (status && !isPaymentPending(status))) return;
    return watchOrder(signal => getOrder(id, signal), next => callback.current(next));
  }, [id, status]);
}
