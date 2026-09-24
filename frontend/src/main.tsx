/** 파일 역할: index.html의 root 요소에 React를 시작하고 URL별 페이지를 연결하는 프런트엔드 진입점이다. */
import { createRoot } from "react-dom/client";
import {
  BrowserRouter,
  Navigate,
  Route,
  Routes
} from "react-router-dom";
import { PaymentResultPage } from "./pages/PaymentResultPage";
import { CartPage } from "./pages/CartPage";
import { OrderPage } from "./pages/OrderPage";
import { ProductDetailPage } from "./pages/ProductDetailPage";
import { StorePage } from "./pages/StorePage";
import { ShopProvider } from "./lib/shop";
import "./styles.css";

const root = document.getElementById("root");

if (!root) {
  throw new Error("React 루트 요소를 찾을 수 없습니다.");
}

createRoot(root).render(
  <BrowserRouter>
    <ShopProvider>
      <Routes>
        <Route path="/" element={<StorePage />} />
        <Route path="/products/:productId" element={<ProductDetailPage />} />
        <Route path="/cart" element={<CartPage />} />
        <Route path="/orders" element={<OrderPage />} />
        <Route path="/orders/:orderId" element={<OrderPage />} />
        <Route path="/payment/result" element={<PaymentResultPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </ShopProvider>
  </BrowserRouter>
);
