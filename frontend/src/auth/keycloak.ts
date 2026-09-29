import Keycloak from "keycloak-js";

// 로그인·회원가입 화면은 Keycloak이 제공한다. 이 앱은 비밀번호를 받지 않고 access token만 메모리에 둔다.
const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL || "http://127.0.0.1:8081",
  realm: import.meta.env.VITE_KEYCLOAK_REALM || "modo-club",
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || "modo-club-web"
});

let initialized: Promise<boolean> | null = null;

export interface Customer {
  id: string;
  name: string;
  email: string;
}

// 새로고침이나 결제창 복귀로 페이지를 다시 열어도 Keycloak 세션이 있으면 화면 이동 없이 토큰을 다시 받는다.
export function initAuth(): Promise<boolean> {
  initialized ??= keycloak.init({
    onLoad: "check-sso",
    pkceMethod: "S256",
    silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
    checkLoginIframe: false,
    messageReceiveTimeout: 3000
  }).catch(() => false);
  return initialized;
}

/** 만료가 가까우면 먼저 갱신한다. 갱신에 실패하면 로그아웃 상태로 바꾸고 null을 돌려준다. */
export async function getAccessToken(): Promise<string | null> {
  if (!(await initAuth()) || !keycloak.authenticated) return null;
  try {
    await keycloak.updateToken(30);
    return keycloak.token ?? null;
  } catch {
    keycloak.clearToken();
    return null;
  }
}

export function currentCustomer(): Customer | null {
  const token = keycloak.tokenParsed;
  if (!keycloak.authenticated || typeof token?.sub !== "string" || !token.sub.trim()) return null;
  const email = typeof token.email === "string" ? token.email : "";
  const given = typeof token.given_name === "string" ? token.given_name : "";
  const family = typeof token.family_name === "string" ? token.family_name : "";
  // 한글 이름은 성과 이름을 붙여 쓴다.
  const name = /[가-힣]/.test(family + given) ? `${family}${given}` : `${given} ${family}`.trim();
  return { id: token.sub, name: name || email, email };
}

export function onSignedOut(listener: () => void): void {
  keycloak.onAuthLogout = listener;
}

function appUrl(path: string): string {
  return `${window.location.origin}${path}`;
}

export function login(returnPath: string): Promise<void> {
  return keycloak.login({ redirectUri: appUrl(returnPath), locale: "ko" });
}

export function register(returnPath: string): Promise<void> {
  return keycloak.register({ redirectUri: appUrl(returnPath), locale: "ko" });
}

export function logout(): Promise<void> {
  return keycloak.logout({ redirectUri: appUrl("/") });
}
