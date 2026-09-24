import type { Product } from "../types/payment";
import { formatAmount } from "../lib/formatters";
import { TeeArtwork } from "./TeeArtwork";

interface ProductCardProps {
  product: Product;
  onTryOn: (product: Product) => void;
}

/** 10개 목록의 각 카드는 옷 자체를 보여주고, 선택하면 아바타 미리보기가 바뀐다. */
export function ProductCard({ product, onTryOn }: ProductCardProps) {
  return <article className="catalog-card">
    <button className="catalog-art" type="button" onClick={() => onTryOn(product)} aria-label={`${product.name} 아바타에 입혀보기`} style={{ backgroundColor: product.stage }}>
      <span className="product-number">{product.id.slice(-2)}</span>
      {product.badge && <span className="product-badge">{product.badge}</span>}
      <TeeArtwork product={product} />
    </button>
    <p className="product-meta">{product.subtitle}</p>
    <h3>{product.name}</h3>
    <div className="product-bottom"><strong>{formatAmount(product.price)}</strong><span className="color-dot" style={{ backgroundColor: product.color }} aria-hidden="true" /></div>
    <button className="try-on-link" type="button" onClick={() => onTryOn(product)}>아바타에 입혀보기 <span aria-hidden="true">↗</span></button>
  </article>;
}
