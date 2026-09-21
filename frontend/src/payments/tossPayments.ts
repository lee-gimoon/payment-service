/** 공식 SDK로 카드·간편결제 인증창을 연다. 최종 승인은 복귀 후 서버에서 한다. */
import { ANONYMOUS, loadTossPayments } from "@tosspayments/tosspayments-sdk";
import type { Order } from "../types/payment";

export async function openTossPayment(order: Order, clientKey: string): Promise<void> {
  const tossPayments = await loadTossPayments(clientKey);
  // 로그인 없는 비회원 결제다. customerKey와 orderId는 서로 다른 역할이다.
  const payment = tossPayments.payment({ customerKey: ANONYMOUS });
  const resultPageUrl = `${window.location.origin}/payment/result`;

  await payment.requestPayment({
    method: "CARD",
    amount: { currency: "KRW", value: order.amount },
    orderId: order.orderId,
    orderName: `${order.productName} ${order.quantity}장`,
    successUrl: `${resultPageUrl}?flow=success`,
    // 사용자가 인증을 취소하면 토스가 orderId를 보내지 않을 수 있다.
    failUrl: `${resultPageUrl}?flow=fail&requestedOrderId=${encodeURIComponent(order.orderId)}`
  });
}
