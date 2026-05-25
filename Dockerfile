# ============================================================================
# Multi-stage Dockerfile for Sri Karthikeya Caterers Backend
#
# Document Studio note: the runtime stage was switched from Alpine (musl)
# to Ubuntu jammy (glibc) so Playwright's bundled Chromium can run. The
# image grew from ~200MB to ~700MB as a result; trade-off accepted because
# the alternative (apk-installed chromium + skip-download workarounds) is
# more fragile across Playwright versions.
# ============================================================================

# ============================================================================
# Stage 1: Build Stage
# Uses Maven to compile and package the Spring Boot application.
# Kept on Alpine since the build doesn't need glibc.
# ============================================================================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and pom.xml first for layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN ./mvnw dependency:go-offline -B

COPY src/ ./src/

RUN ./mvnw clean package -DskipTests -B && \
    mkdir -p target/dependency && \
    cd target/dependency && \
    jar -xf ../*.jar

# ============================================================================
# Stage 2: Runtime Stage
# Ubuntu jammy (glibc) so Playwright's headless Chromium runs without
# wrestling musl. Native Chromium deps installed via apt below.
# ============================================================================
FROM eclipse-temurin:21-jre-jammy

LABEL maintainer="srinivas.yanamandra04@gmail.com"
LABEL description="Sri Karthikeya Caterers - Spring Boot Backend API"
LABEL version="1.1.0"

# Native dependencies Chromium needs at runtime. The full list comes from
# Playwright's own dependency declaration for Ubuntu jammy. Kept explicit
# (rather than apt-get -y install $(playwright cli deps)) so a network
# blip during build doesn't produce a half-installed image.
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl tzdata ca-certificates \
        libnss3 libnspr4 libdbus-1-3 \
        libatk1.0-0 libatk-bridge2.0-0 libatspi2.0-0 \
        libcups2 libdrm2 libxkbcommon0 libxcomposite1 \
        libxdamage1 libxfixes3 libxrandr2 libgbm1 \
        libpangocairo-1.0-0 libpango-1.0-0 libcairo2 \
        libasound2 fonts-liberation && \
    rm -rf /var/lib/apt/lists/*

# Timezone
ENV TZ=Asia/Kolkata
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# Non-root user
RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -m -s /bin/bash appuser

WORKDIR /app

# Copy the extracted JAR layers from builder stage
COPY --from=builder --chown=appuser:appgroup /build/target/dependency/BOOT-INF/lib /app/lib
COPY --from=builder --chown=appuser:appgroup /build/target/dependency/META-INF /app/META-INF
COPY --from=builder --chown=appuser:appgroup /build/target/dependency/BOOT-INF/classes /app

# Install Playwright's Chromium *into the image* so first-request render
# doesn't have to download 150MB. The CLI is bundled inside the Playwright
# Java JAR pulled in by Maven.
USER appuser
ENV PLAYWRIGHT_BROWSERS_PATH=/home/appuser/.cache/ms-playwright
RUN java -cp "/app:/app/lib/*" com.microsoft.playwright.CLI install chromium

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${PORT:-8080}/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -XX:+OptimizeStringConcat \
    -Djava.security.egd=file:/dev/./urandom \
    -Dfile.encoding=UTF-8"

ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -cp /app:/app/lib/* syncqubits.ai.skc.SkcApplication"]
