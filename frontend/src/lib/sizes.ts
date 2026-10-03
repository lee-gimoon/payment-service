import type { ProductSize, ShirtSize } from "../types/payment";

/** 한 상품의 같은 사이즈는 장바구니와 주문에 최대 10장까지 담는다. 서버도 남은 수량을 10까지만 알려준다. */
export const MAX_PER_OPTION = 10;

/** 고른 사이즈가 품절이면 판매 중인 첫 사이즈를 고른다. 모든 사이즈가 품절이면 null이다. */
export function availableSize(sizes: ProductSize[], preferred: ShirtSize): ShirtSize | null {
  const available = sizes.filter(option => !option.soldOut).map(option => option.size);
  return available.includes(preferred) ? preferred : available[0] ?? null;
}

/** 지금 더 담을 수 있는 수량. 남은 수량과 한 옵션 한도 중 작은 값에서 이미 담은 수량을 뺀다. */
export function addableQuantity(remaining: number, inCart: number): number {
  return Math.max(0, Math.min(MAX_PER_OPTION, remaining) - inCart);
}

/** 서버는 10장 이상 남은 사이즈를 10으로 보내므로 10은 "10장 이상"으로 읽는다. */
export function remainingLabel(remaining: number): string {
  if (remaining <= 0) return "품절";
  return remaining >= MAX_PER_OPTION ? `남은 수량 ${MAX_PER_OPTION}장 이상` : `남은 수량 ${remaining}장`;
}
