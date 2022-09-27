cd "`dirname "$0"`"

./jre/zulu-8.jre/Contents/Home/bin/java "-Dsuwayomi.tachidesk.config.server.webUIInterface=electron" "-Dsuwayomi.tachidesk.config.server.electronPath=electron/Electron.app/Contents/MacOS/Electron" -jar Tachidesk-Server.jar