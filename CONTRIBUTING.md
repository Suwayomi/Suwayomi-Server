# Contributing
## Where should I start?
Checkout [This Kanban Board](https://github.com/Suwayomi/Tachidesk/projects/1) to see the rough development roadmap.

**Note to potential contributors:** Notify the developers on [Suwayomi discord](https://discord.gg/DDZdqZWaHA) (#programming channel) or open a WIP pull request before starting if you decide to take on working on anything from/not from the roadmap in order to avoid parallel efforts on the same issue/feature.

## How does Tachidesk-Server work?
This project has two components: 
1. **Server:** contains the implementation of [tachiyomi's extensions library](https://github.com/tachiyomiorg/extensions-lib) and uses an Android compatibility library to run apk extensions. All this concludes to serving a REST API.
2. **WebUI:** A react SPA(`create-react-app`) project that works with the server to do the presentation located at https://github.com/Suwayomi/Tachidesk-WebUI

## Why a web server app?
This structure is chosen to
- Achieve the maximum multi-platform-ness
- Gives the ability to acces Tachidesk-Server from a remote client e.g. your phone, tablet or smart TV
- Eaise development of user intefaces for Tachidesk

## Building from source
### Prerequisites
You need these software packages installed in order to build the project

- Java Development Kit and Java Runtime Environment version 8 or newer(both Oracle JDK and OpenJDK works)
- Android stubs jar
    - Manual download: Download [android.jar](https://raw.githubusercontent.com/Suwayomi/Tachidesk/android-jar/android.jar) and put it under `AndroidCompat/lib`.
    - Automated download: Run `AndroidCompat/getAndroid.sh`(MacOS/Linux) or `AndroidCompat/getAndroid.ps1`(Windows) from project's root directory to download and rebuild the jar file from Google's repository.

### building the full-blown jar (Tachidesk-Server + Tachidesk-WebUI bundle)
Run `./gradlew server:downloadWebUI server:shadowJar`, the resulting built jar file will be `server/build/Tachidesk-Server-vX.Y.Z-rxxx.jar`.

### building without `webUI` bundled(server only)
Delete `server/src/main/resources/WebUI.zip` if exists from previous runs, then run `./gradlew server:shadowJar`, the resulting built jar file will be `server/build/Tachidesk-Server-vX.Y.Z-rxxx.jar`.

### building the Windows package
First Build the jar, then cd into the `scripts` directory and run `./windows-bundler.sh win32` or `./windows-bundler.sh win64` depending on the target architecture, the resulting built zip package file will be `server/build/Tachidesk-Server-vX.Y.Z-rxxx-winXX.zip`.

## Running in development mode
run `./gradlew :server:run --stacktrace` to run the server
