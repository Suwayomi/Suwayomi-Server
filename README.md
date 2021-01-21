# Tachidesk
A free and open source manga reader than runs extensions built for [Tachiyomi](https://tachiyomi.org/) which runs on desktop operating systems.

Ability to read and write Tachiyomi compatible backups and syncing is a planned feature.

## How does it work?
This project has two components: 
1. **server:** contains the implementation of [tachiyomi's extensions library](https://github.com/tachiyomiorg/extensions-lib) and uses an Android compatibility library to run apk extensions. All this concludes to serving a REST API to `webUI`.
2. **webUI:** A react SPA project that works with the server to do the presentation.

## How do I run the thing?
#### Running pre-built jar packages
Download the latest (or a working more stable older) release from [the repo branch](https://github.com/AriaMoradi/Tachidesk/tree/repo) or obtain it from [the releases section](https://github.com/AriaMoradi/Tachidesk/releases).

Double click on the jar file or run `java -jar Tachidesk-latest.jar` or `java -jar Tachidesk-vX.Y.Z-rxxx.jar`

The server will be running on `http://localhost:4567` open this url in your browser.

## Building from source
### Get Android stubs jar
#### Manual download
Download [android.jar](https://raw.githubusercontent.com/AriaMoradi/Tachidesk/android-jar/android.jar) and put it under `AndroidCompat/lib`.
#### Building from source(needs `bash`, `curl`, `base64`, `zip` to work)
Run `scripts/getAndroid.sh` from project's root directory to download and rebuild the jar file from Google's repository.
### building the jar
Run `./gradlew shadowJar` the resulting built jar file will be `server/build/Tachidesk-vX.Y.Z-rxxx.jar`.
## running for development purposes
### `server` module
Run `./gradlew :server:run -x :webUI:copyBuild --stacktrace` to run the server
### `webUI` module
How to do it is described in `webUI/react/README.md` but for short,
 first cd into `webUI/react` then run `yarn` to install the node modules(do this only once)
 then `yarn start` to start the client if a new browser window doesn't start automatically,
 then open `http://127.0.0.1:3000` in a modern browser. This is a `creat-react-app` project/
 and supports HMR and other goodies it provides.

## Is this application usable? Should I test it?
Checkout [the state of project](https://github.com/AriaMoradi/Tachidesk/issues/2) to see what's implemented.

## Credit
The `AndroidCompat` module and `scripts/getAndroid.sh` was originally developed by [@null-dev](https://github.com/null-dev) for [TachiWeb-Server](https://github.com/Tachiweb/TachiWeb-server) and is licensed under `Apache License Version 2.0`.

Parts of [tachiyomi](https://github.com/tachiyomiorg/tachiyomi) is adopted into this codebase, also licensed under `Apache License Version 2.0`.

## License

    Copyright (C) 2020-2021 Aria Moradi and contributors

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.
