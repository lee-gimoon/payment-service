import type { Carrier, DeliveryStatus } from "../types/payment";

interface CarrierInfo {
  name: string;
  /** 택배사 배송 조회 페이지. 택배사가 주소를 바꾸면 여기만 고친다. */
  trackingUrl: (trackingNumber: string) => string;
}

export const CARRIERS: Record<Carrier, CarrierInfo> = {
  CJ: { name: "CJ대한통운", trackingUrl: number => `https://trace.cjlogistics.com/next/tracking.html?wblNo=${encodeURIComponent(number)}` },
  HANJIN: { name: "한진택배", trackingUrl: number => `https://www.hanjin.com/kor/CMS/DeliveryMgr/WaybillResult.do?mCode=MN038&schLang=KR&wblnum=${encodeURIComponent(number)}` },
  LOTTE: { name: "롯데택배", trackingUrl: number => `https://www.lotteglogis.com/home/reservation/tracking/linkView?InvNo=${encodeURIComponent(number)}` },
  EPOST: { name: "우체국택배", trackingUrl: number => `https://service.epost.go.kr/trace.RetrieveDomRigiTraceList.comm?sid1=${encodeURIComponent(number)}` },
  LOGEN: { name: "로젠택배", trackingUrl: number => `https://www.ilogen.com/web/personal/trace/${encodeURIComponent(number)}` }
};

export const CARRIER_CODES = Object.keys(CARRIERS) as Carrier[];

/** 서버와 같은 송장번호 형식. 하이픈 없이 숫자만 받는다. */
export const TRACKING_NUMBER_PATTERN = "[0-9]{8,20}";

const DELIVERY_LABELS: Record<DeliveryStatus, string> = {
  PREPARING: "상품 준비 중",
  SHIPPED: "배송 중",
  DELIVERED: "배송 완료"
};

export function deliveryStatusLabel(status: DeliveryStatus): string {
  return DELIVERY_LABELS[status];
}

/** 붙여 넣은 송장번호의 하이픈·공백을 지운다. */
export function normalizeTrackingNumber(value: string): string {
  return value.replace(/[\s-]/g, "");
}
