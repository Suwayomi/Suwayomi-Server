cd "`dirname "$0"`"

./jre/bin/java --add-exports=java.desktop/sun.awt=ALL-UNNAMED -Dsun.java2d.opengl=true -jar Suwayomi-Launcher.jar "$@"
