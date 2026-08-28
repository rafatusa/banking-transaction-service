# syntax=docker/dockerfile:1

# ---- Build stage -------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Dependency layer first: it only changes when the POM changes.
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

# Analysis configs are referenced by the POM and must exist for the build to resolve.
COPY config ./config
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# ---- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy

# Run as an unprivileged user; never as root.
RUN groupadd --system --gid 1001 banking \
    && useradd --system --uid 1001 --gid banking --home /app --shell /usr/sbin/nologin banking \
    && mkdir -p /app \
    && chown -R banking:banking /app

WORKDIR /app

COPY --from=builder --chown=banking:banking /build/target/*.jar /app/app.jar

USER banking

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport" \
    SERVER_PORT=8080

# Probes the app's own health endpoint rather than merely proving the JVM exists.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD ["/bin/sh", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]

# Shell form is required so $JAVA_OPTS expands; exec ensures the JVM becomes PID 1
# and receives SIGTERM directly for graceful shutdown.
ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
