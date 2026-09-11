export type PaymentStatus =
  | "READY"
  | "PROCESSING"
  | "SUCCEEDED"
  | "FAILED"
  | "UNKNOWN";

export interface PaymentDetails {
  status: PaymentStatus;
  attemptId: string | null;
  pgStatus: string | null;
  approvedAt: string | null;
  checkedAt: string | null;
  errorCode: string | null;
  message: string;
  canReconcile: boolean;
}

export interface Order {
  orderId: string;
  productName: string;
  quantity: number;
  amount: number;
  currency: string;
  createdAt: string;
  payment: PaymentDetails;
}

export interface PaymentConfig {
  enabled: boolean;
  clientKey: string;
}

export interface ConfirmPaymentCommand {
  orderId: string;
  paymentKey: string;
  amount: number;
}
