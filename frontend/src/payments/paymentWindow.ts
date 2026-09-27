import type { TossPaymentsWidgets } from "@tosspayments/tosspayments-sdk";
import type { Order, PaymentConfig } from "../types/payment";

// 서버가 최종 승인을 검증할 수 있는 결제수단만 허용한다.
const SUPPORTED_METHODS = new Set([
  "CARD", "TOSSPAY", "NAVERPAY", "SAMSUNGPAY", "LPAY", "KAKAOPAY",
  "PAYCO", "SSG", "APPLEPAY", "PINPAY", "KBPAY"
]);

export async function openPaymentWindow(
  widgets: TossPaymentsWidgets,
  order: Order,
  config: PaymentConfig,
  signal: AbortSignal,
  attemptId: string,
  onCancel?: () => Promise<void>
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
    // 페이지를 떠난 뒤 SDK가 생성한 창도 finally에서 제거한다.
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
        try {
          await onCancel?.();
          finish();
        } catch (error) {
          finish(error);
        }
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
            orderName: order.items?.length > 1 ? order.productName : `${order.productName} ${order.quantity}장`,
            successUrl: `${resultPageUrl}?flow=success&attemptId=${encodeURIComponent(attemptId)}`,
            // 인증 취소 응답에 orderId가 없어도 원래 주문을 조회할 수 있다.
            failUrl: `${resultPageUrl}?flow=fail&requestedOrderId=${encodeURIComponent(order.orderId)}&attemptId=${encodeURIComponent(attemptId)}`
          });
          finish();
        } catch (error) {
          finish(error);
        }
      });
    });
  } finally {
    if (onAbort) signal.removeEventListener("abort", onAbort);
    // cancel 이벤트 시점에는 SDK가 이미 창을 닫았다.
    if (!closedByUser) await paymentWindow.destroy();
  }
}
