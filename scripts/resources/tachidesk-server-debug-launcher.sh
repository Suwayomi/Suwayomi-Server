#!/bin/sh

exec ./jre/bin/java \
    -Dsuwayomi.tachidesk.config.server.debugLogsEnabled=true \
    -jar ./Tachidesk-Server.jar
