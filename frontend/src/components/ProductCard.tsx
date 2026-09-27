import type { Product } from "../types/payment";
import { formatAmount } from "../lib/formatters";
import { TeeArtwork } from "./TeeArtwork";
import { Link } from "react-router-dom";

interface ProductCardProps {
  product: Product;
}
export function ProductCard({ product }: ProductCardProps) {
  return <article className="catalog-card">
    <Link className="catalog-art" to={`/products/${product.id}`} aria-label={`${product.name} 상품 상세 보기`} style={{ backgroundColor: product.stage }}>
      <span className="product-number">{product.id.slice(-2)}</span>
      {product.badge && <span className="product-badge">{product.badge}</span>}
      <TeeArtwork product={product} />
    </Link>
    <p className="product-meta">{product.subtitle}</p>
    <h3><Link to={`/products/${product.id}`}>{product.name}</Link></h3>
    <div className="product-bottom"><strong>{formatAmount(product.price)}</strong><span className="color-dot" style={{ backgroundColor: product.color }} aria-hidden="true" /></div>
  </article>;
}
