import Keycloak from "keycloak-js";
import { SleepingServer } from "../lib/serverWakeup";

const config = {
  url: import.meta.env.VITE_KEYCLOAK_URL || "http://127.0.0.1:8081",
  realm: import.meta.env.VITE_KEYCLOAK_REALM || "modo-club",
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || "modo-club-web"
};

// 로그인·회원가입 화면은 Keycloak이 제공한다. 이 앱은 비밀번호를 받지 않고 access token만 메모리에 둔다.
const keycloak = new Keycloak(config);

// 운영 배포에서는 Keycloak도 방문이 없으면 잠든다. 어느 출처에서나 읽을 수 있는 realm 공개 정보로 깨어났는지 확인한다.
const authServer = new SleepingServer(`${config.url}/realms/${config.realm}`);

/** Keycloak 화면(계정 설정)으로 가는 링크의 onClick. Keycloak이 잠들었으면 깨운 뒤 이동한다. */
export const followAuthLink = authServer.followWhenAwake;

let initialized: Promise<boolean> | null = null;

export interface Customer {
  id: string;
  name: string;
  email: string;
  /** Keycloak realm 역할 shop-admin이 있는 쇼핑몰 관리자인지. 메뉴 표시에만 쓰고, 권한은 서버가 검사한다. */
  isShopAdmin: boolean;
}

// 새로고침이나 결제창 복귀로 페이지를 다시 열어도 Keycloak 세션이 있으면 화면 이동 없이 토큰을 다시 받는다.
export function initAuth(): Promise<boolean> {
  initialized ??= authServer.whenAwake().then(() => keycloak.init({
    onLoad: "check-sso",
    pkceMethod: "S256",
    silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
    checkLoginIframe: false,
    messageReceiveTimeout: 3000
  })).catch(() => false);
  return initialized;
}

/** 만료가 가까우면 먼저 갱신한다. 갱신에 실패하면 로그아웃 상태로 바꾸고 null을 돌려준다. */
export async function getAccessToken(): Promise<string | null> {
  if (!(await initAuth()) || !keycloak.authenticated) return null;
  try {
    await refreshToken();
    return keycloak.token ?? null;
  } catch {
    keycloak.clearToken();
    return null;
  }
}

// 오래 쉬는 동안 Keycloak이 잠들면 갱신 요청이 실패한다. 세션은 Keycloak DB에 남아 있으므로 깨운 뒤 한 번 더 시도하고,
// 그래도 실패하면 세션이 끝난 것으로 본다.
async function refreshToken(): Promise<void> {
  try {
    await keycloak.updateToken(30);
  } catch (error) {
    if (!authServer.sleeps) throw error;
    await authServer.whenAwake(true);
    await keycloak.updateToken(30);
  }
}

// 주문·결제·상담 API는 로그인한 회원의 access token을 붙인다. 로그인하지 않았으면 서버가 401로 거부한다.
export async function signedIn(options: RequestInit = {}): Promise<RequestInit> {
  const token = await getAccessToken();
  if (!token) return options;
  const headers = new Headers(options.headers);
  headers.set("Authorization", `Bearer ${token}`);
  return { ...options, headers };
}

export function currentCustomer(): Customer | null {
  const token = keycloak.tokenParsed;
  if (!keycloak.authenticated || typeof token?.sub !== "string" || !token.sub.trim()) return null;
  const email = typeof token.email === "string" ? token.email : "";
  const given = typeof token.given_name === "string" ? token.given_name : "";
  const family = typeof token.family_name === "string" ? token.family_name : "";
  // 한글 이름은 성과 이름을 붙여 쓴다.
  const name = /[가-힣]/.test(family + given) ? `${family}${given}` : `${given} ${family}`.trim();
  return { id: token.sub, name: name || email, email, isShopAdmin: keycloak.hasRealmRole("shop-admin") };
}

export function onSignedOut(listener: () => void): void {
  keycloak.onAuthLogout = listener;
}

function appUrl(path: string): string {
  return `${window.location.origin}${path}`;
}

// Keycloak 화면으로 이동하는 동작은 잠든 Keycloak이 오류 화면을 보여주지 않도록 깨운 뒤 이동한다.
export async function login(returnPath: string): Promise<void> {
  await authServer.whenAwake();
  return keycloak.login({ redirectUri: appUrl(returnPath), locale: "ko" });
}

export async function register(returnPath: string): Promise<void> {
  await authServer.whenAwake();
  return keycloak.register({ redirectUri: appUrl(returnPath), locale: "ko" });
}

/** Keycloak 계정 화면(이름·이메일·비밀번호·2단계 인증). 돌아오기 링크는 returnPath로 연결한다. */
export function accountUrl(returnPath: string): string {
  return keycloak.createAccountUrl({ redirectUri: appUrl(returnPath) });
}

export async function logout(): Promise<void> {
  await authServer.whenAwake();
  return keycloak.logout({ redirectUri: appUrl("/") });
}
