/** 파일 역할: 토스 결제창의 복귀 URL을 읽어 승인 요청 데이터를 준비하고, 재방문에 필요한 임시 정보를 복원한다. */
import type { ConfirmPaymentCommand } from "../types/payment";
import {
  readSessionValue,
  writeSessionValue
} from "../lib/storage";

/** 결제창에서 돌아온 인증 결과 구분이다. success만으로 최종 결제 성공을 의미하지는 않는다. */
export type PaymentRedirectFlow = "success" | "fail" | null;

/** 결과 화면이 승인 요청 또는 주문 조회 중 무엇을 할지 판단하는 데 필요한 데이터다. */
export interface PaymentRedirectState {
  orderId: string | null;
  flow: PaymentRedirectFlow;
  confirmation: ConfirmPaymentCommand | null;
  storageKey: string | null;
}

const ORDER_ID_PATTERN = /^[a-zA-Z0-9_-]{6,64}$/;
const AMOUNT_PATTERN = /^\d{1,12}$/;

/** 출처를 신뢰할 수 없는 값이 해당 주문의 승인 요청 형식인지 검사한다. 실제 주문 금액 검증은 서버가 한다. */
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

/** 새로고침 전에 저장한 승인 정보를 읽고, JSON 형식과 주문번호가 맞는 경우에만 복원한다. */
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

/**
 * 인증 성공 URL에서 승인 정보를 읽어 임시 저장하거나, 일반 재방문이면 저장된 정보를 복원한다.
 * 처리 후 URL에서 paymentKey를 제거한다. URL·저장소를 변경하는 동작이며 서버 요청은 하지 않는다.
 */
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
