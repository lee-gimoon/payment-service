import type { Order } from "../types/payment";

interface TossPaymentRequest {
  method: "CARD";
  amount: {
    currency: string;
    value: number;
  };
  orderId: string;
  orderName: string;
  successUrl: string;
  failUrl: string;
  card: {
    flowMode: "DEFAULT";
  };
}

interface TossPayment {
  requestPayment(request: TossPaymentRequest): Promise<void>;
}

interface TossPaymentsClient {
  payment(options: { customerKey: string }): TossPayment;
}

type TossPaymentsFactory = (clientKey: string) => TossPaymentsClient;

declare global {
  interface Window {
    TossPayments?: TossPaymentsFactory;
  }
}

const TOSS_SDK_URL = "https://js.tosspayments.com/v2/standard";
let sdkPromise: Promise<TossPaymentsFactory> | null = null;

function loadTossPaymentsSdk(): Promise<TossPaymentsFactory> {
  if (window.TossPayments) {
    return Promise.resolve(window.TossPayments);
  }

  if (sdkPromise) {
    return sdkPromise;
  }

  sdkPromise = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = TOSS_SDK_URL;
    script.async = true;
    script.dataset.tossPaymentsSdk = "true";

    script.addEventListener("load", () => {
      if (window.TossPayments) {
        resolve(window.TossPayments);
        return;
      }

      sdkPromise = null;
      reject(new Error("결제창 초기화에 실패했습니다. 잠시 후 다시 시도해주세요."));
    });

    script.addEventListener("error", () => {
      script.remove();
      sdkPromise = null;
      reject(new Error("결제창을 불러오지 못했습니다. 연결을 확인하고 다시 시도해주세요."));
    });

    document.head.appendChild(script);
  });

  return sdkPromise;
}

export async function openTossPayment(order: Order, clientKey: string): Promise<void> {
  const TossPayments = await loadTossPaymentsSdk();
  const payment = TossPayments(clientKey).payment({ customerKey: order.orderId });
  const resultPageUrl = `${window.location.origin}/payment/result`;

  await payment.requestPayment({
    method: "CARD",
    amount: {
      currency: order.currency,
      value: order.amount
    },
    orderId: order.orderId,
    orderName: `${order.productName} ${order.quantity}장`,
    successUrl: `${resultPageUrl}?flow=success`,
    failUrl: `${resultPageUrl}?flow=fail&requestedOrderId=${encodeURIComponent(order.orderId)}`,
    card: { flowMode: "DEFAULT" }
  });
}
