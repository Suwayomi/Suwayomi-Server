#!/bin/sh

export LD_PRELOAD="/usr/share/java/suwayomi-server/bin/catch_abort.so"
exec /usr/bin/java "$@" -jar /usr/share/java/suwayomi-server/bin/Suwayomi-Server.jar
