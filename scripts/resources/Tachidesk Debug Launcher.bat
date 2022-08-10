:: cleaner output
@echo off

jre\bin\java -Dsuwayomi.tachidesk.config.server.debugLogsEnabled=true -jar  Tachidesk-Server.jar

:: Prevent cmd from closing when Tachidesk crashes
pause