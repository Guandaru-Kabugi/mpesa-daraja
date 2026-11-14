# Multi-stage build: stage 1 compiles the app with Maven and a full JDK,
# stage 2 copies only the finished .jar onto a small Java runtime image.
# The final image never contains Maven, the source code or the JDK.

# ---------- Stage 1: build ----------
FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml first and download dependencies in their own layer.
# Docker caches each step, so when only your code changes (not the pom),
# rebuilds skip the slow download.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src/ src/
# Tests need a database, which doesn't exist at build time, so skip them here
RUN mvn -B -q -DskipTests package

# ---------- Stage 2: run ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Don't run as root inside the container: if the app were ever compromised,
# the attacker gets an unprivileged user instead of root.
RUN useradd --system --uid 1001 --no-create-home spring

COPY --from=build /app/target/*.jar app.jar
USER spring

EXPOSE 8082
# MaxRAMPercentage lets Java size its memory from the container's limit
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
