# ============================================
# Stage 1: Build (Gradle로 JAR 생성)
# ============================================
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Gradle 캐시 활용을 위해 의존성 파일 먼저 복사
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./

# Gradle wrapper 권한 설정 + 의존성 미리 다운로드
RUN chmod +x ./gradlew && \
    ./gradlew dependencies --no-daemon || true

# 소스 코드 복사
COPY src ./src

# JAR 빌드 (테스트는 CI에서 별도 실행하므로 스킵)
RUN ./gradlew bootJar --no-daemon -x test

# ============================================
# Stage 2: Runtime (실행 환경, 가벼움)
# ============================================
FROM eclipse-temurin:17-jre

WORKDIR /app

# 비루트 사용자 생성 (보안)
RUN groupadd -r spring && useradd -r -g spring spring

# 빌드된 JAR만 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 비루트 사용자로 전환
USER spring:spring

# 컨테이너 외부 노출 포트
EXPOSE 8080

# JVM 메모리 최적화 (t3.micro 1GB RAM 환경)
ENV JAVA_OPTS="-Xmx384m -Xms256m -XX:+UseSerialGC -XX:MaxRAMPercentage=75"

# 컨테이너 시작 명령
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]