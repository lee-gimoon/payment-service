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

/*
 * 토스 SDK를 사용하는 순서와 각 단계에서 받는 값의 타입이다.
 * 1. SDK가 제공하는 TossPayments 함수의 타입은 TossPaymentsFactory다.
 * 2. TossPayments(clientKey)를 호출하면 payment(...) 메서드가 있는 TossPaymentsClient 객체를 받는다.
 * 3. client.payment({ customerKey })를 호출하면 requestPayment(...) 메서드가 있는 TossPayment 객체를 받는다.
 * 4. payment.requestPayment(request)를 호출한다. request 타입은 TossPaymentRequest, 결과 타입은 Promise<void>다.
 * 아래 선언은 이 함수와 객체를 만드는 코드가 아니라, 각 단계의 형태를 TypeScript에 알려주는 코드다.
 */
/**
 * 토스 SDK가 제공하는 결제 객체의 형태를 TypeScript에 알려주는 인터페이스다.
 * Java의 인터페이스처럼 객체가 가져야 할 메서드의 형태를 선언한다.
 * 여기서는 함수를 구현하지 않으며, 실제 requestPayment 함수는 브라우저에 로드한 토스 SDK가 제공한다.
 */
interface TossPayment {
  /** 결제 요청 정보를 받아 카드 인증 절차를 시작한다. Promise<void>는 비동기이며 반환값이 없다는 뜻이다. */
  requestPayment(request: TossPaymentRequest): Promise<void>;
}

/**
 * 고객 식별자를 지정해 결제창을 사용할 객체를 만드는 SDK 클라이언트 형식이다.
 * Java의 인터페이스처럼 객체가 가져야 할 payment 메서드의 형태를 선언한다.
 */
interface TossPaymentsClient {
  /** 지정한 고객 식별자로 결제창 요청 객체를 만든다. */
  payment(options: { customerKey: string }): TossPayment;
}

/** 공개 클라이언트 키로 토스 SDK 클라이언트를 만드는 함수의 타입이다. */
type TossPaymentsFactory = (clientKey: string) => TossPaymentsClient;

declare global {
  /**
   * 브라우저에는 이미 window 객체가 있고, Window는 그 객체의 형태를 설명하는 타입이다.
   * TossPaymentsFactory는 인자와 반환값을 설명하는 함수 타입이고, window.TossPayments는 SDK가 제공하는 실제 함수다.
   * ?는 SDK가 아직 로드되지 않아 함수가 없을 수도 있다는 뜻이다. 이 선언이 함수를 만들지는 않는다.
   */
  interface Window {
    TossPayments?: TossPaymentsFactory;
  }
}

const TOSS_SDK_URL = "https://js.tosspayments.com/v2/standard";
let sdkPromise: Promise<TossPaymentsFactory> | null = null;

/**
 * 토스 SDK를 준비하고, clientKey를 받아 TossPaymentsClient 객체를 만드는 TossPayments 함수를 돌려준다.
 * 이미 로드되어 window에 함수가 있으면 바로 사용하고, 로딩 중이면 그 작업이 끝나기를 기다린다.
 * 둘 다 아니면 SDK 스크립트를 불러온다. 실패하면 다음 호출에서 다시 시도할 수 있게 한다.
 */
function loadTossPaymentsSdk(): Promise<TossPaymentsFactory> {
  if (window.TossPayments) {
    // Promise.resolve(값)은 "결과가 이 값으로 정해진 Promise"를 만든다. await하면 이미 있는 TossPayments 함수를 바로 받는다.
    return Promise.resolve(window.TossPayments);
  }

  if (sdkPromise) {
    // SDK를 이미 불러오는 중이면 새로 불러오지 않고, 진행 중인 같은 작업이 끝나기를 기다린다.
    return sdkPromise;
  }

  // new Promise(...)에 전달한 함수는 즉시 실행되며, Promise가 resolve와 reject 함수를 인자로 건네준다.
  // 나중에 resolve(값)을 호출하면 Promise가 성공하고, 기다리던 await가 그 값을 받는다.
  // reject(오류)를 호출하면 Promise가 실패하고, 기다리던 await에 오류가 전달된다.
  // 이 Promise를 sdkPromise에 저장하고, <script>의 load 또는 error 이벤트에서 두 함수 중 하나를 호출한다.
  sdkPromise = new Promise((resolve, reject) => {
    // 새 <script> 요소를 만든다. 아직 HTML 문서에 추가하지 않았으므로 SDK 파일은 불러오지 않는다.
    const script = document.createElement("script");
    // 이 태그로 불러올 토스 SDK 자바스크립트 파일의 주소를 지정한다.
    script.src = TOSS_SDK_URL;
    // SDK 파일을 비동기로 불러오도록 설정한다.
    script.async = true;
    // 태그에 data-toss-payments-sdk="true"라는 식별용 표시를 붙인다.
    script.dataset.tossPaymentsSdk = "true";

    // addEventListener는 어떤 일이 일어났을 때 실행할 함수를 등록하는 메서드다.
    // 여기서는 <script>의 load 이벤트, 즉 SDK 파일 로딩이 끝났을 때 아래 함수를 실행한다.
    script.addEventListener("load", () => {
      // 토스 SDK 스크립트가 실행되면 브라우저의 전역 객체 window에 TossPayments 함수를 추가한다.
      // 그 함수가 실제로 추가되었는지 확인한다.
      if (window.TossPayments) {
        // 기다리던 Promise를 성공 처리하고, TossPayments 함수를 그 결과로 전달한다.
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

    // 만든 <script> 요소를 현재 브라우저 페이지의 <head>에 붙인다. 그러면 src에 지정한 토스 SDK 파일을 불러오기 시작한다.
    document.head.appendChild(script);
  });

  return sdkPromise;
}

/**
 * 서버가 정한 주문·금액으로 카드 인증창을 열고 성공·실패 시 결과 화면으로 돌아오도록 설정한다.
 * 최종 결제 승인은 복귀 후 PaymentResultPage가 우리 서버에 요청한다.
 */
export async function openTossPayment(order: Order, clientKey: string): Promise<void> {
  // SDK 로딩을 기다린 뒤, clientKey로 토스 클라이언트를 만들 수 있는 TossPayments 함수를 받는다.
  const TossPayments = await loadTossPaymentsSdk();
  // 이 상점의 clientKey로 토스 SDK를 초기화해 결제 기능을 사용할 클라이언트 객체를 만든다.
  const client = TossPayments(clientKey);
  // 이 코드에서는 주문번호를 고객 식별자로 사용해 결제창 요청 도구를 준비한다.
  const payment = client.payment({ customerKey: order.orderId });
  // 현재 사이트의 주소에 /payment/result를 붙여, 결제 후 돌아올 결과 페이지 주소를 만든다.
  const resultPageUrl = `${window.location.origin}/payment/result`;

  // 카드 결제에 필요한 금액·주문 정보와 성공·실패 URL을 토스 SDK에 전달해 결제창 열기를 요청한다.
  // payment.requestPayment(...)를 호출하면 브라우저에서 실행 중인 토스 SDK가 토스 결제 서비스와 통신해 결제창을 띄운다.
  // 우리 코드가 직접 fetch(...)를 호출하지 않아도, SDK가 브라우저에서 토스 결제창을 불러오는 네트워크 요청을 진행한다.
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
