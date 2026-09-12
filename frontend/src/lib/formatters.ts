/** 파일 역할: 여러 화면에서 금액·날짜·결제 상태를 같은 표현으로 표시하게 하는 도우미를 제공한다. */
import type { PaymentStatus } from "../types/payment";

const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  READY: "결제 대기",
  PROCESSING: "처리 중",
  SUCCEEDED: "결제 완료",
  FAILED: "결제 실패",
  UNKNOWN: "확인 필요"
};

/** 서버의 결제 상태 코드를 화면에서 읽을 수 있는 한국어 이름으로 바꾼다. */
export function paymentStatusLabel(status: PaymentStatus): string {
  return PAYMENT_STATUS_LABELS[status];
}

/** 원화 정수 금액을 천 단위 구분과 원 단위가 있는 문자열로 바꾼다. */
export function formatAmount(amount: number): string {
  return `${amount.toLocaleString("ko-KR")}원`;
}

/** 날짜를 브라우저 시간대의 한국어 표현으로 바꾸고, 값이 없거나 잘못되었으면 대시를 표시한다. */
export function formatDateTime(value: string | null): string {
  if (!value) {
    return "—";
  }

  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleString("ko-KR");
}
