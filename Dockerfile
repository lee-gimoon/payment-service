# 백엔드(Spring Boot) 운영 이미지. Railway의 backend 서비스가 저장소 루트를 빌드 컨텍스트로 쓴다.
# 로컬 확인: docker build -t payment-backend .
# 배포 구성과 환경변수는 docs/deployment.md에 있다.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# 의존성을 먼저 받아 두면 소스만 바뀐 빌드는 이 단계를 캐시로 건너뛴다.
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
# 테스트는 Testcontainers(Docker)가 필요해 이미지 빌드에서는 돌리지 않는다. 푸시 전에 로컬에서 test를 실행한다.
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app

# 운영 기본값. Railway 변수로 같은 이름을 지정하면 덮어쓴다.
#  - PAYMENT_BIND_ADDRESS: 컨테이너 밖(Railway 내부망)에서 받도록 모든 주소에서 듣는다.
#  - PAYMENT_FORWARD_HEADERS_STRATEGY: 공개 주소 없이 frontend nginx 뒤에서만 받으므로 nginx가 넘긴 X-Forwarded-*를 믿는다.
#  - 나머지는 통신이 없을 때 잠들게 하는 설정이다. 주기 작업은 깨어 있는 동안만 돌고, 쉬는 DB 연결은 1분 뒤 닫는다.
#  - JAVA_OPTS: 메모리 사용량이 곧 요금이라 힙을 작게 잡고, 첫 접속 대기를 줄이려 JIT를 빠른 단계에서 멈춘다.
ENV PAYMENT_BIND_ADDRESS=:: \
    PAYMENT_FORWARD_HEADERS_STRATEGY=framework \
    PAYMENT_RECOVERY_INTERVAL=PT30M \
    ORDER_UNPAID_EXPIRY_INTERVAL=PT30M \
    PAYMENT_DB_POOL_MIN_IDLE=0 \
    PAYMENT_DB_POOL_IDLE_TIMEOUT_MS=60000 \
    JAVA_OPTS="-Xms64m -Xmx320m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
