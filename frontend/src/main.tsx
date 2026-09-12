/** 파일 역할: index.html의 root 요소에 React를 시작하고 URL별 페이지를 연결하는 프런트엔드 진입점이다. */
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

// /는 스토어, /payment/result는 결제창 복귀 화면으로 연결하고 그 외 경로는 스토어로 보낸다.
createRoot(root).render(
  <BrowserRouter>
    <Routes>
      <Route path="/" element={<StorePage />} />
      <Route path="/payment/result" element={<PaymentResultPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  </BrowserRouter>
);
