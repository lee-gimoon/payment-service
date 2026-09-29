import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { currentCustomer, initAuth, login, logout, onSignedOut, register, type Customer } from "./keycloak";

export type AuthStatus = "checking" | "signedOut" | "signedIn";

interface AuthState {
  status: AuthStatus;
  customer: Customer | null;
  /** 로그인 후 돌아올 경로. 생략하면 지금 보고 있는 화면으로 돌아온다. */
  login: (returnPath?: string) => void;
  register: (returnPath?: string) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

function currentPath(): string {
  return `${window.location.pathname}${window.location.search}`;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("checking");
  const [customer, setCustomer] = useState<Customer | null>(null);

  useEffect(() => {
    let active = true;
    onSignedOut(() => {
      if (!active) return;
      setStatus("signedOut");
      setCustomer(null);
    });
    initAuth().then(signedIn => {
      if (!active) return;
      setStatus(signedIn ? "signedIn" : "signedOut");
      setCustomer(currentCustomer());
    });
    return () => { active = false; };
  }, []);

  return <AuthContext.Provider value={{
    status,
    customer,
    login: returnPath => { void login(returnPath ?? currentPath()); },
    register: returnPath => { void register(returnPath ?? currentPath()); },
    logout: () => { void logout(); }
  }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const state = useContext(AuthContext);
  if (!state) throw new Error("AuthProvider가 필요합니다.");
  return state;
}
