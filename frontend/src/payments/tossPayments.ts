/** SDK v2의 결제창형 제품을 초기화한다. 최종 승인은 복귀 후 서버에서 한다. */
import { ANONYMOUS, loadTossPayments } from "@tosspayments/tosspayments-sdk";
import type { Order, PaymentConfig } from "../types/payment";
import { openPaymentWindow } from "./paymentWindow.ts";

export async function openTossPayment(
  order: Order, config: PaymentConfig, signal: AbortSignal
): Promise<void> {
  if (signal.aborted) return;
  const tossPayments = await loadTossPayments(config.clientKey);
  if (signal.aborted) return;
  // 로그인 없는 비회원 결제다. customerKey와 orderId는 서로 다른 역할이다.
  const widgets = tossPayments.widgets({ customerKey: ANONYMOUS });
  await openPaymentWindow(widgets, order, config, signal);
}
