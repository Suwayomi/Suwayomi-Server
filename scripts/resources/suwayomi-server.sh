#!/bin/sh

cd "$(dirname "$0")"

if [ -f ./bin/catch_abort.so ]; then
  # portable realpath: catch_abort.so is a preload lib, not in PATH
  export LD_PRELOAD="$(cd ./bin && pwd)/catch_abort.so"
fi
# "$@" goes before -jar so args reach the JVM (e.g. -Dsuwayomi.tachidesk.config.*
# overrides); the server itself consumes no program arguments.
exec ./jre/bin/java "$@" -jar ./bin/Suwayomi-Server.jar
