# Multi-stage Dockerfile for HookRelay Backend (API & Background Worker)
# Build Stage
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY pom.xml pom.xml
COPY core core
COPY worker worker
COPY api api

RUN mvn clean package -DskipTests -B

# Runtime Stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install curl for container health check
RUN apk add --no-cache curl tzdata && \
    addgroup -S hookrelay && adduser -S hookrelay -G hookrelay

COPY --from=builder /build/api/target/hookrelay-api-*.jar /app/app.jar

USER hookrelay

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || curl -f http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
CMD ["--spring.profiles.active=api"]

