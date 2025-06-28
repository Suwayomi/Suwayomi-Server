#!/bin/sh

export LD_PRELOAD="/usr/share/java/suwayomi-server/bin/catch_abort.so"

if [ -z "$DISPLAY" ] && command -v Xvfb >/dev/null; then
  echo "-- START: Spawning X server using xvfb-run --"
  exec xvfb-run /usr/bin/java "$@" -jar /usr/share/java/suwayomi-server/bin/Suwayomi-Server.jar
else
  exec /usr/bin/java "$@" -jar /usr/share/java/suwayomi-server/bin/Suwayomi-Server.jar
fi
