# ============================================================================
# Multi-stage Dockerfile for Sri Karthikeya Caterers Backend
# Optimized for Render.com deployment with security and performance best practices
# ============================================================================

# ============================================================================
# Stage 1: Build Stage
# Uses Maven to compile and package the Spring Boot application
# ============================================================================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

# Set working directory
WORKDIR /build

# Copy Maven wrapper and pom.xml first (for better layer caching)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies (cached layer if pom.xml doesn't change)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src/ ./src/

# Build the application (skip tests for faster builds, run tests in CI/CD)
RUN ./mvnw clean package -DskipTests -B && \
    # Extract the built JAR name for easier reference
    mkdir -p target/dependency && \
    cd target/dependency && \
    jar -xf ../*.jar

# ============================================================================
# Stage 2: Runtime Stage
# Minimal JRE image for running the application
# ============================================================================
FROM eclipse-temurin:21-jre-alpine

# Metadata
LABEL maintainer="srinivas.yanamandra04@gmail.com"
LABEL description="Sri Karthikeya Caterers - Spring Boot Backend API"
LABEL version="1.0.0"

# Install required packages and security updates
RUN apk update && \
    apk upgrade && \
    apk add --no-cache \
    curl \
    tzdata \
    && rm -rf /var/cache/apk/*

# Set timezone to IST (Indian Standard Time)
ENV TZ=Asia/Kolkata
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# Create non-root user for security
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Set working directory
WORKDIR /app

# Copy the extracted JAR layers from builder stage
COPY --from=builder --chown=appuser:appgroup /build/target/dependency/BOOT-INF/lib /app/lib
COPY --from=builder --chown=appuser:appgroup /build/target/dependency/META-INF /app/META-INF
COPY --from=builder --chown=appuser:appgroup /build/target/dependency/BOOT-INF/classes /app

# Switch to non-root user
USER appuser

# Expose port (Render will override this with PORT env variable)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${PORT:-8080}/actuator/health || exit 1

# JVM optimization flags for containerized environments
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -XX:+OptimizeStringConcat \
    -Djava.security.egd=file:/dev/./urandom \
    -Dfile.encoding=UTF-8"

# Spring Boot production profile
ENV SPRING_PROFILES_ACTIVE=prod

# Run the application using the exploded JAR for faster startup
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -cp /app:/app/lib/* syncqubits.ai.skc.SkcApplication"]
