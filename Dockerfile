# Stage 1: Build the Tachidesk app
FROM eclipse-temurin:11-jdk-focal AS builder

WORKDIR /app

# Copy the project files to the container
COPY . .

# Build the project
RUN ./gradlew shadowJar

# Stage 2: Create the final image using eclipse-temurin JRE
FROM ghcr.io/linuxserver/baseimage-alpine:3.17

# Install dependecy
RUN apk add -U --upgrade --no-cache curl openjdk8-jre-base tzdata

# Create the /config directory
RUN  mkdir -p /app/tachidesk

WORKDIR /app/tachidesk

# Copy the built app from the builder stage and rename it
COPY --from=builder /app/server/build/Tachidesk-Server-*.jar Tachidesk-Server-Latest.jar



# Container Labels
LABEL maintainer="suwayomi" \
      org.opencontainers.image.title="Tachidesk Docker" \
      org.opencontainers.image.authors="https://github.com/suwayomi" \
      org.opencontainers.image.url="https://github.com/suwayomi/docker-tachidesk/pkgs/container/tachidesk" \
      org.opencontainers.image.source="https://github.com/suwayomi/docker-tachidesk" \
      org.opencontainers.image.description="This image is used to start tachidesk jar executable in a container" \
      org.opencontainers.image.vendor="suwayomi" \
      org.opencontainers.image.licenses="MPL-2.0"


# copy local files
COPY root/ /

# ports and volumes
EXPOSE 4567
VOLUME /config
