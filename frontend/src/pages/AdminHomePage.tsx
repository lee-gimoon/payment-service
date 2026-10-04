import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getChatRooms } from "../api/chatApi";
import { getAdminOrderCounts } from "../api/adminOrderApi";
import { useAuth } from "../auth/auth";
import { accountUrl, followAuthLink } from "../auth/keycloak";
import { AdminGate } from "../components/AdminGate";
import { AppShell } from "../components/AppShell";
import type { AdminOrderCounts } from "../types/admin";

// 관리자 계정의 첫 화면. 일반 회원의 마이페이지처럼 헤더의 `관리자 홈` 메뉴로 들어온다.
export function AdminHomePage() {
  const { status, customer } = useAuth();

  useEffect(() => {
    document.title = "관리자 홈 · MODO CLUB";
    window.scrollTo(0, 0);
  }, []);

  return <AppShell footerText="쇼핑몰 관리" mainClassName="admin-page">
    <nav className="breadcrumb" aria-label="현재 위치"><Link to="/">스토어</Link><span aria-hidden="true">/</span><span>관리자 홈</span></nav>
    <div className="page-intro">
      <p className="eyebrow">ADMIN</p>
      <h1>관리자 홈</h1>
      <p>{status === "signedIn" && customer?.isShopAdmin ? `${customer.name}님, 처리할 주문과 상담을 확인하세요.` : "처리할 주문과 상담을 확인하세요."}</p>
    </div>
    <AdminGate><AdminHomeCards /></AdminGate>
  </AppShell>;
}

function AdminHomeCards() {
  const [counts, setCounts] = useState<AdminOrderCounts | null>(null);
  const [waiting, setWaiting] = useState<number | null>(null);

  useEffect(() => {
    let active = true;
    // 수를 못 불러와도 각 화면으로는 이동할 수 있다.
    getAdminOrderCounts().then(result => { if (active) setCounts(result); }).catch(() => {});
    getChatRooms(true).then(rooms => { if (active) setWaiting(rooms.length); }).catch(() => {});
    return () => { active = false; };
  }, []);

  return <div className="admin-home-grid">
    <Link className="admin-home-card" to="/admin/orders?delivery=PREPARING">
      <span className="eyebrow">ORDERS</span>
      <h2>주문 관리</h2>
      <p>{counts ? <>상품 준비 중 <strong>{counts.preparing}</strong>건 · 배송 중 {counts.shipping}건</> : "결제 완료 주문의 송장을 등록합니다."}</p>
      <span className="card-link">주문 관리로 이동 →</span>
    </Link>
    <Link className="admin-home-card" to="/admin/chat">
      <span className="eyebrow">SUPPORT</span>
      <h2>상담 관리</h2>
      <p>{waiting === null ? "고객 문의에 답합니다." : <>답변 대기 <strong>{waiting}</strong>건</>}</p>
      <span className="card-link">상담 관리로 이동 →</span>
    </Link>
    <a className="admin-home-card" href={accountUrl("/admin")} onClick={followAuthLink}>
      <span className="eyebrow">ACCOUNT</span>
      <h2>계정 설정</h2>
      <p>이름, 비밀번호, 2단계 인증은 로그인 서비스(Keycloak) 계정 화면에서 바꿉니다.</p>
      <span className="card-link">계정 설정 열기 ↗</span>
    </a>
  </div>;
}
