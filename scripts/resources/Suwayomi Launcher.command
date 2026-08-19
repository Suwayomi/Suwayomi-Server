cd "`dirname "$0"`"

./jre/bin/java --add-exports=java.desktop/sun.awt=ALL-UNNAMED -jar Suwayomi-Launcher.jar "$@"
