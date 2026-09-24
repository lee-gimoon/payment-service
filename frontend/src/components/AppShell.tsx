/** 파일 역할: 스토어와 결제 결과 화면이 함께 사용하는 머리글·본문·바닥글 구조를 제공한다. */
import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { useShop } from "../lib/shop";

/** 공통 레이아웃에 넣을 본문, 바닥글 안내, 선택적인 본문 스타일 클래스다. */
interface AppShellProps {
  children: ReactNode;
  footerText: string;
  mainClassName?: string;
}

/** 공통 브랜드와 바닥글 사이에 전달받은 페이지 본문을 배치하는 레이아웃 컴포넌트다. */
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
