import { readLocalValue, removeLocalValue, writeLocalValue } from "./storage.ts";

type CustomerId = string | null | undefined;
type OrderKey = "pendingOrderId" | "lastOrderId";

function memberKey(customerId: CustomerId, orderKey: OrderKey): string | null {
  if (!customerId?.trim()) return null;
  return `modoOrders:${encodeURIComponent(customerId)}:${orderKey}`;
}

function readOrderId(customerId: CustomerId, orderKey: OrderKey): string | null {
  const key = memberKey(customerId, orderKey);
  // 기존 전역 키는 주문한 회원을 알 수 없으므로 자동으로 가져오지 않는다.
  return key ? readLocalValue(key) : null;
}

function saveOrderId(customerId: CustomerId, orderKey: OrderKey, orderId: string): void {
  const key = memberKey(customerId, orderKey);
  if (key) writeLocalValue(key, orderId);
}

export function getPendingOrderId(customerId: CustomerId): string | null {
  return readOrderId(customerId, "pendingOrderId");
}

export function getLastOrderId(customerId: CustomerId): string | null {
  return readOrderId(customerId, "lastOrderId");
}

export function savePendingOrderId(customerId: CustomerId, orderId: string): void {
  saveOrderId(customerId, "pendingOrderId", orderId);
}

export function saveLastOrderId(customerId: CustomerId, orderId: string): void {
  saveOrderId(customerId, "lastOrderId", orderId);
}

export function clearPendingOrderId(customerId: CustomerId, expectedOrderId: string): void {
  const key = memberKey(customerId, "pendingOrderId");
  // 이전 주문의 늦은 성공 응답이 새로 만든 대기 주문을 지우지 않게 한다.
  if (key && readLocalValue(key) === expectedOrderId) removeLocalValue(key);
}
