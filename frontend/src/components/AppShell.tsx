import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/auth";
import { useShop } from "../lib/shop";
import { ServerWakeupNotice } from "./ServerWakeupNotice";
interface AppShellProps {
  children: ReactNode;
  footerText: string;
  mainClassName?: string;
}
export function AppShell({ children, footerText, mainClassName }: AppShellProps) {
  const { cartCount } = useShop();
  const { status, customer, login, register, logout } = useAuth();
  const isShopAdmin = status === "signedIn" && Boolean(customer?.isShopAdmin);
  return (
    <>
      <div className="announcement">시안용 스토어 · 테스트 결제는 실제 청구되지 않습니다</div>
      <ServerWakeupNotice />
      <header>
        <Link className="brand" to="/">
          MODO <span>CLUB</span>
        </Link>
        <nav aria-label="주요 메뉴">
          <Link to="/#products">전체 상품</Link>
          {isShopAdmin ? <>
            {/* 관리자는 직원 계정이라 장바구니·주문 확인·마이페이지 대신 관리자 화면을 쓴다. */}
            <Link to="/admin">관리자 홈</Link>
            <Link to="/admin/orders">주문 관리</Link>
            <Link to="/admin/chat">상담 관리</Link>
          </> : <>
            <Link to="/orders">주문 확인</Link>
            <Link to="/cart">장바구니 <span>{cartCount}</span></Link>
            {/* 로그아웃 상태에서 누르면 마이페이지가 로그인을 안내한다. */}
            <Link to="/mypage">마이페이지</Link>
          </>}
          {status === "signedIn" && <>
            {/* 이름은 누구로 로그인했는지 알려 주는 인사말이다. 화면 이동은 글자 메뉴가 맡는다. */}
            <strong className="nav-customer">{customer?.name}님</strong>
            <button className="nav-button" type="button" onClick={logout}>로그아웃</button>
          </>}
          {status === "signedOut" && <>
            <button className="nav-button" type="button" onClick={() => login()}>로그인</button>
            <button className="nav-button nav-register" type="button" onClick={() => register()}>회원가입</button>
          </>}
        </nav>
      </header>

      <main className={mainClassName}>{children}</main>

      <footer>
        MODO CLUB
        <span>{footerText}</span>
      </footer>
    </>
  );
}
