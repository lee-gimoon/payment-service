/** 파일 역할: 여러 화면에서 금액·날짜·결제 상태를 같은 표현으로 표시하게 하는 도우미를 제공한다. */
import type { PaymentStatus } from "../types/payment";

const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  READY: "결제 대기",
  PROCESSING: "처리 중",
  SUCCEEDED: "결제 완료",
  FAILED: "결제 실패",
  UNKNOWN: "결제 확인 필요",
  CANCEL_PENDING: "취소 결과 확인 필요",
  CANCELED: "결제 취소 완료",
  REVIEW_REQUIRED: "결제 확인 지연"
};

/**
 * 서버의 결제 상태 코드를 화면에서 읽을 수 있는 한국어 이름으로 바꾼다.
 * 예: paymentStatusLabel("READY")는 "결제 대기", paymentStatusLabel("SUCCEEDED")는 "결제 완료"를 반환한다.
 */
export function paymentStatusLabel(status: PaymentStatus): string {
  return PAYMENT_STATUS_LABELS[status];
}

/**
 * 원화 정수 금액을 천 단위 쉼표와 원 단위가 붙은 문자열로 바꾼다.
 * 예: formatAmount(10000)은 "10,000원", formatAmount(1234567)은 "1,234,567원"을 반환한다.
 */
export function formatAmount(amount: number): string {
  return `${amount.toLocaleString("ko-KR")}원`;
}

/**
 * 날짜를 브라우저 시간대의 한국어 표현으로 바꾸고, 값이 없거나 잘못되었으면 대시를 표시한다.
 * 예: 한국 시간대에서 formatDateTime("2026-09-14T03:00:00Z")은 2026년 9월 14일 오후 12시로 표시된다.
 * formatDateTime(null)이나 formatDateTime("잘못된 날짜")는 "—"를 반환한다.
 */
export function formatDateTime(value: string | null): string {
  if (!value) {
    return "—";
  }

  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleString("ko-KR");
}
