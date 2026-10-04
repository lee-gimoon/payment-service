# 스토어 화면 운영 이미지(React 빌드 + nginx). Railway의 frontend 서비스가 저장소 루트를 빌드 컨텍스트로 쓴다.
# 로컬 확인: docker build -f frontend/Dockerfile --build-arg VITE_KEYCLOAK_URL=http://127.0.0.1:8081 -t payment-frontend .
# 배포 구성과 환경변수는 docs/deployment.md에 있다.

# TypeScript 7의 tsc는 glibc용 실행 파일이라 alpine 대신 slim 이미지로 빌드한다.
FROM node:22-bookworm-slim AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# Vite는 VITE_ 변수를 빌드할 때 화면 코드에 넣는다. Keycloak 공개 주소는 Railway 변수에서 빌드 인자로 받는다.
ARG VITE_KEYCLOAK_URL
# 운영 배포는 방문이 없으면 서버를 재우므로, 화면이 잠든 서버를 깨운 뒤 요청하게 한다.
ENV VITE_SERVERS_SLEEP=true
RUN test -n "$VITE_KEYCLOAK_URL" || { echo "VITE_KEYCLOAK_URL(Keycloak 공개 주소)이 필요합니다." >&2; exit 1; }
RUN npm run build

FROM nginx:1.28-alpine
# NGINX_ENTRYPOINT_LOCAL_RESOLVERS: 시작할 때 /etc/resolv.conf의 DNS 서버를 NGINX_LOCAL_RESOLVERS로 꺼내 준다.
# PORT는 Railway가 주는 값으로 바뀐다. BACKEND_URL은 Railway 변수로 꼭 지정한다(예: http://backend.railway.internal:8080).
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1 \
    PORT=8080
COPY frontend/nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
