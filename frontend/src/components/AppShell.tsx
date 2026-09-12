/** 파일 역할: 스토어와 결제 결과 화면이 함께 사용하는 머리글·본문·바닥글 구조를 제공한다. */
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

/** 공통 레이아웃에 넣을 본문, 바닥글 안내, 선택적인 본문 스타일 클래스다. */
interface AppShellProps {
  children: ReactNode;
  footerText: string;
  mainClassName?: string;
}

/** 공통 브랜드와 바닥글 사이에 전달받은 페이지 본문을 배치하는 레이아웃 컴포넌트다. */
export function AppShell({ children, footerText, mainClassName }: AppShellProps) {
  return (
    <>
      <header>
        <Link className="brand" to="/">
          한 장의 티셔츠
          <span>PAYMENT LAB</span>
        </Link>
        <span className="test-badge">테스트 스토어</span>
      </header>

      <main className={mainClassName}>{children}</main>

      <footer>
        PAYMENT LAB
        <span>{footerText}</span>
      </footer>
    </>
  );
}
