import { ANONYMOUS, loadTossPayments } from "@tosspayments/tosspayments-sdk";
import type { Order, PaymentConfig } from "../types/payment";
import { openPaymentWindow } from "./paymentWindow.ts";

export async function openTossPayment(
  order: Order, config: PaymentConfig, signal: AbortSignal,
  attemptId: string, onCancel: () => Promise<void>
): Promise<void> {
  if (signal.aborted) return;
  const tossPayments = await loadTossPayments(config.clientKey);
  if (signal.aborted) return;
  const widgets = tossPayments.widgets({ customerKey: ANONYMOUS });
  await openPaymentWindow(widgets, order, config, signal, attemptId, onCancel);
}
