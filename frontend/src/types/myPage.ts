import type { OrderStatus } from "./payment";

/** 마이페이지 주문 목록의 한 줄. 결제 상세는 주문 확인 화면에서 본다. */
export interface OrderSummary {
  orderId: string;
  productName: string;
  quantity: number;
  amount: number;
  currency: string;
  createdAt: string;
  status: OrderStatus;
}

export interface Address {
  id: string;
  label: string;
  recipientName: string;
  /** 숫자만 담긴다. 화면에서는 formatPhone으로 하이픈을 넣어 보여준다. */
  phone: string;
  postalCode: string;
  address: string;
  addressDetail: string;
  defaultAddress: boolean;
}

export interface AddressInput {
  label: string;
  recipientName: string;
  phone: string;
  postalCode: string;
  address: string;
  addressDetail: string;
  makeDefault: boolean;
}
