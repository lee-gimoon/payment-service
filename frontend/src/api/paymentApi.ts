/** 파일 역할: React 화면에서 사용하는 Spring Boot API 경로, 요청 형식, 공통 응답 처리를 모은다. */
import type {
  CartItem,
  ConfirmPaymentCommand,
  Order,
  PaymentAttempt,
  PaymentAttemptStatus,
  PaymentConfig,
  Product
} from "../types/payment";

/** 서버의 공통 오류 응답 형식이다. 예상과 다른 응답에서도 읽을 수 있도록 필드는 선택 사항이다. */
interface ApiErrorResponse {
  code?: string;
  message?: string;
}

/** HTTP 상태 코드와 서버 오류 코드를 함께 보관하여 API 요청 실패를 화면에 전달한다. */
export class ApiRequestError extends Error {
  readonly status: number;
  readonly code?: string;

  /** 서버 오류 메시지를 Error에 담고, HTTP 상태와 업무 오류 코드를 함께 기록한다. */
  constructor(status: number, error: ApiErrorResponse) {
    super(error.message || "요청을 처리하지 못했습니다. 저장된 결과를 조회해주세요.");
    this.name = "ApiRequestError";
    this.status = status;
    this.code = error.code;
  }
}

/**
 * fetch로 요청한 JSON 응답을 읽고, 허용하지 않은 HTTP 오류는 ApiRequestError로 전달한다.
 * T는 호출자가 기대하는 응답 타입이며 런타임 검증은 아니다. acceptedErrorStatuses는 본문을 결과로 받을 오류 코드다.
 */
async function request<T>(
  path: string,
  options?: RequestInit,
  acceptedErrorStatuses: readonly number[] = []
): Promise<T> {
  const response = await fetch(path, options);
  const data = (await response.json().catch(() => ({
    message: "서버 응답을 읽지 못했습니다. 잠시 후 다시 시도해주세요."
  }))) as T | ApiErrorResponse;

  if (!response.ok && !acceptedErrorStatuses.includes(response.status)) {
    throw new ApiRequestError(response.status, data as ApiErrorResponse);
  }

  return data as T;
}

/** GET /payment-config로 결제 가능 여부와 브라우저용 클라이언트 키를 읽는다. */
export function getPaymentConfig(): Promise<PaymentConfig> {
  return request<PaymentConfig>("/payment-config");
}

/** 상품·사이즈·수량을 보내고 서버 상품 가격으로 계산된 주문을 받는다. */
export function createOrder(items: CartItem[]): Promise<Order> {
  return request<Order>("/orders", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ items })
  });
}

export function getProducts(): Promise<Product[]> {
  return request<Product[]>("/products");
}

/** GET /orders/{orderId}로 우리 서버에 저장된 주문과 결제 상태를 읽는다. */
export function getOrder(orderId: string): Promise<Order> {
  return request<Order>(`/orders/${encodeURIComponent(orderId)}`);
}

export function startPaymentAttempt(orderId: string): Promise<PaymentAttempt> {
  return request<PaymentAttempt>(`/orders/${encodeURIComponent(orderId)}/payment-attempts`, { method: "POST" });
}

export function recordAuthenticationResult(
  attemptId: string,
  status: Extract<PaymentAttemptStatus, "AUTH_CANCELED" | "AUTH_FAILED">,
  errorCode: string | null = null
): Promise<PaymentAttempt> {
  return request<PaymentAttempt>(`/payment-attempts/${encodeURIComponent(attemptId)}/authentication-result`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ status, errorCode })
  });
}

/**
 * 결제수단 인증으로 받은 정보를 POST /payments/confirm에 보내 최종 승인을 요청한다.
 * HTTP 422도 결제 거절 상태를 담은 Order 응답이므로 일반 요청 오류 대신 화면에 표시할 결과로 받는다.
 */
export function confirmPayment(command: ConfirmPaymentCommand): Promise<Order> {
  return request<Order>(
    "/payments/confirm",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(command)
    },
    [422]
  );
}
