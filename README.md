# Tachidesk
A free and open source manga reader that runs extensions built for [Tachiyomi](https://tachiyomi.org/). 

Tachidesk is as multi-platform as you can get. Any platform that runs java and/or has a modern browser can run it.

Ability to read and write Tachiyomi compatible backups and syncing is a planned feature.

## How do I run the thing?
#### Prerequisites
You should have The Java Runtime Environment(JRE) 8 or newer and a modern browser installed. Also an internet connection is required as almost everything this app does is downloading stuff. 

#### Running pre-built jar packages
Download the latest (or a working more stable) release from [the repo branch](https://github.com/AriaMoradi/Tachidesk/tree/repo) or obtain it from [the releases section](https://github.com/AriaMoradi/Tachidesk/releases).

Double click on the jar file or run `java -jar Tachidesk-latest.jar` or `java -jar Tachidesk-vX.Y.Z-rxxx.jar`

The server will be running on `http://localhost:4567`. A new browser window will be opened automatically. Also the System Tray Icon is your friend if you need to open the browser window again or close Tachidesk.

#### Running pre-built windows packages
Windows specific builds have java bundled inside them. Download the zip release insted of the jar release which is named `Tachidesk-latest-win32.zip` or `Tachidesk-vX.Y.Z-rxxx-win32.zip`, unizp it and run `server.exe`.

#### Running on Docker
Check [arbuilder's repo](https://github.com/arbuilder/Tachidesk-docker) out for more details and the dockerfile.

## Building from source
### Get Android stubs jar
#### Manual download
Download [android.jar](https://raw.githubusercontent.com/AriaMoradi/Tachidesk/android-jar/android.jar) and put it under `AndroidCompat/lib`.
#### Building from source(needs `bash`, `curl`, `base64`, `zip` to work)
Run `scripts/getAndroid.sh` from project's root directory to download and rebuild the jar file from Google's repository.
### building the jar
Run `./gradlew shadowJar`, the resulting built jar file will be `server/build/Tachidesk-vX.Y.Z-rxxx.jar`.
### building windows package
Run `./gradlew windowsPackage`, the resulting built zip package file will be `server/build/Tachidesk-vX.Y.Z-rxxx-win32.zip`.
## Running for development purposes
### `server` module
Run `./gradlew :server:run -x :webUI:copyBuild --stacktrace` to run the server
### `webUI` module
How to do it is described in `webUI/react/README.md` but for short,
 first cd into `webUI/react` then run `yarn` to install the node modules(do this only once)
 then `yarn start` to start the client if a new browser window doesn't start automatically,
 then open `http://127.0.0.1:3000` in a modern browser. This is a `create-react-app` project
 and supports HMR and all the other goodies you'll need.

## Is this application usable? Should I test it?
If you'd ask me, I'd tell you If you want to read your manga **online** from tachiyomi or in one place and bypass all the ads, you can use Tachidesk.

There are almost no quality of life features, including no library, no downloading for offline enjoyment and sadly no MangaDex search.

Anyways, for more info checkout [finished milestone #1](https://github.com/AriaMoradi/Tachidesk/issues/2) and [milestone #2](https://github.com/AriaMoradi/Tachidesk/projects/1) to see what's implemented.

## How does it work?
This project has two components: 
1. **server:** contains the implementation of [tachiyomi's extensions library](https://github.com/tachiyomiorg/extensions-lib) and uses an Android compatibility library to run apk extensions. All this concludes to serving a REST API to `webUI`.
2. **webUI:** A react SPA project that works with the server to do the presentation.

## Support
Join Tachidesk's [discord server](https://discord.gg/wgPyb7hE5d) to hang out with the community and receive support.

## Credit
The `AndroidCompat` module and `scripts/getAndroid.sh` was originally developed by [@null-dev](https://github.com/null-dev) for [TachiWeb-Server](https://github.com/Tachiweb/TachiWeb-server) and is licensed under `Apache License Version 2.0`.

Parts of [tachiyomi](https://github.com/tachiyomiorg/tachiyomi) is adopted into this codebase, also licensed under `Apache License Version 2.0`.

You can obtain a copy of `Apache License Version 2.0` from  http://www.apache.org/licenses/LICENSE-2.0

Changes to both codebases is licensed under `MPL v. 2.0` as the rest of this project.

## License

    Copyright (C) 2020-2021 Aria Moradi and contributors

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.
