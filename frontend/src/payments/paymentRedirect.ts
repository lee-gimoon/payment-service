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
  stockRejection: StockRejection | null;
}

interface StockRejection {
  orderId: string;
  attemptId: string;
  message: string;
}

interface PendingFailure {
  orderId: string;
  attemptId: string;
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

function failureStorageKey(orderId: string): string {
  return `pendingAuthenticationResult:${orderId}`;
}

function stockRejectionStorageKey(orderId: string): string {
  return `stockRejection:${orderId}`;
}

function readStockRejection(orderId: string): StockRejection | null {
  try {
    const value = JSON.parse(readSessionValue(stockRejectionStorageKey(orderId)) ?? "null") as Partial<StockRejection> | null;
    return value?.orderId === orderId && typeof value.attemptId === "string"
      && ATTEMPT_ID_PATTERN.test(value.attemptId) && typeof value.message === "string"
      && value.message.length <= 300 ? value as StockRejection : null;
  } catch {
    return null;
  }
}

/** 확정된 품절 거절은 승인 명령 대신 안내만 보관해 새로고침으로 승인하지 않는다. */
export function recordStockRejection(command: ConfirmPaymentCommand, message: string): void {
  const storageKey = `pendingConfirmation:${command.orderId}`;
  const current = readStoredConfirmation(storageKey, command.orderId);
  // 이전 요청의 늦은 응답이 새 결제 시도의 복귀 정보를 지우지 않는다.
  if (current && (current.attemptId !== command.attemptId || current.paymentKey !== command.paymentKey)) return;
  const failure = readStoredFailure(command.orderId);
  if (failure && failure.attemptId !== command.attemptId) return;
  removeSessionValue(storageKey);
  writeSessionValue(stockRejectionStorageKey(command.orderId), JSON.stringify({
    orderId: command.orderId, attemptId: command.attemptId, message: message.slice(0, 300)
  }));
}

function readStoredFailure(orderId: string): PendingFailure | null {
  const stored = readSessionValue(failureStorageKey(orderId));
  if (!stored) return null;
  try {
    const value: unknown = JSON.parse(stored);
    if (!value || typeof value !== "object") return null;
    const failure = value as Partial<PendingFailure>;
    if (failure.orderId !== orderId || typeof failure.attemptId !== "string"
      || !ATTEMPT_ID_PATTERN.test(failure.attemptId)
      || !(failure.errorCode === null || (typeof failure.errorCode === "string" && failure.errorCode.length <= 80))
      || !(failure.errorMessage === null || (typeof failure.errorMessage === "string" && failure.errorMessage.length <= 300))) {
      return null;
    }
    return failure as PendingFailure;
  } catch {
    return null;
  }
}

/** 401·통신 실패 때는 남겨 두고, 서버가 기록을 받아들인 뒤에만 복귀 정보를 지운다. */
export async function recordPaymentFailure(
  redirect: PaymentRedirectState,
  record: (attemptId: string, status: "AUTH_CANCELED" | "AUTH_FAILED", errorCode: string | null) => Promise<unknown>
): Promise<void> {
  if (redirect.flow !== "fail" || !redirect.attemptId) return;
  await record(redirect.attemptId,
    redirect.errorCode === "PAY_PROCESS_CANCELED" ? "AUTH_CANCELED" : "AUTH_FAILED", redirect.errorCode);
  if (redirect.orderId && readStoredFailure(redirect.orderId)?.attemptId === redirect.attemptId) {
    removeSessionValue(failureStorageKey(redirect.orderId));
  }
}

// 새로고침·재로그인 뒤에도 같은 시도의 승인 또는 취소·실패 기록을 이어간다.
export function readPaymentRedirect(): PaymentRedirectState {
  const parameters = new URLSearchParams(window.location.search);
  const rawOrderId = parameters.get("orderId") || parameters.get("requestedOrderId");
  const orderId = rawOrderId && ORDER_ID_PATTERN.test(rawOrderId) ? rawOrderId : null;
  const rawAttemptId = parameters.get("attemptId");
  let attemptId = rawAttemptId && ATTEMPT_ID_PATTERN.test(rawAttemptId) ? rawAttemptId : null;
  const rawFlow = parameters.get("flow");
  let flow: PaymentRedirectFlow =
    rawFlow === "success" || rawFlow === "fail" ? rawFlow : null;
  const storageKey = orderId ? `pendingConfirmation:${orderId}` : null;
  let errorCode = flow === "fail" ? parameters.get("code")?.slice(0, 80) ?? null : null;
  let errorMessage = flow === "fail" ? parameters.get("message")?.slice(0, 300) ?? null : null;
  let stockRejection = orderId ? readStockRejection(orderId) : null;

  let confirmation: ConfirmPaymentCommand | null = null;

  if (flow !== null && storageKey) {
    // 새 인증 결과에 이전 시도의 승인·실패 정보를 재사용하지 않는다.
    removeSessionValue(storageKey);
    removeSessionValue(failureStorageKey(orderId as string));
    if (stockRejection?.attemptId !== attemptId) {
      removeSessionValue(stockRejectionStorageKey(orderId as string));
      stockRejection = null;
    }
  }

  if (flow === "success" && orderId && attemptId && storageKey && !stockRejection) {
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
  } else if (flow === "fail" && orderId && attemptId) {
    writeSessionValue(failureStorageKey(orderId), JSON.stringify({ orderId, attemptId, errorCode, errorMessage }));
  } else if (flow === null && orderId && storageKey && !stockRejection) {
    const failure = readStoredFailure(orderId);
    if (failure) {
      flow = "fail";
      attemptId = failure.attemptId;
      errorCode = failure.errorCode;
      errorMessage = failure.errorMessage;
    } else {
      confirmation = readStoredConfirmation(storageKey, orderId);
    }
  }

  // 결제 키가 브라우저 기록과 다른 페이지의 Referer에 남지 않게 한다.
  const safeUrl = orderId
    ? `/payment/result?orderId=${encodeURIComponent(orderId)}`
    : "/payment/result";
  window.history.replaceState(null, "", safeUrl);

  return { orderId, attemptId, flow, confirmation, storageKey, errorCode, errorMessage, stockRejection };
}
