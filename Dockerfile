# syntax=docker/dockerfile:1.6

FROM eclipse-temurin:21-jdk AS builder
WORKDIR /src

COPY gradle ./gradle
COPY gradlew ./gradlew
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY buildSrc ./buildSrc
COPY AndroidCompat ./AndroidCompat
COPY server ./server
COPY scripts ./scripts

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :server:shadowJar

FROM eclipse-temurin:21-jre AS runtime

RUN apt-get update && \
    apt-get install -y --no-install-recommends tini ca-certificates && \
    rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 1000 suwayomi && \
    useradd --system --uid 1000 --gid suwayomi --home /data suwayomi && \
    mkdir -p /data && chown -R suwayomi:suwayomi /data

COPY --from=builder /src/server/build/Suwayomi-Server-*.jar /opt/suwayomi/server.jar

USER suwayomi
WORKDIR /data
VOLUME ["/data"]
EXPOSE 4567

ENV JAVA_OPTS="" \
    SUWAYOMI_DISABLE_KCEF=1

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["sh", "-c", "exec java $JAVA_OPTS -Dsuwayomi.tachidesk.config.server.rootDir=/data -jar /opt/suwayomi/server.jar"]
