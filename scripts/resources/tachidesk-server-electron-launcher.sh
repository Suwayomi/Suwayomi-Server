#!/bin/sh

exec /usr/bin/java -Dsuwayomi.tachidesk.config.server.webUIInterface=electron -Dsuwayomi.tachidesk.config.server.electronPath=/usr/bin/electron -jar /usr/share/java/tachidesk-server/tachidesk-server.jar
