import type { ReactNode } from "react";
import { Link } from "react-router-dom";

interface AppShellProps {
  children: ReactNode;
  footerText: string;
  mainClassName?: string;
}

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
