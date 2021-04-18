
| Build | Stable | Preview | Support Server |
|-------|----------|---------|---------|
| ![CI](https://github.com/Suwayomi/Tachidesk/actions/workflows/build_push.yml/badge.svg) | [![stable release](https://img.shields.io/github/release/Suwayomi/Tachidesk.svg?maxAge=3600&label=download)](https://github.com/Suwayomi/Tachidesk/releases) | [![preview](https://img.shields.io/badge/dynamic/json?url=https://github.com/Suwayomi/Tachidesk/raw/preview/index.json&label=download&query=$.latest&color=blue)](https://github.com/Suwayomi/Tachidesk/raw/preview/Tachidesk-latest.jar) | [![Discord](https://img.shields.io/discord/801021177333940224.svg?label=discord&labelColor=7289da&color=2c2f33&style=flat)](https://discord.gg/DDZdqZWaHA) |

# Tachidesk
<img src="https://github.com/Suwayomi/Tachidesk/raw/master/server/src/main/resources/icon/faviconlogo.png" alt="drawing" width="200"/>

A free and open source manga reader that runs extensions built for [Tachiyomi](https://tachiyomi.org/). 

Tachidesk is as multi-platform as you can get. Any platform that runs java and/or has a modern browser can run it.

Ability to read and write Tachiyomi compatible backups and syncing is a planned feature.

## Is this application usable? Should I test it?
Here is a list of current features:

- Installing and executing Tachiyomi's Extensions, So you'll get the same sources.
- A library to save your mangas and categories to put them into.
- Searching and browsing installed sources.
- A decent chapter reader.
- Ability to download Mangas for offline read(This partially works)
- Backup and restore support powered by Tachiyomi Legacy Backups

**Note:** Keep in mind that Tachidesk is alpha software and can break rarely and/or with each update, so you may have to delete your data to fix it. See [General troubleshooting](#general-troubleshooting) and [Support and help](#support-and-help) if it happens.

Anyways, for more info checkout [finished milestone #1](https://github.com/Suwayomi/Tachidesk/issues/2) and [milestone #2](https://github.com/Suwayomi/Tachidesk/projects/1) to see what's implemented in more detail.

## Downloading and Running the app
### All Operating Systems
You should have The Java Runtime Environment(JRE) 8 or newer and a modern browser installed. Also an internet connection is required as almost everything this app does is downloading stuff. 

Download the latest "Stable" jar release from [the releases section](https://github.com/Suwayomi/Tachidesk/releases) or a preview jar build from [the preview branch](https://github.com/Suwayomi/Tachidesk/tree/preview).

Double click on the jar file or run `java -jar Tachidesk-vX.Y.Z-rxxx.jar` (or `java -jar Tachidesk-latest.jar` if you have the latest preview) from a Terminal/Command Prompt window to run the app which will open a new browser window automatically. Also the System Tray Icon is your friend if you need to open the browser window again or close Tachidesk.

### Windows
Download the latest win32 release from [the releases section](https://github.com/Suwayomi/Tachidesk/releases).

The Windows specific build has java bundled inside, so you don't have to install java to use it. Unzip `Tachidesk-vX.Y.Z-rxxx-win32.zip` and run `server.exe`. The rest works like the previous section.

### Arch Linux
You can install Tachidesk from the AUR
```
yay -S tachidesk
```

### Docker
Check [arbuilder's repo](https://github.com/arbuilder/Tachidesk-docker) out for more details and the dockerfile.

## General troubleshooting
If the app breaks, make sure that it's not running(right click on tray icon and quit or kill it through the way your Operating System provides), delete the directory below and re-run the app (**This procedure will delete all your data!**) and if the problem persists open an issue or ask for help on discord. 

On Mac OS X : `/Users/<Account>/Library/Application Support/Tachidesk`

On Windows XP : `C:\Documents and Settings\<Account>\Application Data\Local Settings\Tachidesk`

On Windows 7 and later : `C:\Users\<Account>\AppData\Local\Tachidesk`

On Unix/Linux : `/home/<account>/.local/share/Tachidesk`

## Support and help
Join Tachidesk's [discord server](https://discord.gg/DDZdqZWaHA) to hang out with the community and to receive support and help.

## How does it work?
This project has two components: 
1. **server:** contains the implementation of [tachiyomi's extensions library](https://github.com/tachiyomiorg/extensions-lib) and uses an Android compatibility library to run apk extensions. All this concludes to serving a REST API to `webUI`.
2. **webUI:** A react SPA project that works with the server to do the presentation.

## Building from source
### Prerequisite: Get Android stubs jar
#### Manual download
Download [android.jar](https://raw.githubusercontent.com/Suwayomi/Tachidesk/android-jar/android.jar) and put it under `AndroidCompat/lib`.
#### Automated download
Run `AndroidCompat/getAndroid.sh`(MacOS/Linux) or `AndroidCompat/getAndroid.ps1`(Windows) from project's root directory to download and rebuild the jar file from Google's repository.
### Prerequisite: Software dependencies
You need this software packages installed in order to build this project:
- Java Development Kit and Java Runtime Environment version 8 or newer(both Oracle JDK and OpenJDK works)
- Nodejs LTS or latest
- Yarn
### building the full-blown jar
Run `./gradlew :webUI:copyBuild server:shadowJar`, the resulting built jar file will be `server/build/Tachidesk-vX.Y.Z-rxxx.jar`.
### building without `webUI` bundled(server only)
Delete the `server/src/main/resources/react` directory if exists from previous runs, then run `./gradlew server:shadowJar`, the resulting built jar file will be `server/build/Tachidesk-vX.Y.Z-rxxx.jar`.
### building the Windows package
Run `./gradlew :server:windowsPackage` to build a server only bundle and `./gradlew :webUI:copyBuild :server:windowsPackage` to get a full bundle , the resulting built zip package file will be `server/build/Tachidesk-vX.Y.Z-rxxx-win32.zip`.
## Running for development purposes
### `server` module
Follow [Get Android stubs jar](#prerequisite-get-android-stubs-jar) then run `./gradlew :server:run --stacktrace` to run the server
### `webUI` module
How to do it is described in `webUI/react/README.md` but for short,
 first cd into `webUI/react` then run `yarn` to install the node modules(do this only once)
 then `yarn start` to start the development server, if a new browser window doesn't get opned automatically,
 then open `http://127.0.0.1:3000` in a modern browser. This is a `create-react-app` project
 and supports HMR and all the other goodies you'll need.

## Credit
This project is a spiritual successor of [TachiWeb-Server](https://github.com/Tachiweb/TachiWeb-server), Many of the ideas and the groundwork adopted in this project comes from TachiWeb.

The `AndroidCompat` module was originally developed by [@null-dev](https://github.com/null-dev) for [TachiWeb-Server](https://github.com/Tachiweb/TachiWeb-server) and is licensed under `Apache License Version 2.0`.

Parts of [tachiyomi](https://github.com/tachiyomiorg/tachiyomi) is adopted into this codebase, also licensed under `Apache License Version 2.0`.

You can obtain a copy of `Apache License Version 2.0` from  http://www.apache.org/licenses/LICENSE-2.0

Changes to both codebases is licensed under `MPL v. 2.0` as the rest of this project.

## License

    Copyright (C) Contributors to the Suwayomi project

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.
