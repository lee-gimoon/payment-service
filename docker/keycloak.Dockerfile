# Keycloak 운영 이미지. Railway의 keycloak 서비스가 저장소 루트를 빌드 컨텍스트로 쓴다.
# 로컬 개발은 이 이미지 대신 compose.yaml의 start-dev를 그대로 쓴다.
# 로컬 확인: docker build -f docker/keycloak.Dockerfile --build-arg STORE_URL=http://127.0.0.1:8080 -t payment-keycloak .
# 배포 구성과 환경변수는 docs/deployment.md에 있다.

# 로컬 compose.yaml의 Keycloak과 같은 버전으로 맞춘다.
ARG KEYCLOAK_VERSION=26.7.4

# 1) 운영용 realm과 테마. 로컬 테스트 계정(비밀번호가 저장소에 공개돼 있다)은 빼고,
#    로컬 스토어 주소(http://127.0.0.1:5173)를 Railway 스토어 주소로 바꾼다.
FROM alpine:3.22 AS realm
RUN apk add --no-cache jq
COPY docker/keycloak/modo-club-realm.json /work/modo-club-realm.json
COPY docker/keycloak-themes/modo-club /work/themes/modo-club
ARG STORE_URL
RUN test -n "$STORE_URL" || { echo "STORE_URL(스토어 공개 주소)이 필요합니다." >&2; exit 1; }; \
    store="${STORE_URL%/}"; \
    jq --arg store "$store" \
      'del(.users) | walk(if type == "string" then gsub("http://127\\.0\\.0\\.1:5173"; $store) else . end)' \
      /work/modo-club-realm.json > /work/realm.json \
    && sed -i "s#http://127.0.0.1:5173#$store#g" /work/themes/modo-club/account/theme.properties \
    && ! grep -rq "127.0.0.1:5173" /work/realm.json /work/themes
# Keycloak 옵션에 없는 DB 커넥션 풀(Agroal) 설정. 쉬는 연결을 2분마다 검사하는 기본 동작은 그 검사 쿼리가 서버를 깨워 두므로 끄고,
# 쉬는 연결은 기본 5분 대신 2분마다 정리해 통신이 빨리 멈추게 한다.
RUN printf '%s\n' \
      'quarkus.datasource.jdbc.background-validation-interval=0' \
      'quarkus.datasource.jdbc.idle-removal-interval=2M' > /work/quarkus.properties

# 2) 운영 모드 빌드. DB 종류와 테마를 미리 넣어 두면 시작할 때 다시 빌드하지 않아 첫 접속이 빨라진다.
FROM quay.io/keycloak/keycloak:${KEYCLOAK_VERSION} AS builder
ENV KC_DB=postgres
COPY --from=realm /work/themes/modo-club /opt/keycloak/themes/modo-club
COPY --from=realm /work/quarkus.properties /opt/keycloak/conf/quarkus.properties
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:${KEYCLOAK_VERSION}
COPY --from=builder /opt/keycloak/ /opt/keycloak/
COPY --from=realm /work/realm.json /opt/keycloak/data/import/modo-club-realm.json

# 운영 기본값. Railway 변수로 같은 이름을 지정하면 덮어쓴다.
#  - KC_HTTP_ENABLED·KC_PROXY_HEADERS: HTTPS는 Railway 앞단이 처리하고, 원래 주소는 X-Forwarded-* 헤더로 받는다.
#  - KC_CACHE=local: 서버 1대라 클러스터를 쓰지 않는다. 클러스터 기능(JDBC_PING)은 DB를 주기적으로 갱신해 서버가 잠들지 못하게 한다.
#    실행 옵션이라 빌드 단계가 아니라 여기서 정한다.
#  - KC_DB_POOL_MIN_SIZE: 쉬는 DB 연결을 남기지 않아 통신이 없으면 잠들 수 있게 한다.
#  - KC_SPI_SCHEDULED__INTERVAL: 만료된 이벤트·토큰 등을 지우는 작업 주기(초, 기본 15분)
#  - KC_SPI_USER_SESSIONS__INFINISPAN__SESSION_EXPIRATION_PERIOD: DB에 저장한 로그인 세션 중 만료된 것을 지우는 주기(초, 기본 3분)
#    둘 다 DB를 조회하므로 잠드는 기준(5~10분)보다 길게 30분으로 둔다. 만료된 세션은 이 작업과 상관없이 쓸 수 없다.
#  - JAVA_OPTS_KC_HEAP: 기본값은 컨테이너 메모리의 비율이라 Railway에서는 지나치게 커진다. 메모리 사용량이 곧 요금이다.
ENV KC_HTTP_ENABLED=true \
    KC_PROXY_HEADERS=xforwarded \
    KC_CACHE=local \
    KC_DB_POOL_MIN_SIZE=0 \
    KC_SPI_SCHEDULED__INTERVAL=1800 \
    KC_SPI_USER_SESSIONS__INFINISPAN__SESSION_EXPIRATION_PERIOD=1800 \
    JAVA_OPTS_KC_HEAP="-Xms64m -Xmx512m"
EXPOSE 8080
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
# realm은 Keycloak DB에 없을 때만 가져온다. 이미 있으면 파일을 읽지 않는다.
CMD ["start", "--optimized", "--import-realm"]
