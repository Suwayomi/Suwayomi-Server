
| Build | Stable | Preview | Support Server |
|-------|----------|---------|---------|
| ![CI](https://github.com/Suwayomi/Tachidesk/actions/workflows/build_push.yml/badge.svg) | [![stable release](https://img.shields.io/github/release/Suwayomi/Tachidesk.svg?maxAge=3600&label=download)](https://github.com/Suwayomi/Tachidesk/releases) | [![preview](https://img.shields.io/badge/dynamic/json?url=https://github.com/Suwayomi/Tachidesk/raw/preview/index.json&label=download&query=$.latest&color=blue)](https://github.com/Suwayomi/Tachidesk/tree/preview/latest_pointer) | [![Discord](https://img.shields.io/discord/801021177333940224.svg?label=discord&labelColor=7289da&color=2c2f33&style=flat)](https://discord.gg/DDZdqZWaHA) |

# Tachidesk
<img src="https://github.com/Suwayomi/Tachidesk/raw/master/server/src/main/resources/icon/faviconlogo.png" alt="drawing" width="200"/>

A free and open source manga reader that runs extensions built for [Tachiyomi](https://tachiyomi.org/). 

Tachidesk is an independent Tachiyomi compatible software and is **not a Fork of** Tachiyomi.

Tachidesk is as multi-platform as you can get. Any platform that runs java and/or has a modern browser can run it. This includes Windows, Linux, macOS, chrome OS, etc. Follow [Downloading and Running the app](#downloading-and-running-the-app) for installation instructions.

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

## Downloading and Running the app
### All Operating Systems
You should have The Java Runtime Environment(JRE) 8 or newer and a modern browser installed(Google is your friend for seeking assitance). Also an internet connection is required as almost everything this app does is downloading stuff. 

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
Or the latest preview version
```
yay -S tachidesk-preview
```

### Docker
Check [arbuilder's repo](https://github.com/arbuilder/Tachidesk-docker) out for more details and the dockerfile.

### Using Tachidesk Remotely
You can run Tachidesk on your computer or a server and connect to it remotely through the web interface with a web browser on any device including a mobile or tablet or even your smart TV!, this method of using Tachidesk is only recommended if you are a power user and know what you are doing.

## Troubleshooting and Support
See [this troubleshooting wiki page](https://github.com/Suwayomi/Tachidesk/wiki/Troubleshooting).

## Contributing and Technical info
See [CONTRIBUTING.md](./CONTRIBUTING.md).

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
