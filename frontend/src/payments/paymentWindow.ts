/** 결제창형 SDK의 선택·취소 이벤트를 하나의 작업으로 묶어 중복 요청과 남은 창을 정리한다. */
import type { TossPaymentsWidgets } from "@tosspayments/tosspayments-sdk";
import type { Order, PaymentConfig } from "../types/payment";

// 현재 서버가 처리하는 카드·국내 간편결제만 인증을 요청한다.
// 결제 어드민의 UI에도 같은 범위만 노출한다. 새 수단은 서버 처리와 함께 추가한다.
const SUPPORTED_METHODS = new Set([
  "CARD", "TOSSPAY", "NAVERPAY", "SAMSUNGPAY", "LPAY", "KAKAOPAY",
  "PAYCO", "SSG", "APPLEPAY", "PINPAY", "KBPAY"
]);

export async function openPaymentWindow(
  widgets: TossPaymentsWidgets,
  order: Order,
  config: PaymentConfig,
  signal: AbortSignal
): Promise<void> {
  if (signal.aborted) return;
  await widgets.setAmount({ currency: "KRW", value: order.amount });
  if (signal.aborted) return;

  const paymentWindow = await widgets.renderPaymentWindow({
    variantKey: {
      paymentMethod: config.paymentMethodVariantKey || undefined,
      agreement: config.agreementVariantKey || undefined
    }
  });
  const resultPageUrl = `${window.location.origin}/payment/result`;
  let closedByUser = false;
  let onAbort: (() => void) | undefined;

  try {
    // SDK를 기다리는 동안 페이지를 떠났다면 늦게 만들어진 창도 제거한다.
    if (signal.aborted) return;
    await new Promise<void>((resolve, reject) => {
      let settled = false;
      let requesting = false;
      function finish(error?: unknown) {
        if (settled) return;
        settled = true;
        if (error !== undefined) reject(error);
        else resolve();
      }

      onAbort = () => finish();
      signal.addEventListener("abort", onAbort, { once: true });
      paymentWindow.on("cancel", async () => {
        closedByUser = true;
        finish();
      });
      paymentWindow.on("paymentRequest", async ({ paymentMethod }) => {
        if (settled || requesting) return;
        requesting = true;
        try {
          if (!SUPPORTED_METHODS.has(paymentMethod.code)) {
            throw new Error("현재는 카드·국내 간편결제만 지원합니다. 결제창을 다시 열어 해당 수단을 선택해주세요.");
          }
          await widgets.requestPayment({
            orderId: order.orderId,
            orderName: `${order.productName} ${order.quantity}장`,
            successUrl: `${resultPageUrl}?flow=success`,
            // 인증 취소 시 토스가 orderId를 생략해도 요청한 주문을 찾을 수 있다.
            failUrl: `${resultPageUrl}?flow=fail&requestedOrderId=${encodeURIComponent(order.orderId)}`
          });
          finish();
        } catch (error) {
          // SDK 이벤트 콜백의 오류도 StorePage의 runAction으로 전달한다.
          finish(error);
        }
      });
    });
  } finally {
    if (onAbort) signal.removeEventListener("abort", onAbort);
    // cancel 이벤트는 SDK가 창을 닫은 뒤 발생한다. 그 밖의 종료는 직접 정리한다.
    if (!closedByUser) await paymentWindow.destroy();
  }
}
