#!/bin/sh

exec ./jre/bin/java \
    -Dsuwayomi.tachidesk.config.server.webUIInterface=electron \
    -Dsuwayomi.tachidesk.config.server.electronPath=./electron/electron \
    -jar ./tachidesk-server.jar
