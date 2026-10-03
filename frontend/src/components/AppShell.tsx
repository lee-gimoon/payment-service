import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/auth";
import { useShop } from "../lib/shop";
interface AppShellProps {
  children: ReactNode;
  footerText: string;
  mainClassName?: string;
}
export function AppShell({ children, footerText, mainClassName }: AppShellProps) {
  const { cartCount } = useShop();
  const { status, customer, login, register, logout } = useAuth();
  return (
    <>
      <div className="announcement">시안용 스토어 · 테스트 결제는 실제 청구되지 않습니다</div>
      <header>
        <Link className="brand" to="/">
          MODO <span>CLUB</span>
        </Link>
        <nav aria-label="주요 메뉴">
          <Link to="/#products">전체 상품</Link>
          {/* 관리자는 직원 계정이라 주문 조회·마이페이지 대신 관리자 화면을 쓴다. */}
          {!(status === "signedIn" && customer?.isShopAdmin) && <Link to="/orders">주문 확인</Link>}
          <Link to="/cart">장바구니 <span>{cartCount}</span></Link>
          {status === "signedIn" && <>
            {customer?.isShopAdmin && <Link to="/admin/chat">상담 관리</Link>}
            {customer?.isShopAdmin
              ? <strong className="nav-customer">{customer.name}님</strong>
              : <Link className="nav-mypage" to="/mypage" aria-label={`${customer?.name}님 마이페이지`}>
                <span className="nav-customer">{customer?.name}님</span><span className="nav-mypage-short">마이페이지</span>
              </Link>}
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
