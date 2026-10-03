import type { Delivery, OrderItem, OrderStatus, ShippingInfo } from "./payment";

/** 관리자 주문 목록의 한 줄. 받는 분 이름만 담고 전체 주소는 상세에서 본다. */
export interface AdminOrderSummary {
  orderId: string;
  productName: string;
  quantity: number;
  amount: number;
  paidAt: string | null;
  recipientName: string | null;
  delivery: Delivery | null;
}

export interface AdminOrder {
  orderId: string;
  productName: string;
  quantity: number;
  amount: number;
  currency: string;
  items: OrderItem[];
  shipping: ShippingInfo | null;
  createdAt: string;
  paidAt: string | null;
  status: OrderStatus;
  delivery: Delivery | null;
}

export interface AdminOrderCounts {
  preparing: number;
  shipping: number;
}
