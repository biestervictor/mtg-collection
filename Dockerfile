# ============================================
# BUILDER STAGE
# ============================================
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /build

COPY pom.xml /build/
RUN mvn dependency:go-offline -B

COPY src /build/src
RUN mvn package -DskipTests -B

# ============================================
# RUNTIME STAGE (ARM64 & AMD64)
# ============================================
FROM eclipse-temurin:17-jre

WORKDIR /app

RUN apt-get update && apt-get install -y curl && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

RUN groupadd -r mtg && useradd -r -g mtg mtg

COPY --from=builder /build/target/*.jar app.jar

RUN chown -R mtg:mtg /app

USER mtg

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8080/ || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
