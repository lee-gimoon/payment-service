import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/auth";

/** 쇼핑몰 관리자에게만 children을 보여준다. 권한은 서버가 다시 검사하므로 화면 표시만 나눈다. */
export function AdminGate({ children }: { children: ReactNode }) {
  const { status, customer, login } = useAuth();
  if (status === "checking") return <p role="status">로그인 상태를 확인하고 있습니다.</p>;
  if (status === "signedOut") {
    return <section className="lookup-card" aria-labelledby="admin-gate-title">
      <div>
        <p className="eyebrow">SIGN IN</p>
        <h2 id="admin-gate-title">로그인이 필요합니다</h2>
        <p className="subtle">관리자 화면은 쇼핑몰 관리자 계정으로 로그인해야 사용할 수 있습니다.</p>
      </div>
      <button className="primary-button" type="button" onClick={() => login()}>관리자 계정으로 로그인</button>
    </section>;
  }
  if (!customer?.isShopAdmin) {
    return <section className="empty-state">
      <h2>쇼핑몰 관리자만 볼 수 있습니다</h2>
      <p>이 화면은 쇼핑몰 관리자 역할(shop-admin)이 있는 계정만 사용할 수 있습니다.</p>
      <Link className="primary-button" to="/">스토어로 돌아가기</Link>
    </section>;
  }
  return <>{children}</>;
}
