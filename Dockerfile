# ============================================
# BUILDER STAGE
# ============================================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

ARG VERSION=unknown
ARG BUILD_TIMESTAMP=unknown

RUN echo "VERSION=${VERSION}" > /build/version.properties && \
    echo "BUILD_TIMESTAMP=${BUILD_TIMESTAMP}" >> /build/version.properties

COPY pom.xml /build/
RUN mvn dependency:go-offline -B

COPY src /build/src
RUN mvn package -DskipTests -B && \
    cp /build/target/classes/META-INF/app.properties /build/target/*.jar!/BOOT-INF/classes/META-INF/ 2>/dev/null || true

# ============================================
# RUNTIME STAGE (ARM64 & AMD64)
# ============================================
FROM eclipse-temurin:17-jre

WORKDIR /app

RUN apt-get update && apt-get install -y curl && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

RUN groupadd -r mtg && useradd -r -g mtg mtg

COPY --from=builder /build/target/*.jar app.jar
COPY --from=builder /build/version.properties /app/version.properties

RUN chown -R mtg:mtg /app

USER mtg

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8080/ || exit 1

ENTRYPOINT ["sh", "-c", "\
    VERSION=$(grep VERSION /app/version.properties | cut -d= -f2); \
    BUILD_TS=$(grep BUILD_TIMESTAMP /app/version.properties | cut -d= -f2); \
    java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 \
         -Dapp.version=$VERSION -Dapp.build.timestamp=$BUILD_TS \
         -jar /app/app.jar"]
