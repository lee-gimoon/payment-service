import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { AppShell } from "../components/AppShell";
import { AvatarArtwork } from "../components/TeeArtwork";
import { formatAmount } from "../lib/formatters";
import { useShop } from "../lib/shop";
import type { ShirtSize } from "../types/payment";

const SIZES: ShirtSize[] = ["S", "M", "L", "XL"];

export function ProductDetailPage() {
  const { productId } = useParams();
  const navigate = useNavigate();
  const { products, loading, catalogError, reloadProducts, addToCart } = useShop();
  const product = products.find(item => item.id === productId);
  const [size, setSize] = useState<ShirtSize>("M");
  const [error, setError] = useState("");

  useEffect(() => {
    document.title = `${product?.name ?? "상품 보기"} · MODO CLUB`;
    window.scrollTo(0, 0);
  }, [product?.name]);

  function handleAdd() {
    if (!product) return;
    if (!addToCart(product.id, size)) {
      setError("한 상품의 같은 사이즈는 최대 10장까지 담을 수 있습니다.");
      return;
    }
    navigate("/cart");
  }

  return <AppShell footerText="상품 선택 → 아바타 입혀보기 → 장바구니 → 테스트 결제" mainClassName="detail-page">
    <nav className="breadcrumb" aria-label="현재 위치"><Link to="/">스토어</Link><span aria-hidden="true">/</span><span>상품 보기</span></nav>
    {catalogError ? <div className="error" role="alert">{catalogError} <button className="secondary" type="button" onClick={reloadProducts}>다시 시도</button></div> :
      loading ? <p className="catalog-empty" role="status">상품을 불러오고 있습니다.</p> :
      !product ? <div className="empty-state"><h1>상품을 찾을 수 없습니다.</h1><Link className="primary-button" to="/">전체 상품 보기</Link></div> :
      <section className="look-section product-detail" aria-labelledby="look-title">
        <div className="look-art" style={{ backgroundColor: product.stage }}>
          <AvatarArtwork product={product} />
        </div>
        <div className="look-details">
          <p className="eyebrow">TRY IT ON</p>
          <h1 id="look-title">{product.name}</h1>
          <p className="product-meta">{product.subtitle}</p>
          <p className="look-description">선택한 티셔츠를 캐릭터 아바타에 입혀본 모습입니다. 사이즈를 고른 뒤 장바구니에 담아보세요.</p>
          <strong className="look-price">{formatAmount(product.price)}</strong>
          <fieldset className="size-field">
            <legend>사이즈 선택</legend>
            <div className="size-options">{SIZES.map(option => <button type="button" key={option} className={size === option ? "selected" : ""} aria-pressed={size === option} onClick={() => setSize(option)}>{option}</button>)}</div>
          </fieldset>
          <button className="primary-button add-to-cart" type="button" onClick={handleAdd}>장바구니 담기 <span aria-hidden="true">↗</span></button>
          {error && <p className="error" role="alert">{error}</p>}
          <p className="subtle">상품 이미지와 아바타는 시안용 일러스트입니다.</p>
        </div>
      </section>}
  </AppShell>;
}
