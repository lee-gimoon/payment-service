import type { ConfirmPaymentCommand } from "../types/payment";
import {
  readSessionValue,
  removeSessionValue,
  writeSessionValue
} from "../lib/storage.ts";

export type PaymentRedirectFlow = "success" | "fail" | null;

export interface PaymentRedirectState {
  orderId: string | null;
  attemptId: string | null;
  flow: PaymentRedirectFlow;
  confirmation: ConfirmPaymentCommand | null;
  storageKey: string | null;
  errorCode: string | null;
  errorMessage: string | null;
}

const ORDER_ID_PATTERN = /^[a-zA-Z0-9_-]{6,64}$/;
const AMOUNT_PATTERN = /^\d{1,12}$/;
const ATTEMPT_ID_PATTERN = /^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$/;

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
    command.amount <= 999_999_999_999 &&
    typeof command.attemptId === "string" &&
    ATTEMPT_ID_PATTERN.test(command.attemptId)
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

// 승인 응답을 받기 전 새로고침하면 같은 시도의 승인 정보로 복구한다.
export function readPaymentRedirect(): PaymentRedirectState {
  const parameters = new URLSearchParams(window.location.search);
  const rawOrderId = parameters.get("orderId") || parameters.get("requestedOrderId");
  const orderId = rawOrderId && ORDER_ID_PATTERN.test(rawOrderId) ? rawOrderId : null;
  const rawAttemptId = parameters.get("attemptId");
  const attemptId = rawAttemptId && ATTEMPT_ID_PATTERN.test(rawAttemptId) ? rawAttemptId : null;
  const rawFlow = parameters.get("flow");
  const flow: PaymentRedirectFlow =
    rawFlow === "success" || rawFlow === "fail" ? rawFlow : null;
  const storageKey = orderId ? `pendingConfirmation:${orderId}` : null;
  const errorCode = flow === "fail" ? parameters.get("code")?.slice(0, 80) ?? null : null;
  const errorMessage = flow === "fail" ? parameters.get("message")?.slice(0, 300) ?? null : null;

  let confirmation: ConfirmPaymentCommand | null = null;

  if (flow !== null && storageKey) {
    // 새 인증 결과에 이전 시도의 승인 정보를 재사용하지 않는다.
    removeSessionValue(storageKey);
  }

  if (flow === "success" && orderId && attemptId && storageKey) {
    const paymentKey = parameters.get("paymentKey");
    const amount = parameters.get("amount");

    if (paymentKey && paymentKey.length <= 200 && amount && AMOUNT_PATTERN.test(amount)) {
      const candidate: ConfirmPaymentCommand = {
        orderId,
        paymentKey,
        amount: Number(amount),
        attemptId
      };

      if (isConfirmation(candidate, orderId)) {
        confirmation = candidate;
        writeSessionValue(storageKey, JSON.stringify(candidate));
      }
    }
  } else if (flow === null && orderId && storageKey) {
    confirmation = readStoredConfirmation(storageKey, orderId);
  }

  // 결제 키가 브라우저 기록과 다른 페이지의 Referer에 남지 않게 한다.
  const safeUrl = orderId
    ? `/payment/result?orderId=${encodeURIComponent(orderId)}`
    : "/payment/result";
  window.history.replaceState(null, "", safeUrl);

  return { orderId, attemptId, flow, confirmation, storageKey, errorCode, errorMessage };
}
