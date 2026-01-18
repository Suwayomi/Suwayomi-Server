FROM eclipse-temurin:25.0.1_8-jdk-noble AS build

ARG TACHIDESK_ABORT_HANDLER_DOWNLOAD_URL

# build abort handler
RUN if [ -n "$TACHIDESK_ABORT_HANDLER_DOWNLOAD_URL" ]; then \
      apt-get update && \
      apt-get -y install -y curl gcc && \
      cd /tmp && \
      curl "$TACHIDESK_ABORT_HANDLER_DOWNLOAD_URL" -O && \
      gcc -fPIC -I$JAVA_HOME/include -I$JAVA_HOME/include/linux -shared catch_abort.c -lpthread -o /opt/catch_abort.so && \
      rm -f catch_abort.c && \
      apt-get -y purge gcc --auto-remove && \
      apt-get clean && \
      rm -rf /var/lib/apt/lists/* || exit 1; \
    fi

FROM eclipse-temurin:25.0.1_8-jre-noble

ARG TARGETPLATFORM
ARG TACHIDESK_KCEF=y # y or n, leave empty for auto-detection
ARG TACHIDESK_KCEF_RELEASE_URL

# Install envsubst from GNU's gettext project
# install unzip to unzip the server-reference.conf from the jar
# Install tini for a tiny init system (handles orphan processes for graceful restart)
RUN apt-get update && \
    apt-get -y install -y curl gettext-base unzip tini ca-certificates p11-kit && \
    /usr/bin/p11-kit extract --format=java-cacerts --filter=certificates --overwrite --purpose server-auth $JAVA_HOME/lib/security/cacerts && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

COPY scripts/kcef_download.sh /root/kcef_download.sh

# install CEF dependencies
RUN if [ "$TACHIDESK_KCEF" = "y" ] || ([ "$TACHIDESK_KCEF" = "" ] && ([ "$TARGETPLATFORM" = "linux/amd64" ] || [ "$TARGETPLATFORM" = "linux/arm64" ])); then \
      apt-get update && \
      apt-get -y install --no-install-recommends -y libxss1 libxext6 libxrender1 libxcomposite1 libxdamage1 libxkbcommon0 libxtst6 \
          libjogl2-jni libgluegen2-jni libglib2.0-0t64 libnss3 libdbus-1-3 libpango-1.0-0 libcairo2 libasound2t64 dbus \
          libatk-bridge2.0-0t64 libcups2t64 libdrm2 libgbm1 xvfb \
          curl jq gawk findutils && \
      /root/kcef_download.sh "$TACHIDESK_KCEF_RELEASE_URL" "$TARGETPLATFORM" && \
      apt-get clean && \
      rm -rf /var/lib/apt/lists/* || exit 1; \
    fi

COPY --from=build /opt/*.so /opt/

# Create a user to run as
# .X11-unix must be created by root
# Ubuntu exposes libgluegen_rt.so as libgluegen2_rt.so for some reason, so rename it
# JCEF (or Java?) also does not search /usr/lib/jni, so copy them over into one it will search
RUN userdel -r ubuntu && \
    groupadd --gid 1000 suwayomi && \
    useradd  --uid 1000 --gid suwayomi --no-log-init -G audio,video suwayomi && \
    mkdir -p /home/suwayomi/.local/share/Tachidesk && \
    if command -v Xvfb; then \
      mkdir /tmp/.X11-unix && chmod 1777 /tmp/.X11-unix && \
      cp /usr/lib/jni/libgluegen2_rt.so /home/suwayomi/libgluegen_rt.so && \
      cp /usr/lib/jni/*.so /home/suwayomi/; \
    fi

COPY scripts/create_server_conf.sh /home/suwayomi/create_server_conf.sh
COPY scripts/startup_script.sh /home/suwayomi/startup_script.sh

ARG TACHIDESK_RELEASE_JAR_LOCATION
# Copy the app into the container
# then update permissions of files.
# we grant o+rwx because we need to allow non default UIDs (eg via docker run ... --user)
# to write to the directory to generate the server.conf
RUN curl -s --create-dirs -L $TACHIDESK_RELEASE_JAR_LOCATION -o /home/suwayomi/startup/tachidesk_latest.jar && \
    chmod 777 -R /home/suwayomi && \
    chown -R suwayomi:suwayomi /home/suwayomi

LABEL maintainer="mpdr200011" \
      org.opencontainers.image.title="Suwayomi Docker" \
      org.opencontainers.image.authors="https://github.com/mpdr200011" \
      org.opencontainers.image.url="https://github.com/mpdr200011/docker-tachidesk/pkgs/container/tachidesk" \
      org.opencontainers.image.source="https://github.com/mpdr200011/docker-tachidesk" \
      org.opencontainers.image.description="This image is used to start suwayomi server in a container" \
      org.opencontainers.image.vendor="mpdr200011" \
      org.opencontainers.image.licenses="MPL-2.0"

ENV HOME=/home/suwayomi
WORKDIR /home/suwayomi
USER suwayomi
EXPOSE 4567

ENTRYPOINT ["tini", "--"]
CMD ["/home/suwayomi/startup_script.sh"]

# vim: set ft=dockerfile:
