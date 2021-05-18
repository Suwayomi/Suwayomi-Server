# Building `Tachidesk Launcher.exe`
1. compile `Tachidesk Launcher.c` statically using GCC MinGW: `gcc -o "Tachidesk Launcher.exe" "Tachidesk Launcher.c"`
2. Add `server/src/main/resources/icon/faviconlogo.ico` into the exe with `rcedit` from the electron project: `rcedit "Tachidesk Launcher.exe" --set-icon faviconlogo.ico`
