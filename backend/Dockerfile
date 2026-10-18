# Spring Boot 3.5 backend (default deployment).
# Base images are build arguments so they can be pinned by digest from .env
# (run scripts/pin-images.ps1 or scripts/pin-images.sh once to record tested digests).
ARG BUILD_IMAGE=maven:3.9.9-eclipse-temurin-17
ARG RUNTIME_IMAGE=gcr.io/distroless/java17-debian13:nonroot

FROM ${BUILD_IMAGE} AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM ${RUNTIME_IMAGE}
WORKDIR /app
COPY --from=build --chown=nonroot:nonroot /app/target/service-desk-1.0.0.jar /app/app.jar
USER nonroot
EXPOSE 8080
CMD ["/app/app.jar"]
