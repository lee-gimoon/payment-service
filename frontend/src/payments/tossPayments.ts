/** 파일 역할: 토스 브라우저 SDK를 불러오고, 서버에서 만든 주문 정보로 카드 인증창을 연다. */
import type { Order } from "../types/payment";

/** 이 프로젝트가 카드 결제창에 전달하는 주문·금액·복귀 URL의 형식이다. */
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

/** 토스 SDK의 기능 중 이 프로젝트에서 사용하는 결제창 호출 부분만 표현한다. */
interface TossPayment {
  /** 카드 인증창을 열고, 인증 결과에 맞는 복귀 URL로 이동하는 절차를 시작한다. */
  requestPayment(request: TossPaymentRequest): Promise<void>;
}

/** 고객 식별자를 지정해 결제창을 사용할 객체를 만드는 SDK 클라이언트 형식이다. */
interface TossPaymentsClient {
  /** 지정한 고객 식별자로 결제창 요청 객체를 만든다. */
  payment(options: { customerKey: string }): TossPayment;
}

/** 공개 클라이언트 키로 토스 SDK 클라이언트를 만드는 함수의 타입이다. */
type TossPaymentsFactory = (clientKey: string) => TossPaymentsClient;

declare global {
  /** 외부 스크립트가 window에 추가하는 TossPayments 함수를 TypeScript가 인식하도록 확장한다. */
  interface Window {
    TossPayments?: TossPaymentsFactory;
  }
}

const TOSS_SDK_URL = "https://js.tosspayments.com/v2/standard";
let sdkPromise: Promise<TossPaymentsFactory> | null = null;

/** 이미 로드한 SDK나 로딩 중인 Promise를 재사용하며, 로딩 실패 후에는 다시 시도할 수 있게 한다. */
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

    // 스크립트 로딩 후 실제 SDK 함수가 등록되었는지 확인하고 대기 중인 호출자에게 전달한다.
    script.addEventListener("load", () => {
      if (window.TossPayments) {
        resolve(window.TossPayments);
        return;
      }

      sdkPromise = null;
      reject(new Error("결제창 초기화에 실패했습니다. 잠시 후 다시 시도해주세요."));
    });

    // 다운로드 실패 시 스크립트와 캐시를 정리하여 다음 결제 버튼 클릭에서 재시도할 수 있게 한다.
    script.addEventListener("error", () => {
      script.remove();
      sdkPromise = null;
      reject(new Error("결제창을 불러오지 못했습니다. 연결을 확인하고 다시 시도해주세요."));
    });

    document.head.appendChild(script);
  });

  return sdkPromise;
}

/**
 * 서버가 정한 주문·금액으로 카드 인증창을 열고 성공·실패 시 결과 화면으로 돌아오도록 설정한다.
 * 최종 결제 승인은 복귀 후 PaymentResultPage가 우리 서버에 요청한다.
 */
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
