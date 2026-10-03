export type PaymentStatus =
  | "READY"
  | "APPROVING"
  | "SUCCEEDED"
  | "FAILED"
  | "UNKNOWN"
  | "REVIEW_REQUIRED";

export interface PaymentDetails {
  status: PaymentStatus;
  pgStatus: string | null;
  approvedAt: string | null;
  checkedAt: string | null;
  errorCode: string | null;
  message: string;
  paidAmount: number | null;
  paidCurrency: string | null;
}

export interface Order {
  orderId: string;
  productName: string;
  quantity: number;
  amount: number;
  currency: string;
  items: OrderItem[];
  createdAt: string;
  status: OrderStatus;
  latestAttempt: PaymentAttempt | null;
  payment: PaymentDetails;
}

export type OrderStatus = "PENDING_PAYMENT" | "PAYMENT_IN_PROGRESS" | "PAID";

export type PaymentAttemptStatus = "STARTED" | "AUTH_CANCELED" | "AUTH_FAILED" |
  "APPROVING" | "UNKNOWN" | "REVIEW_REQUIRED" | "SUCCEEDED" | "FAILED";

export interface PaymentAttempt {
  id: string;
  orderId?: string;
  status: PaymentAttemptStatus;
  startedAt?: string;
  finishedAt?: string | null;
  errorCode?: string | null;
}

export interface Product {
  id: string;
  name: string;
  subtitle: string;
  category: string;
  price: number;
  color: string;
  stage: string;
  artwork: string;
  badge: string;
  sizes: ProductSize[];
}

export type ShirtSize = "S" | "M" | "L" | "XL";

export interface ProductSize {
  size: ShirtSize;
  soldOut: boolean;
  /** 남은 수량. 서버는 실제 재고량을 숨기려고 10장 이상이면 10으로 보낸다. */
  remaining: number;
}

export interface CartItem {
  productId: string;
  size: ShirtSize;
  quantity: number;
}

export interface OrderItem extends CartItem {
  productName: string;
  unitPrice: number;
}

export interface PaymentConfig {
  enabled: boolean;
  clientKey: string;
  paymentMethodVariantKey: string;
  agreementVariantKey: string;
}

export interface ConfirmPaymentCommand {
  orderId: string;
  paymentKey: string;
  amount: number;
  attemptId: string;
}
