import type { ProductSize, ShirtSize } from "../types/payment";

/** 고른 사이즈가 품절이면 판매 중인 첫 사이즈를 고른다. 모든 사이즈가 품절이면 null이다. */
export function availableSize(sizes: ProductSize[], preferred: ShirtSize): ShirtSize | null {
  const available = sizes.filter(option => !option.soldOut).map(option => option.size);
  return available.includes(preferred) ? preferred : available[0] ?? null;
}
