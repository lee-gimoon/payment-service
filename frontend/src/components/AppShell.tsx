import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { useShop } from "../lib/shop";
interface AppShellProps {
  children: ReactNode;
  footerText: string;
  mainClassName?: string;
}
export function AppShell({ children, footerText, mainClassName }: AppShellProps) {
  const { cartCount } = useShop();
  return (
    <>
      <div className="announcement">시안용 스토어 · 테스트 결제는 실제 청구되지 않습니다</div>
      <header>
        <Link className="brand" to="/">
          MODO <span>CLUB</span>
        </Link>
        <nav aria-label="주요 메뉴"><Link to="/#products">전체 상품</Link><Link to="/orders">주문 확인</Link><Link to="/cart">장바구니 <span>{cartCount}</span></Link></nav>
      </header>

      <main className={mainClassName}>{children}</main>

      <footer>
        MODO CLUB
        <span>{footerText}</span>
      </footer>
    </>
  );
}
