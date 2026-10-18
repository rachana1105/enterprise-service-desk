# Spring Boot 4.1 hardened backend (optional, selected with compose.secure.yaml).
# Base images are build arguments so they can be pinned by digest from .env
# (run scripts/pin-images.ps1 or scripts/pin-images.sh once to record tested digests).
ARG BUILD_IMAGE=maven:3.9.9-eclipse-temurin-17
ARG RUNTIME_IMAGE=cgr.dev/chainguard/jre:latest

FROM ${BUILD_IMAGE} AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM ${RUNTIME_IMAGE}
WORKDIR /app
COPY --from=build --chown=65532:65532 /app/target/service-desk-1.0.0.jar /app/app.jar
USER 65532
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
