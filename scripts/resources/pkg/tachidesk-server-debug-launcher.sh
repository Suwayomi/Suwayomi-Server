#!/bin/sh

exec /usr/bin/java \
    -Dsuwayomi.tachidesk.config.server.debugLogsEnabled=true \
    -jar /usr/share/java/tachidesk-server/tachidesk-server.jar
