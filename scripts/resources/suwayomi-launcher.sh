#!/bin/sh

cd "$(dirname "$0")"
exec ./jre/bin/java --add-exports=java.desktop/sun.awt=ALL-UNNAMED -jar ./Suwayomi-Launcher.jar "$@"
