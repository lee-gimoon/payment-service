import { useEffect, useState } from "react";
import { AppShell } from "../components/AppShell";
import { AvatarArtwork } from "../components/TeeArtwork";
import { ProductCard } from "../components/ProductCard";
import { useShop } from "../lib/shop";

const FILTERS = ["전체", "베이식", "그래픽", "스트라이프"];

export function StorePage() {
  const { products, loading, catalogError, reloadProducts } = useShop();
  const [filter, setFilter] = useState("전체");
  const featuredProduct = products[0];
  const filteredProducts = filter === "전체" ? products : products.filter(product => product.category === filter);

  useEffect(() => {
    document.title = "MODO CLUB · 아바타 티셔츠 스토어";
    if (window.location.hash === "#products") {
      document.getElementById("products")?.scrollIntoView();
    }
  }, []);

  return <AppShell footerText="상품 선택 → 아바타 입혀보기 → 장바구니 → 테스트 결제">
    <section className="hero">
      <div className="hero-copy">
        <p className="eyebrow">WELCOME TO MODO CLUB</p>
        <h1>오늘의 티,<br />오늘의 나.</h1>
        <p>원하는 옷을 고르고,<br />캐릭터 아바타에 입혀보세요.</p>
        <a className="primary-button hero-action" href="#products">룩 보기</a>
      </div>
      <div className="hero-art" aria-hidden="true">
        {featuredProduct && <div className="hero-avatar" style={{ backgroundColor: featuredProduct.stage }}><AvatarArtwork product={featuredProduct} /><span>MY AVATAR</span></div>}
      </div>
    </section>

    <section id="products" className="catalog-section landing-catalog" aria-labelledby="catalog-title">
      <div className="section-heading">
        <div><p className="eyebrow">T-SHIRT SHOP</p><h2 id="catalog-title">티셔츠 <span>{products.length}개 상품</span></h2></div>
        <p>마음에 드는 티셔츠를 고르면 상품 페이지에서 아바타 착용 모습과 사이즈를 확인할 수 있어요.</p>
      </div>
      <div className="filter-row" role="group" aria-label="상품 분류">
        {FILTERS.map(option => <button key={option} className={filter === option ? "active" : ""} type="button" aria-pressed={filter === option} onClick={() => setFilter(option)}>{option === "전체" ? `전체 ${products.length}` : option}</button>)}
      </div>
      {catalogError ? <div className="error" role="alert">{catalogError} <button className="secondary" type="button" onClick={reloadProducts}>다시 시도</button></div> :
        loading ? <p className="catalog-empty" role="status">상품을 불러오고 있습니다.</p> :
        <div className="product-grid">{filteredProducts.map(product => <ProductCard key={product.id} product={product} />)}</div>}
    </section>
  </AppShell>;
}
