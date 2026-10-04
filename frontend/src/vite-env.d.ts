/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_KEYCLOAK_URL?: string;
  readonly VITE_KEYCLOAK_REALM?: string;
  readonly VITE_KEYCLOAK_CLIENT_ID?: string;
  /** 운영 배포처럼 방문이 없으면 서버를 재우는 환경이면 "true". frontend/Dockerfile이 지정한다. */
  readonly VITE_SERVERS_SLEEP?: string;
}
