import { useEffect, useRef, useState } from "react";
import { createOrder, getOrder, getPaymentConfig, getProducts } from "../api/paymentApi";
import { AppShell } from "../components/AppShell";
import { AvatarArtwork } from "../components/TeeArtwork";
import { CheckoutCard } from "../components/CheckoutCard";
import { OrderLookup } from "../components/OrderLookup";
import { OrderResultCard } from "../components/OrderResultCard";
import { ProductCard } from "../components/ProductCard";
import { formatAmount } from "../lib/formatters";
import { readLocalValue, writeLocalValue } from "../lib/storage";
import { openTossPayment } from "../payments/tossPayments";
import type { CartItem, Order, PaymentConfig, Product, ShirtSize } from "../types/payment";

const LAST_ORDER_ID_KEY = "lastOrderId";
const SIZES: ShirtSize[] = ["S", "M", "L", "XL"];
const FILTERS = ["전체", "베이식", "그래픽", "스트라이프"];

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "연결을 확인한 뒤 주문 결과를 조회해주세요.";
}

export function StorePage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [selectedSize, setSelectedSize] = useState<ShirtSize>("M");
  const [filter, setFilter] = useState("전체");
  const [cart, setCart] = useState<CartItem[]>([]);
  const [currentOrder, setCurrentOrder] = useState<Order | null>(null);
  const [paymentConfig, setPaymentConfig] = useState<PaymentConfig | null>(null);
  const [lookupOrderId, setLookupOrderId] = useState("");
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const busyRef = useRef(true);
  const paymentAbortRef = useRef<AbortController | null>(null);

  const selectedProduct = products.find(product => product.id === selectedId) ?? products[0];
  const filteredProducts = filter === "전체" ? products : products.filter(product => product.category === filter);
  const cartCount = cart.reduce((sum, item) => sum + item.quantity, 0);

  function showOrder(order: Order) {
    setCurrentOrder(order);
    setLookupOrderId(order.orderId);
    writeLocalValue(LAST_ORDER_ID_KEY, order.orderId);
  }

  async function runAction(work: () => Promise<void>) {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setError("");
    try {
      await work();
    } catch (actionError) {
      if (!paymentAbortRef.current?.signal.aborted) setError(errorMessage(actionError));
    } finally {
      if (!paymentAbortRef.current?.signal.aborted) {
        busyRef.current = false;
        setBusy(false);
      }
    }
  }

  useEffect(() => {
    let active = true;
    const paymentAbort = new AbortController();
    paymentAbortRef.current = paymentAbort;
    document.title = "MODO CLUB · 아바타 티셔츠 스토어";

    async function initializeStore() {
      const savedOrderId = readLocalValue(LAST_ORDER_ID_KEY);
      if (savedOrderId) setLookupOrderId(savedOrderId);
      const [catalogResult, configResult, orderResult] = await Promise.allSettled([
        getProducts(),
        getPaymentConfig(),
        savedOrderId ? getOrder(savedOrderId) : Promise.resolve(null)
      ]);
      if (!active) return;
      if (catalogResult.status === "fulfilled") {
        setProducts(catalogResult.value);
        setSelectedId(catalogResult.value[0]?.id ?? "");
      } else {
        setError("상품 목록을 불러오지 못했습니다. 서버 연결을 확인해주세요.");
      }
      if (configResult.status === "fulfilled") setPaymentConfig(configResult.value);
      else setPaymentConfig({ enabled: false, clientKey: "", paymentMethodVariantKey: "", agreementVariantKey: "" });
      if (orderResult.status === "fulfilled" && orderResult.value) showOrder(orderResult.value);
      busyRef.current = false;
      setBusy(false);
    }
    void initializeStore();
    return () => {
      active = false;
      paymentAbort.abort();
    };
  }, []);

  function handleTryOn(product: Product) {
    setSelectedId(product.id);
    setSelectedSize("M");
    document.getElementById("look-preview")?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  function handleAddToCart() {
    if (!selectedProduct) return;
    const existing = cart.find(item => item.productId === selectedProduct.id && item.size === selectedSize);
    if (existing && existing.quantity >= 10) {
      setError("한 상품의 같은 사이즈는 최대 10장까지 담을 수 있습니다.");
      return;
    }
    setCart(previous => {
      const found = previous.find(item => item.productId === selectedProduct.id && item.size === selectedSize);
      return found
        ? previous.map(item => item === found ? { ...item, quantity: item.quantity + 1 } : item)
        : [...previous, { productId: selectedProduct.id, size: selectedSize, quantity: 1 }];
    });
    setError("");
  }

  function handleQuantityChange(productId: string, size: ShirtSize, delta: number) {
    setCart(previous => previous.flatMap(item => {
      if (item.productId !== productId || item.size !== size) return [item];
      const quantity = Math.min(10, item.quantity + delta);
      return quantity > 0 ? [{ ...item, quantity }] : [];
    }));
  }

  function handleRemove(productId: string, size: ShirtSize) {
    setCart(previous => previous.filter(item => item.productId !== productId || item.size !== size));
  }

  function handleCreateOrder() {
    if (cart.length === 0) return;
    void runAction(async () => {
      const order = await createOrder(cart);
      showOrder(order);
      setCart([]);
      document.getElementById("cart")?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
  }

  function handleLookup() {
    void runAction(async () => showOrder(await getOrder(lookupOrderId.trim())));
  }

  function handleRefresh() {
    if (!currentOrder) return;
    void runAction(async () => showOrder(await getOrder(currentOrder.orderId)));
  }

  function handlePayment() {
    if (!currentOrder) return;
    void runAction(async () => {
      if (!paymentConfig?.enabled) throw new Error("테스트 결제 설정을 확인해주세요.");
      const latestOrder = await getOrder(currentOrder.orderId);
      const signal = paymentAbortRef.current?.signal;
      if (!signal || signal.aborted) return;
      showOrder(latestOrder);
      if (latestOrder.payment.status === "READY") {
        await openTossPayment(latestOrder, paymentConfig, signal);
      }
    });
  }

  return <AppShell footerText="상품 선택 → 아바타 입혀보기 → 주문 → 테스트 결제" cartCount={cartCount}>
    <section className="hero">
      <div className="hero-copy">
        <p className="eyebrow">WELCOME TO MODO CLUB</p>
        <h1>오늘의 티,<br />오늘의 나.</h1>
        <p>원하는 옷을 고르고,<br />캐릭터 아바타에 입혀보세요.</p>
        <a className="primary-button hero-action" href="#look-preview">룩 보기</a>
      </div>
      <div className="hero-art" aria-hidden="true">
        {selectedProduct && <div className="hero-avatar" style={{ backgroundColor: selectedProduct.stage }}><AvatarArtwork product={selectedProduct} /><span>MY AVATAR</span></div>}
      </div>
    </section>

    <section id="look-preview" className="look-section" aria-labelledby="look-title">
      <div className="look-art" style={{ backgroundColor: selectedProduct?.stage ?? "#e9eeff" }}>
        {selectedProduct && <AvatarArtwork product={selectedProduct} />}
      </div>
      <div className="look-details">
        <p className="eyebrow">YOUR AVATAR</p>
        <h2 id="look-title">아바타에 입혀보기</h2>
        {selectedProduct ? <>
          <p className="look-description">선택한 티셔츠를 같은 캐릭터에 입혀 보여드립니다.</p>
          <h3>{selectedProduct.name}</h3>
          <p className="product-meta">{selectedProduct.subtitle}</p>
          <strong className="look-price">{formatAmount(selectedProduct.price)}</strong>
          <fieldset className="size-field">
            <legend>사이즈 선택</legend>
            <div className="size-options">{SIZES.map(size => <button type="button" key={size} className={selectedSize === size ? "selected" : ""} aria-pressed={selectedSize === size} onClick={() => setSelectedSize(size)}>{size}</button>)}</div>
          </fieldset>
          <button className="primary-button add-to-cart" type="button" onClick={handleAddToCart} disabled={busy}>장바구니 담기 <span aria-hidden="true">↗</span></button>
          <p className="subtle">상품 이미지와 아바타는 시안용 일러스트입니다.</p>
        </> : <p role="status">상품을 불러오고 있습니다.</p>}
      </div>
    </section>

    <section id="products" className="catalog-section" aria-labelledby="catalog-title">
      <div className="section-heading">
        <div><p className="eyebrow">T-SHIRT SHOP</p><h2 id="catalog-title">티셔츠 <span>{products.length}개 상품</span></h2></div>
        <p>마음에 드는 디자인을 골라보세요. 고른 옷은 아바타에 입혀볼 수 있어요.</p>
      </div>
      <div className="filter-row" role="group" aria-label="상품 분류">
        {FILTERS.map(option => <button key={option} className={filter === option ? "active" : ""} type="button" aria-pressed={filter === option} onClick={() => setFilter(option)}>{option === "전체" ? `전체 ${products.length}` : option}</button>)}
      </div>
      {products.length === 0 ? <p className="catalog-empty" role="status">{busy ? "상품을 불러오고 있습니다." : "상품 목록을 표시할 수 없습니다."}</p> :
        <div className="product-grid">{filteredProducts.map(product => <ProductCard key={product.id} product={product} onTryOn={handleTryOn} />)}</div>}
    </section>

    <CheckoutCard cart={cart} products={products} order={currentOrder} paymentConfig={paymentConfig} busy={busy}
      onQuantityChange={handleQuantityChange} onRemove={handleRemove} onCreateOrder={handleCreateOrder} onPay={handlePayment} />
    <OrderLookup orderId={lookupOrderId} busy={busy} onOrderIdChange={setLookupOrderId} onSubmit={handleLookup} />
    {currentOrder && <OrderResultCard order={currentOrder} busy={busy} onRefresh={handleRefresh} />}
    {error && <p className="error" role="alert">{error}</p>}
  </AppShell>;
}
