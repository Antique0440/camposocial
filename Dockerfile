# ====================================================================
# Multi-stage Dockerfile for campoSocial
# Stage 1: Build the standalone Spring Boot JAR with Maven
# ====================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Cache dependency layer
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy sources and package executable jar
COPY src src
RUN ./mvnw clean package -DskipTests -B

# ====================================================================
# Stage 2: Lean, secure production runtime container
# ====================================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as an unprivileged service account for security
RUN addgroup -S camposocial && adduser -S camposocial -G camposocial
USER camposocial:camposocial

# Copy fat JAR from build stage
COPY --from=builder /workspace/target/campoSocial-0.0.1-SNAPSHOT.jar app.jar

# Configuration defaults
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

# Container-aware JVM memory tuning
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

