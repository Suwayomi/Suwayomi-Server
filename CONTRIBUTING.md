# Contributing
## Where should I start?
Checkout [This Kanban Board](https://github.com/Suwayomi/Tachidesk/projects/1) to see the rough development roadmap.

**Note to potential contributors:** Notify the developers on Suwayomi discord (#programming channel) or open a WIP pull request before starting if you decide to take on working on anything from/not from the roadmap in order to avoid parallel efforts on the same issue/feature.

## How does Tachidesk work?
This project has two components: 
1. **server:** contains the implementation of [tachiyomi's extensions library](https://github.com/tachiyomiorg/extensions-lib) and uses an Android compatibility library to run apk extensions. All this concludes to serving a REST API to `webUI`.
2. **webUI:** A react SPA(`create-react-app`) project that works with the server to do the presentation.

## Why a web app?
This structure is chosen to
- Achieve the maximum multi-platform-ness
- Gives the ability to acces Tachidesk from a remote web browser e.g. your phone, tablet or smart TV
- Eaise development of alternative user intefaces for Tachidesk

## User Interfaces for Tachidesk server
Currently there are three known interfaces for Tachidesk:
1. [webUI](https://github.com/Suwayomi/Tachidesk/tree/master/webUI/react): The react SPA that Tachidesk is traditionally shipped with.
2. [TachideskJUI](https://github.com/Suwayomi/TachideskJUI): A Jetbrains Compose Native app, re-uses components made for the upcoming Tachiyomi 1.x
3. [Equinox](https://github.com/Suwayomi/Equinox): A web user interface made with Vue.js, in super early stages of development.

## Building from source
### Prerequisites
You need these software packages installed in order to build the project
### Server
- Java Development Kit and Java Runtime Environment version 8 or newer(both Oracle JDK and OpenJDK works)
- Android stubs jar
    - Manual download: Download [android.jar](https://raw.githubusercontent.com/Suwayomi/Tachidesk/android-jar/android.jar) and put it under `AndroidCompat/lib`.
    -  Automated download: Run `AndroidCompat/getAndroid.sh`(MacOS/Linux) or `AndroidCompat/getAndroid.ps1`(Windows) from project's root directory to download and rebuild the jar file from Google's repository.
### webUI
- Nodejs LTS or latest
- Yarn
- Git
### building the full-blown jar
Run `./gradlew :webUI:copyBuild server:shadowJar`, the resulting built jar file will be `server/build/Tachidesk-vX.Y.Z-rxxx.jar`.
### building without `webUI` bundled(server only)
Delete the `server/src/main/resources/react` directory if exists from previous runs, then run `./gradlew server:shadowJar`, the resulting built jar file will be `server/build/Tachidesk-vX.Y.Z-rxxx.jar`.
### building the Windows package
Run `./gradlew :server:windowsPackage` to build a server only bundle and `./gradlew :webUI:copyBuild :server:windowsPackage` to get a full bundle , the resulting built zip package file will be `server/build/Tachidesk-vX.Y.Z-rxxx-win32.zip`.
## Running in development mode
First satistify [the prerequisites](#prerequisites)
### server
run `./gradlew :server:run --stacktrace` to run the server
### webUI
How to do it is described in `webUI/react/README.md` but for short,
 first cd into `webUI/react` then run `yarn` to install the node modules(do this only once)
 then `yarn start` to start the development server, if a new browser window doesn't get opned automatically,
 then open `http://127.0.0.1:3000` in a modern browser. This is a `create-react-app` project
 and supports HMR and all the other goodies you'll need.

