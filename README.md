
| Build | Stable | Preview | Support Server |
|-------|----------|---------|---------|
| ![CI](https://github.com/Suwayomi/Tachidesk/actions/workflows/build_push.yml/badge.svg) | [![stable release](https://img.shields.io/github/release/Suwayomi/Tachidesk.svg?maxAge=3600&label=download)](https://github.com/Suwayomi/Tachidesk/releases) | [![preview](https://img.shields.io/badge/dynamic/json?url=https://github.com/Suwayomi/Tachidesk-preview/raw/main/index.json&label=download&query=$.latest&color=blue)](https://github.com/Suwayomi/Tachidesk-preview/releases/latest) | [![Discord](https://img.shields.io/discord/801021177333940224.svg?label=discord&labelColor=7289da&color=2c2f33&style=flat)](https://discord.gg/DDZdqZWaHA) |

# What is Tachidesk?
<img src="https://github.com/Suwayomi/Tachidesk/raw/master/server/src/main/resources/icon/faviconlogo.png" alt="drawing" width="200"/>

A free and open source manga reader server that runs extensions built for [Tachiyomi](https://tachiyomi.org/). 

Tachidesk is an independent Tachiyomi compatible software and is **not a Fork of** Tachiyomi.

Tachidesk-Server is as multi-platform as you can get. Any platform that runs java and/or has a modern browser can run it. This includes Windows, Linux, macOS, chrome OS, etc. Follow [Downloading and Running the app](#downloading-and-running-the-app) for installation instructions.

Ability to read and write Tachiyomi compatible backups and syncing is a planned feature.

# Tachidesk-Server is a server app! You may not want to Download Tachidesk-Server directly.
Yes, you need a client/user interface app as a front-end for Tachidesk-Server, if you Directly Download Tachidesk-Server you'll get a bundled version of [Tachidesk-WebUI](https://github.com/Suwayomi/Tachidesk-WebUI) with it.

Here's a list of known clients/user interfaces for Tachidesk-Server:
- [Tachidesk-JUI](https://github.com/Suwayomi/Tachidesk-JUI): The "official" native desktop front-end for Tachidesk-Server. Currently the most advanced.
- [Tachidesk-WebUI](https://github.com/Suwayomi/Tachidesk-WebUI): The web/ElectronJS front-end that Tachidesk-Server is traditionally shipped with. Usually gets new features faster.
- [Tachidesk-qtui](https://github.com/Suwayomi/Tachidesk-qtui): A C++/Qt front-end for mobile devices(Android/linux), in super early stage of development.
- [Equinox](https://github.com/Suwayomi/Equinox): A web user interface made with Vue.js, in super early stage of development.

## Is this application usable? Should I test it?
Here is a list of current features:

- From Tachiyomi
    - Installing and executing Tachiyomi's Extensions, So you'll get the same sources
    - A library to save your mangas and categories to put them into
    - Searching and browsing installed sources
    - Ability to download Manga for offline read
    - Backup and restore support powered by Tachiyomi Backups
    - Viewing latest updated chapters.
- From Aniyomi
    - Installing and executing Aniyomi's Extensions
    - Searching and browsing installed sources.
    - Viewing an anime and it's episodes

**Note:** These are capabilities of Tachidesk-Server, the actual working support is provided by each front-end app, checkout their respective readme for more info.

**Note:** Tachidesk-Server is alpha software and can break rarely and/or with each update. See [Troubleshooting](https://github.com/Suwayomi/Tachidesk-Server/wiki/Troubleshooting) if it happens.

**Note:** “THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.”

# Downloading and Running the app
## General Requirements
In order to use the app effectively you need the following:
- The jar release of Tachideesk-Server
- The Java Runtime Environment(JRE) 8 or newer (included in bundle releases)
- A Modern Browser like Google Chrome, Firefox, etc.
- ElectronJS (optional) (included in bundle releases)
- An internet connection (when you want to use online features)
## Using the jar release directly 
Download the latest `.jar` release from [the releases section](https://github.com/Suwayomi/Tachidesk-Server/releases) or a preview jar build from [the preview repository](https://github.com/Suwayomi/Tachidesk-preview/releases).

Make sure you have The Java Runtime Environment installed on your system, Double click on the jar file or run `java -jar Tachidesk-vX.Y.Z-rxxx.jar` (or `java -jar Tachidesk-latest.jar` if you have the latest preview) from a Terminal/Command Prompt window to run the app which will open a new browser window automatically. Also the System Tray Icon is your friend if you need to open the browser window again or close Tachidesk.

## Using Operating System Specific Bundles
To facilitate the use of Tachidesk we provide bundle releases that include The Java Runtime Environment, ElectronJS and 3 Tachidesk Launcher Scripts.

#### Launcher Scripts
- `Tachidesk Electron Launcher`: Launches Tachidesk inside Electron as a desktop applicaton
- `Tachidesk Browser Launcher`: Launches Tachidesk in a browser window
- `Tachidesk Debug Launcher`: Launches Tachidesk with debug logs attached. If Tachidesk doesn't work for you, running this can give you insight into why.

**Node:** Linux launcher scripts are named a bit differently but work the same. 

### Windows
Download the latest `win32`(Windows 32-bit) or `win64`(Windows 64-bit) release from [the releases section](https://github.com/Suwayomi/Tachidesk/releases) or a preview one from [the preview repository](https://github.com/Suwayomi/Tachidesk-preview/releases).

Unzip the downloaded file and double click on one of the launcher scripts.

### macOS
Download the latest `macOS-x64`(older macOS systems) or `macOS-arm64`(Apple M1) release from [the releases section](https://github.com/Suwayomi/Tachidesk/releases) or a preview one from [the preview repository](https://github.com/Suwayomi/Tachidesk-preview/releases).

Unzip the downloaded file and double click on one of the launcher scripts.

### GNU/Linux
Download the latest `linux-x64`(x86_64) release from [the releases section](https://github.com/Suwayomi/Tachidesk/releases) or a preview one from [the preview repository](https://github.com/Suwayomi/Tachidesk-preview/releases).

`tar xvf` the downloaded file and double click on one of the launcher scripts or run them using the terminal.

## Other methods of getting Tachidesk
### Arch Linux
You can install Tachidesk from the AUR
```
yay -S tachidesk
```

### Docker
Check our Official Docker release [Tachidesk Container](https://github.com/orgs/Suwayomi/packages/container/package/tachidesk) for running Tachidesk Server in a docker container. Source code for our container is available at [docker-tachidesk](https://github.com/Suwayomi/docker-tachidesk). By default the server will be running on http://localhost:4567 open this url in your browser.

Install from the command line:
```
    $ docker pull ghcr.io/suwayomi/tachidesk
```
Run Container from the command line:
```
    $ docker run -p 4567:4567 ghcr.io/suwayomi/tachidesk
```

### Using Tachidesk Remotely
You can run Tachidesk on your computer or a server and connect to it remotely through the web interface with a web browser on any device including a mobile or tablet or even your smart TV!, this method of using Tachidesk is only recommended if you are a power user and know what you are doing.

## Troubleshooting and Support
See [this troubleshooting wiki page](https://github.com/Suwayomi/Tachidesk/wiki/Troubleshooting).

## Contributing and Technical info
See [CONTRIBUTING.md](./CONTRIBUTING.md).

## Credit
This project is a spiritual successor of [TachiWeb-Server](https://github.com/Tachiweb/TachiWeb-server), Many of the ideas and the groundwork adopted in this project comes from TachiWeb.

The `AndroidCompat` module was originally developed by [@null-dev](https://github.com/null-dev) for [TachiWeb-Server](https://github.com/Tachiweb/TachiWeb-server) and is licensed under `Apache License Version 2.0` and `Copyright 2019 Andy Bao and contributors`.

Parts of [tachiyomi](https://github.com/tachiyomiorg/tachiyomi) is adopted into this codebase, also licensed under `Apache License Version 2.0` and `Copyright 2015 Javier Tomás`.

You can obtain a copy of `Apache License Version 2.0` from  http://www.apache.org/licenses/LICENSE-2.0

Changes to both codebases is licensed under `MPL v. 2.0` as the rest of this project.

## License

    Copyright (C) Contributors to the Suwayomi project

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.
