:: cleaner output
@echo off

jre\bin\java -Dsuwayomi.tachidesk.config.server.debugLogsEnabled=true -jar  Tachidesk-Server.jasr

:: Prevent cmd from closing when Tachidesk crashes
pause