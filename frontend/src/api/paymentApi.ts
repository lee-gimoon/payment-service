import { getAccessToken } from "../auth/keycloak";
import type {
  CartItem,
  ConfirmPaymentCommand,
  Order,
  PaymentAttempt,
  PaymentAttemptStatus,
  PaymentConfig,
  Product
} from "../types/payment";

interface ApiErrorResponse {
  code?: string;
  message?: string;
}

export class ApiRequestError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(status: number, error: ApiErrorResponse) {
    super(error.message || "요청을 처리하지 못했습니다. 저장된 결과를 조회해주세요.");
    this.name = "ApiRequestError";
    this.status = status;
    this.code = error.code;
  }
}

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

// 주문·결제 API는 로그인한 회원의 access token을 붙인다. 로그인하지 않았으면 서버가 401로 거부한다.
async function signedIn(options: RequestInit = {}): Promise<RequestInit> {
  const token = await getAccessToken();
  if (!token) return options;
  const headers = new Headers(options.headers);
  headers.set("Authorization", `Bearer ${token}`);
  return { ...options, headers };
}

export function getPaymentConfig(): Promise<PaymentConfig> {
  return request<PaymentConfig>("/payment-config");
}

export async function createOrder(items: CartItem[]): Promise<Order> {
  return request<Order>("/orders", await signedIn({
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ items })
  }));
}

export function getProducts(): Promise<Product[]> {
  return request<Product[]>("/products");
}

export async function getOrder(orderId: string): Promise<Order> {
  return request<Order>(`/orders/${encodeURIComponent(orderId)}`, await signedIn());
}

export async function startPaymentAttempt(orderId: string): Promise<PaymentAttempt> {
  return request<PaymentAttempt>(`/orders/${encodeURIComponent(orderId)}/payment-attempts`,
    await signedIn({ method: "POST" }));
}

export async function recordAuthenticationResult(
  attemptId: string,
  status: Extract<PaymentAttemptStatus, "AUTH_CANCELED" | "AUTH_FAILED">,
  errorCode: string | null = null
): Promise<PaymentAttempt> {
  return request<PaymentAttempt>(`/payment-attempts/${encodeURIComponent(attemptId)}/authentication-result`,
    await signedIn({
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status, errorCode })
    }));
}

export async function confirmPayment(command: ConfirmPaymentCommand): Promise<Order> {
  // 422 응답에도 서버가 저장한 결제 실패 결과가 포함된다.
  return request<Order>(
    "/payments/confirm",
    await signedIn({
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(command)
    }),
    [422]
  );
}
