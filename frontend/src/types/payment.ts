/** 파일 역할: Spring Boot의 주문·결제 API와 React 사이에서 주고받는 데이터의 TypeScript 타입을 정의한다. */

/** 승인·자동 확인·자동 취소 및 운영자 확인 상태를 구분한다. */
export type PaymentStatus =
  | "READY"
  | "PROCESSING"
  | "SUCCEEDED"
  | "FAILED"
  | "UNKNOWN"
  | "CANCEL_PENDING"
  | "CANCELED"
  | "REVIEW_REQUIRED";

/** 주문 응답에 포함된 승인 금액·상태·승인 및 취소 시각이다. */
export interface PaymentDetails {
  status: PaymentStatus;
  pgStatus: string | null;
  approvedAt: string | null;
  checkedAt: string | null;
  errorCode: string | null;
  message: string;
  paidAmount: number | null;
  paidCurrency: string | null;
  canceledAt: string | null;
}

/** 주문 API가 반환하는 상품·금액과 연결된 결제 상태로, 백엔드 OrderResponse에 대응한다. */
export interface Order {
  orderId: string;
  productName: string;
  quantity: number;
  amount: number;
  currency: string;
  createdAt: string;
  payment: PaymentDetails;
}

/** 브라우저용 키와 결제창형 UI 선택 설정이다. 서버 시크릿 키는 포함하지 않는다. */
export interface PaymentConfig {
  enabled: boolean;
  clientKey: string;
  paymentMethodVariantKey: string;
  agreementVariantKey: string;
}

/** 결제수단 인증 이후 최종 승인을 요청할 때 서버에 보내는 주문번호·결제 키·금액이다. */
export interface ConfirmPaymentCommand {
  orderId: string;
  paymentKey: string;
  amount: number;
}
