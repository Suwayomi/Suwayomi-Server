# Tachidesk
A free and open source manga reader that runs extensions built for [Tachiyomi](https://tachiyomi.org/). 

Tachidesk is as multi-platform as you can get. Any platform that runs java and/or has a modern browser can run it.

Ability to read and write Tachiyomi compatible backups and syncing is a planned feature.

## Is this application usable? Should I test it?
Here is a list of current features:

- Installing and executing Tachiyomi's Extensions, So you'll get the same sources.
- A library to save your mangas and categories to put them into.
- Searching and browseing installed sources.
- A minimal manga reader.
- Ability to download Mangas for offline read(This partially works)

**Note:** Keep in mind that Tachidesk is alpha software and can break rarely and/or with each update, so you may have to delete your data to fix it. See [General troubleshooting](#general-troubleshooting) and [Support and help](#support-and-help) if it happens.

Anyways, for more info checkout [finished milestone #1](https://github.com/AriaMoradi/Tachidesk/issues/2) and [milestone #2](https://github.com/AriaMoradi/Tachidesk/projects/1) to see what's implemented in more detail.

## Downloading and Running the app
#### Prerequisites
You should have The Java Runtime Environment(JRE) 8 or newer (if you're not planning to use the Windows specific build) and a modern browser installed. Also an internet connection is required as almost everything this app does is downloading stuff. 

#### Download the app
Download the latest jar or windows(win32) release from [the releases section](https://github.com/AriaMoradi/Tachidesk/releases).

#### Running pre-built jar packages
Double click on the jar file or run `java -jar Tachidesk-vX.Y.Z-rxxx.jar` from a Terminal/Command Prompt window to run the app which will open a new browser window automatically. Also the System Tray Icon is your friend if you need to open the browser window again or close Tachidesk.

#### Running pre-built Windows packages
Windows specific builds have java bundled inside them, so you don't have to install java to use it. Unzip `Tachidesk-vX.Y.Z-rxxx-win32.zip` and run `server.exe`, the rest will work like the jar release.

#### Running on Docker
Check [arbuilder's repo](https://github.com/arbuilder/Tachidesk-docker) out for more details and the dockerfile.

## General troubleshooting
If the app breaks try deleting the directory below and re-running the app (**This will delete all your data!**) and if the problem persists open an issue. 

On Mac OS X : `/Users/<Account>/Library/Application Support/Tachidesk`

On Windows XP : `C:\Documents and Settings<Account>\Application Data\Local Settings\Tachidesk`

On Windows 7 and later : `C:\Users<Account>\AppData\Tachidesk`

On Unix/Linux : `/home/<account>/.local/share/Tachidesk`

## Support and help
Join Tachidesk's [discord server](https://discord.gg/wgPyb7hE5d) to hang out with the community and receive support and help.

## How does it work?
This project has two components: 
1. **server:** contains the implementation of [tachiyomi's extensions library](https://github.com/tachiyomiorg/extensions-lib) and uses an Android compatibility library to run apk extensions. All this concludes to serving a REST API to `webUI`.
2. **webUI:** A react SPA project that works with the server to do the presentation.

## Building from source
### Get Android stubs jar
#### Manual download
Download [android.jar](https://raw.githubusercontent.com/AriaMoradi/Tachidesk/android-jar/android.jar) and put it under `AndroidCompat/lib`.
#### Automated download(needs `bash`, `curl`, `base64`, `zip` to work)
Run `scripts/getAndroid.sh` from project's root directory to download and rebuild the jar file from Google's repository.
### building the jar
Run `./gradlew shadowJar`, the resulting built jar file will be `server/build/Tachidesk-vX.Y.Z-rxxx.jar`.
### building the Windows package
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
