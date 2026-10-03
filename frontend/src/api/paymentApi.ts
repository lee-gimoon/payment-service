import type {
  CartItem,
  ConfirmPaymentCommand,
  Order,
  PaymentAttempt,
  PaymentAttemptStatus,
  PaymentConfig,
  Product
} from "../types/payment";
import { signedIn } from "../auth/keycloak";
import { request } from "./http";

export { ApiRequestError } from "./http";

export function getPaymentConfig(): Promise<PaymentConfig> {
  return request<PaymentConfig>("/payment-config");
}

/** 서버가 내 배송지(addressId)를 주문에 복사한다. 빈 배송 메모는 보내지 않는다. */
export async function createOrder(items: CartItem[], addressId: string, deliveryMemo: string): Promise<Order> {
  return request<Order>("/orders", await signedIn({
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ items, addressId, deliveryMemo: deliveryMemo.trim() || null })
  }));
}

export function getProducts(): Promise<Product[]> {
  return request<Product[]>("/products");
}

export function getProduct(productId: string): Promise<Product> {
  return request<Product>(`/products/${encodeURIComponent(productId)}`);
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
