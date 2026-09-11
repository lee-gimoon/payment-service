import type {
  ConfirmPaymentCommand,
  Order,
  PaymentConfig
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

export function getPaymentConfig(): Promise<PaymentConfig> {
  return request<PaymentConfig>("/payment-config");
}

export function createOrder(): Promise<Order> {
  return request<Order>("/orders", { method: "POST" });
}

export function getOrder(orderId: string): Promise<Order> {
  return request<Order>(`/orders/${encodeURIComponent(orderId)}`);
}

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

export function reconcilePayment(orderId: string): Promise<Order> {
  return request<Order>(
    `/payments/${encodeURIComponent(orderId)}/reconcile`,
    { method: "POST" },
    [422]
  );
}
