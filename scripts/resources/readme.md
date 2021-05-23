# Building `Tachidesk Launcher.exe`
1. compile `Tachidesk Launcher.c` statically with MSVC compiler.
2. Add `server/src/main/resources/icon/faviconlogo.ico` into the exe with `rcedit` from the electron project: `rcedit "Tachidesk Launcher.exe" --set-icon faviconlogo.ico`
