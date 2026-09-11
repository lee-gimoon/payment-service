import type { ConfirmPaymentCommand } from "../types/payment";
import {
  readSessionValue,
  writeSessionValue
} from "../lib/storage";

export type PaymentRedirectFlow = "success" | "fail" | null;

export interface PaymentRedirectState {
  orderId: string | null;
  flow: PaymentRedirectFlow;
  confirmation: ConfirmPaymentCommand | null;
  storageKey: string | null;
}

const ORDER_ID_PATTERN = /^[a-zA-Z0-9_-]{6,64}$/;
const AMOUNT_PATTERN = /^\d{1,12}$/;

function isConfirmation(
  value: unknown,
  expectedOrderId: string
): value is ConfirmPaymentCommand {
  if (!value || typeof value !== "object") {
    return false;
  }

  const command = value as Partial<ConfirmPaymentCommand>;
  return (
    command.orderId === expectedOrderId &&
    typeof command.paymentKey === "string" &&
    command.paymentKey.length > 0 &&
    command.paymentKey.length <= 200 &&
    typeof command.amount === "number" &&
    Number.isInteger(command.amount) &&
    command.amount >= 1 &&
    command.amount <= 999_999_999_999
  );
}

function readStoredConfirmation(
  storageKey: string,
  orderId: string
): ConfirmPaymentCommand | null {
  const storedValue = readSessionValue(storageKey);
  if (!storedValue) {
    return null;
  }

  try {
    const parsed = JSON.parse(storedValue) as unknown;
    return isConfirmation(parsed, orderId) ? parsed : null;
  } catch {
    return null;
  }
}

export function readPaymentRedirect(): PaymentRedirectState {
  const parameters = new URLSearchParams(window.location.search);
  const rawOrderId = parameters.get("orderId") || parameters.get("requestedOrderId");
  const orderId = rawOrderId && ORDER_ID_PATTERN.test(rawOrderId) ? rawOrderId : null;
  const rawFlow = parameters.get("flow");
  const flow: PaymentRedirectFlow =
    rawFlow === "success" || rawFlow === "fail" ? rawFlow : null;
  const storageKey = orderId ? `pendingConfirmation:${orderId}` : null;

  let confirmation: ConfirmPaymentCommand | null = null;

  if (flow === "success" && orderId && storageKey) {
    const paymentKey = parameters.get("paymentKey");
    const amount = parameters.get("amount");

    if (paymentKey && paymentKey.length <= 200 && amount && AMOUNT_PATTERN.test(amount)) {
      const candidate: ConfirmPaymentCommand = {
        orderId,
        paymentKey,
        amount: Number(amount)
      };

      if (isConfirmation(candidate, orderId)) {
        confirmation = candidate;
        writeSessionValue(storageKey, JSON.stringify(candidate));
      }
    }
  } else if (flow !== "fail" && orderId && storageKey) {
    confirmation = readStoredConfirmation(storageKey, orderId);
  }

  // paymentKey가 다른 페이지의 Referer나 브라우저 기록에 남지 않도록 즉시 제거한다.
  const safeUrl = orderId
    ? `/payment/result?orderId=${encodeURIComponent(orderId)}`
    : "/payment/result";
  window.history.replaceState(null, "", safeUrl);

  return { orderId, flow, confirmation, storageKey };
}
