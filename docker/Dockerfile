# =============================================================
# AML System - Multi-stage Dockerfile
# =============================================================
# Prerequisites: Add spring-boot-starter-actuator to pom.xml
# for the health check endpoint to work:
#
#   <dependency>
#       <groupId>org.springframework.boot</groupId>
#       <artifactId>spring-boot-starter-actuator</artifactId>
#   </dependency>
#
# Build: docker build -t aml-system:latest -f Dockerfile ..
# Run:   docker run -p 8080:8080 aml-system:latest
# =============================================================

# ---- Stage 1: Build ----
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy pom.xml first for dependency caching
COPY pom.xml .
COPY scripts/ci-maven.sh ./scripts/ci-maven.sh
RUN bash scripts/ci-maven.sh dependency:go-offline

# Copy source code and build
COPY src ./src
RUN bash scripts/ci-maven.sh clean package -DskipTests

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="AML Team"
LABEL description="Anti-Money Laundering Detection System"
LABEL version="1.0.0"

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -S aml && adduser -S aml -G aml

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /build/target/*.jar app.jar

# Create logs directory
RUN mkdir -p /app/logs && chown -R aml:aml /app

USER aml

# Expose application port
EXPOSE 8080

# JVM configuration with security and performance tuning
ENV JAVA_OPTS="-XX:+UseG1GC \
    -XX:MaxRAMPercentage=75.0 \
    -Djava.security.egd=file:/dev/./urandom"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/api/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
