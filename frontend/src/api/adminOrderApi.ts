import { signedIn } from "../auth/keycloak";
import type { AdminOrder, AdminOrderCounts, AdminOrderSummary } from "../types/admin";
import type { Carrier, DeliveryStatus } from "../types/payment";
import { jsonBody, request } from "./http";

// 관리자 주문 관리 API. shop-admin 역할이 없는 토큰이면 서버가 403으로 거부한다.
export async function getAdminOrders(delivery: DeliveryStatus | null): Promise<AdminOrderSummary[]> {
  const query = delivery ? `?delivery=${delivery}` : "";
  return request<AdminOrderSummary[]>(`/admin/orders${query}`, await signedIn());
}

export async function getAdminOrderCounts(): Promise<AdminOrderCounts> {
  return request<AdminOrderCounts>("/admin/orders/summary", await signedIn());
}

export async function getAdminOrder(orderId: string): Promise<AdminOrder> {
  return request<AdminOrder>(`/admin/orders/${encodeURIComponent(orderId)}`, await signedIn());
}

export async function shipOrder(orderId: string, carrier: Carrier, trackingNumber: string): Promise<AdminOrder> {
  return request<AdminOrder>(`/admin/orders/${encodeURIComponent(orderId)}/shipment`,
    await signedIn(jsonBody("PUT", { carrier, trackingNumber })));
}

export async function markOrderDelivered(orderId: string): Promise<AdminOrder> {
  return request<AdminOrder>(`/admin/orders/${encodeURIComponent(orderId)}/shipment/delivered`,
    await signedIn({ method: "POST" }));
}
