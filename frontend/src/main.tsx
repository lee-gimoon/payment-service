import { createRoot } from "react-dom/client";
import {
  BrowserRouter,
  Navigate,
  Route,
  Routes
} from "react-router-dom";
import { PaymentResultPage } from "./pages/PaymentResultPage";
import { StorePage } from "./pages/StorePage";
import "./styles.css";

const root = document.getElementById("root");

if (!root) {
  throw new Error("React 루트 요소를 찾을 수 없습니다.");
}

createRoot(root).render(
  <BrowserRouter>
    <Routes>
      <Route path="/" element={<StorePage />} />
      <Route path="/payment/result" element={<PaymentResultPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  </BrowserRouter>
);
