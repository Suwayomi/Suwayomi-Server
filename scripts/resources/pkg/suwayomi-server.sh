#!/bin/sh

# catch_abort.so is only shipped in the bundle tarballs, not the distro
# packages; only preload it when present.
if [ -f /usr/share/java/suwayomi-server/bin/catch_abort.so ]; then
  export LD_PRELOAD="/usr/share/java/suwayomi-server/bin/catch_abort.so"
fi
export DBUS_SESSION_BUS_ADDRESS="disabled:"
cd /usr/share/java/suwayomi-server/

# "$@" goes before -jar so args reach the JVM (e.g. -Dsuwayomi.tachidesk.config.*
# overrides); the server itself consumes no program arguments.
if [ -z "$DISPLAY" ] && command -v Xvfb >/dev/null; then
  echo "-- START: Spawning X server using xvfb-run --"
  exec xvfb-run /usr/bin/java "$@" -jar /usr/share/java/suwayomi-server/bin/Suwayomi-Server.jar
else
  exec /usr/bin/java "$@" -jar /usr/share/java/suwayomi-server/bin/Suwayomi-Server.jar
fi
