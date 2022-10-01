FROM alpine:latest

RUN apk --update add curl openjdk8-jre-base tzdata

RUN addgroup -g 1000 -S suwayomi && adduser -u 1000 -S suwayomi -G suwayomi

RUN mkdir -p /home/suwayomi && chown -R suwayomi:suwayomi /home/suwayomi

USER suwayomi

WORKDIR /home/suwayomi

RUN curl -s --create-dirs -L https://raw.githubusercontent.com/suwayomi/docker-tachidesk/main/scripts/startup_script.sh -o /home/suwayomi/startup/startup_script.sh

COPY server/build/Tachidesk-Server*.jar /home/suwayomi/startup/tachidesk_latest.jar

EXPOSE 4567

CMD ["/bin/sh", "/home/suwayomi/startup/startup_script.sh"]