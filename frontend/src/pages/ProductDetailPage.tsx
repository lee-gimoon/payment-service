import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { AppShell } from "../components/AppShell";
import { AvatarArtwork } from "../components/TeeArtwork";
import { formatAmount } from "../lib/formatters";
import { useShop } from "../lib/shop";
import { addableQuantity, availableSize, remainingLabel } from "../lib/sizes";
import type { ShirtSize } from "../types/payment";

export function ProductDetailPage() {
  const { productId } = useParams();
  const navigate = useNavigate();
  const { products, loading, catalogError, reloadProducts, refreshProduct, cart, addToCart } = useShop();
  const product = products.find(item => item.id === productId);
  const [size, setSize] = useState<ShirtSize>("M");
  const [chosenQuantity, setChosenQuantity] = useState(1);
  const [error, setError] = useState("");
  const selectedSize = product ? availableSize(product.sizes, size) : null;
  const remaining = product?.sizes.find(option => option.size === selectedSize)?.remaining ?? 0;
  const inCart = cart.find(item => item.productId === productId && item.size === selectedSize)?.quantity ?? 0;
  const maxQuantity = addableQuantity(remaining, inCart);
  // 사이즈를 바꾸거나 남은 수량이 줄어도 고를 수 있는 범위 안으로 맞춘다.
  const quantity = Math.max(1, Math.min(chosenQuantity, maxQuantity));

  useEffect(() => {
    if (productId) refreshProduct(productId);
  }, [productId, refreshProduct]);

  useEffect(() => {
    document.title = `${product?.name ?? "상품 보기"} · MODO CLUB`;
    window.scrollTo(0, 0);
  }, [product?.name]);

  function handleAdd() {
    if (!product || !selectedSize || maxQuantity === 0) return;
    if (!addToCart(product.id, selectedSize, quantity)) {
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
          <p className="look-description">선택한 티셔츠를 캐릭터 아바타에 입혀본 모습입니다. 사이즈와 수량을 고른 뒤 장바구니에 담아보세요.</p>
          <strong className="look-price">{formatAmount(product.price)}</strong>
          <fieldset className="size-field">
            <legend>사이즈 선택</legend>
            <div className="size-options">{product.sizes.map(option => <button type="button" key={option.size} className={selectedSize === option.size ? "selected" : ""} aria-pressed={selectedSize === option.size} disabled={option.soldOut} onClick={() => setSize(option.size)}>{option.size}{option.soldOut && <small> 품절</small>}</button>)}</div>
            {selectedSize && <p className="stock-note" role="status">{remainingLabel(remaining)}</p>}
          </fieldset>
          {selectedSize ? <>
            <div className="quantity-field">
              <span id="detail-quantity-label">수량</span>
              <div className="quantity-control" role="group" aria-labelledby="detail-quantity-label">
                <button type="button" aria-label="수량 줄이기" onClick={() => setChosenQuantity(quantity - 1)} disabled={quantity <= 1}>−</button>
                <span>{maxQuantity === 0 ? 0 : quantity}</span>
                <button type="button" aria-label="수량 늘리기" onClick={() => setChosenQuantity(quantity + 1)} disabled={quantity >= maxQuantity}>+</button>
              </div>
            </div>
            {maxQuantity === 0 && <p className="subtle" role="status">장바구니에 이미 {inCart}장을 담아 이 사이즈는 더 담을 수 없습니다.</p>}
            <button className="primary-button add-to-cart" type="button" onClick={handleAdd} disabled={maxQuantity === 0}>장바구니 담기 <span aria-hidden="true">↗</span></button>
          </> : <><button className="primary-button add-to-cart" type="button" disabled>품절</button><p className="subtle" role="status">모든 사이즈가 품절되었습니다.</p></>}
          {error && <p className="error" role="alert">{error}</p>}
          <p className="subtle">상품 이미지와 아바타는 시안용 일러스트입니다.</p>
        </div>
      </section>}
  </AppShell>;
}
