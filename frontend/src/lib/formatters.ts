import type { PaymentStatus } from "../types/payment";

const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  READY: "결제 대기",
  PROCESSING: "처리 중",
  SUCCEEDED: "결제 완료",
  FAILED: "결제 실패",
  UNKNOWN: "확인 필요"
};

export function paymentStatusLabel(status: PaymentStatus): string {
  return PAYMENT_STATUS_LABELS[status];
}

export function formatAmount(amount: number): string {
  return `${amount.toLocaleString("ko-KR")}원`;
}

export function formatDateTime(value: string | null): string {
  if (!value) {
    return "—";
  }

  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleString("ko-KR");
}
