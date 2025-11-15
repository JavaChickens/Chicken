# Chicken Minecraft Server Hosting Platform - Docker Image
# Multi-stage build for optimized production deployment
# Copyright (c) 2024 Chicken Project - Licensed under Apache 2.0

# Build stage
FROM openjdk:17-jdk-slim AS builder

# Install build dependencies
RUN apt-get update && apt-get install -y \
    maven \
    git \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy Maven configuration files
COPY pom.xml .
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage
FROM openjdk:17-jre-slim

# Install runtime dependencies
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Create application user for security
RUN groupadd -r chicken && useradd -r -g chicken chicken

# Set working directory
WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /app/target/chicken-server-host-*.jar app.jar

# Create necessary directories
RUN mkdir -p /app/data/servers /app/data/plugins-cache /app/logs \
    && chown -R chicken:chicken /app

# Switch to non-root user
USER chicken

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Environment variables
ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-Xmx2g -Xms1g"

# Start the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# Labels for metadata
LABEL maintainer="piratebomber" \
      version="1.0.0" \
      description="Enterprise Minecraft Server Hosting Platform" \
      org.opencontainers.image.title="Chicken Server Host" \
      org.opencontainers.image.description="Production-ready Minecraft server hosting with web interface and CLI" \
      org.opencontainers.image.version="1.0.0" \
      org.opencontainers.image.vendor="piratebomber" \
      org.opencontainers.image.licenses="Apache-2.0"