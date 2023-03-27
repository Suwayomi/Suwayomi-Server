# Contributing
## Where should I start?
Checkout [This Kanban Board](https://github.com/Suwayomi/Tachidesk/projects/1) to see the rough development roadmap.

### Important notes
- Notify the developers on [Suwayomi discord](https://discord.gg/DDZdqZWaHA) (#tachidesk-server and #tachidesk-webui channels) or open a WIP pull request before starting if you decide to take on working on anything from/not from the roadmap in order to avoid parallel efforts on the same issue/feature.
- Your pull request will be squashed into a single commit.
- We hate big pull requests, make them as small as possible, change one meaningful thing. Spam pull requests, we don't mind.

### Project goals and vision
- Porting Tachiyomi and covering it's features
- Syncing with Tachiyomi, [main issue](https://github.com/Suwayomi/Tachidesk-Server/issues/159)
- Generally rejecting features that Tachiyomi(main app) doesn't have,
    - Unless it's something that makes sense for desktop sizes or desktop form factor (keyboard + mouse)
    - Additional/crazy features can go in forks and alternative clients
- [Tachidesk-WebUI](https://github.com/Suwayomi/Tachidesk-WebUI) should
    - be responsive
    - support both desktop and mobile form factors well
     
## How does Tachidesk-Server work?
This project has two components: 
1. **Server:** contains the implementation of [tachiyomi's extensions library](https://github.com/tachiyomiorg/extensions-lib) and uses an Android compatibility library to run jar libraries converted from apk extensions. All this concludes to serving a REST API.
2. **WebUI:** A React SPA(`create-react-app`) project that works with the server to do the presentation located at https://github.com/Suwayomi/Tachidesk-WebUI

## Why a web app?
This structure is chosen to
- Achieve the maximum multi-platform-ness
- Gives the ability to access Tachidesk-Server from a remote client e.g., your phone, tablet or smart TV
- Ease development of user interfaces for Tachidesk

## Building from source
### Prerequisites
You need these software packages installed in order to build the project

- Java Development Kit and Java Runtime Environment version 8 or newer(both Oracle JDK and OpenJDK works)

### building the full-blown jar (Tachidesk-Server + Tachidesk-WebUI bundle)
Run `./gradlew server:downloadWebUI server:shadowJar`, the resulting built jar file will be `server/build/Tachidesk-Server-vX.Y.Z-rxxx.jar`.

### building without `webUI` bundled (server only)
Delete `server/src/main/resources/WebUI.zip` if exists from previous runs, then run `./gradlew server:shadowJar`, the resulting built jar file will be `server/build/Tachidesk-Server-vX.Y.Z-rxxx.jar`.

### building the Windows package
First Build the jar, then cd into the `scripts` directory and run `./windows-bundler.sh win32` or `./windows-bundler.sh win64` depending on the target architecture, the resulting built zip package file will be `server/build/Tachidesk-Server-vX.Y.Z-rxxx-winXX.zip`.

## Running in development mode
run `./gradlew :server:run --stacktrace` to run the server

## Running tests
run `./gradlew :server:test` to execute all tests
to test a specific class run `./gradlew :server:test --tests <package.with.classname>`

## Building the android-jar maven repository
Run `AndroidCompat/getAndroid.sh`(macOS/Linux) or `AndroidCompat/getAndroid.ps1`(Windows)
from project's root directory to download and rebuild the jar file from Google's repository,
then use `AndroidCompat/lib/android.jar` to manually create a maven repository inside the `android-jar` git branch.
Update the dependency declaration afterwards.
