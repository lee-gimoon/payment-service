import type { PaymentAttemptStatus, PaymentStatus } from "../types/payment";

const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  READY: "결제 대기",
  PROCESSING: "처리 중",
  SUCCEEDED: "결제 완료",
  FAILED: "결제 실패",
  UNKNOWN: "결제 확인 필요",
  REVIEW_REQUIRED: "결제 확인 지연"
};
export function paymentStatusLabel(status: PaymentStatus): string {
  return PAYMENT_STATUS_LABELS[status];
}

const ATTEMPT_STATUS_LABELS: Record<PaymentAttemptStatus, string> = {
  STARTED: "결제창 진행 중",
  AUTH_CANCELED: "결제 인증 취소",
  AUTH_FAILED: "결제 인증 실패",
  PROCESSING: "승인 처리 중",
  SUCCEEDED: "승인 완료",
  FAILED: "승인 실패",
  REVIEW_REQUIRED: "결제 확인 필요"
};

export function paymentAttemptStatusLabel(status: PaymentAttemptStatus): string {
  return ATTEMPT_STATUS_LABELS[status];
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
