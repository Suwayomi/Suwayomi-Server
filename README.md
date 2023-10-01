
| Build | Stable | Preview | Support Server |
|-------|----------|---------|---------|
| ![CI](https://github.com/Suwayomi/Tachidesk/actions/workflows/build_push.yml/badge.svg) | [![stable release](https://img.shields.io/github/release/Suwayomi/Tachidesk.svg?maxAge=3600&label=download)](https://github.com/Suwayomi/Tachidesk/releases) | [![preview](https://img.shields.io/badge/dynamic/json?url=https://github.com/Suwayomi/Tachidesk-preview/raw/main/index.json&label=download&query=$.latest&color=blue)](https://github.com/Suwayomi/Tachidesk-preview/releases/latest) | [![Discord](https://img.shields.io/discord/801021177333940224.svg?label=discord&labelColor=7289da&color=2c2f33&style=flat)](https://discord.gg/DDZdqZWaHA) |

## Table of Content
- [What is Tachidesk?](#what-is-tachidesk)
- [Tachidesk client projects](#tachidesk-client-projects)
  * [Is this application usable? Should I test it?](#is-this-application-usable-should-i-test-it)
- [Downloading and Running the app](#downloading-and-running-the-app)
  * [Using Operating System Specific Bundles](#using-operating-system-specific-bundles)
      - [Launcher Scripts](#launcher-scripts)
    + [Windows](#windows)
    + [macOS](#macos)
    + [GNU/Linux](#gnulinux)
  * [Other methods of getting Tachidesk](#other-methods-of-getting-tachidesk)
    + [Arch Linux](#arch-linux)
    + [Ubuntu-based distributions](#ubuntu-based-distributions)
    + [Docker](#docker)
  * [Advanced Methods](#advanced-methods)
    + [Running the jar release directly](#running-the-jar-release-directly)
    + [Using Tachidesk Remotely](#using-tachidesk-remotely)
- [Syncing With Tachiyomi](#syncing-with-tachiyomi)
- [Troubleshooting and Support](#troubleshooting-and-support)
- [Contributing and Technical info](#contributing-and-technical-info)
- [Credit](#credit)
- [License](#license)
<!-- Generated with https://ecotrust-canada.github.io/markdown-toc/ -->

# What is Tachidesk?
<img src="https://github.com/Suwayomi/Tachidesk/raw/master/server/src/main/resources/icon/faviconlogo.png" alt="drawing" width="200"/>

A free and open source manga reader server that runs extensions built for [Tachiyomi](https://tachiyomi.org/). 

Tachidesk is an independent Tachiyomi compatible software and is **not a Fork of** Tachiyomi.

Tachidesk-Server is as multi-platform as you can get. Any platform that runs java and/or has a modern browser can run it. This includes Windows, Linux, macOS, chrome OS, etc. Follow [Downloading and Running the app](#downloading-and-running-the-app) for installation instructions.

Ability to sync with Tachiyomi is a planned feature, for more info look [here](#syncing-with-tachiyomi).

# Tachidesk client projects
**You need a client/user interface app as a front-end for Tachidesk-Server, if you [Directly Download Tachidesk-Server](https://github.com/Suwayomi/Tachidesk-Server/releases/latest) you'll get a bundled version of [Tachidesk-WebUI](https://github.com/Suwayomi/Tachidesk-WebUI) with it.**

Here's a list of known clients/user interfaces for Tachidesk-Server:
##### Actively Developed Cients
- [Tachidesk-WebUI](https://github.com/Suwayomi/Tachidesk-WebUI): The web/ElectronJS front-end that Tachidesk-Server ships with by default.
- [Tachidesk-JUI](https://github.com/Suwayomi/Tachidesk-JUI): The native desktop front-end for Tachidesk-Server. Currently the most advanced.
- [Tachidesk-qtui](https://github.com/Suwayomi/Tachidesk-qtui): A C++/Qt front-end for mobile devices(Android/linux), feature support is basic.
- [Tachidesk-Sorayomi](https://github.com/Suwayomi/Tachidesk-Sorayomi): A Flutter front-end for Desktop(Linux, windows, etc.), Web and Android with a User Inerface inspired by Tachiyomi.
- [Tachidesk-VaadinUI](https://github.com/Suwayomi/Tachidesk-VaadinUI): A Web front-end for Tachidesk-Server built with Vaadin.
##### Inctive/Abandoned Cients
- [Equinox](https://github.com/Suwayomi/Equinox): A web user interface made with Vue.js.
- [Tachidesk-GTK](https://github.com/mahor1221/Tachidesk-GTK): A native Rust/GTK desktop client.

## Is this application usable? Should I test it?
Here is a list of current features:

- Installing and executing Tachiyomi's Extensions, So you'll get the same sources
- A library to save your mangas and categories to put them into
- Searching and browsing installed sources
- Ability to download Manga for offline read
- Backup and restore support powered by Tachiyomi-compatible Backups
- Viewing latest updated chapters.

**Note:** These are capabilities of Tachidesk-Server, the actual working support is provided by each front-end app, checkout their respective readme for more info.

# Downloading and Running the app
## Using Operating System Specific Bundles
To facilitate the use of Tachidesk we provide bundle releases that include The Java Runtime Environment, ElectronJS and 3 Tachidesk Launcher Scripts.

If a bundle for your operating system or cpu architecture is not provided then refer to [Advanced Methods](#advanced-methods) 

#### Launcher Scripts
- `Tachidesk Electron Launcher`: Launches Tachidesk inside Electron as a desktop applicaton
- `Tachidesk Browser Launcher`: Launches Tachidesk in a browser window
- `Tachidesk Debug Launcher`: Launches Tachidesk with debug logs attached. If Tachidesk doesn't work for you, running this can give you insight into why.

**Node:** Linux launcher scripts are named a bit differently but work the same. 

### Windows
Download the latest `win32`(Windows 32-bit) or `win64`(Windows 64-bit) release from [the releases section](https://github.com/Suwayomi/Tachidesk-Server/releases) or a preview one from [the preview repository](https://github.com/Suwayomi/Tachidesk-Server-preview/releases).

Unzip the downloaded file and double click on one of the launcher scripts.

### macOS
Download the latest `macOS-x64`(older macOS systems) or `macOS-arm64`(Apple M1 and newer) release from [the releases section](https://github.com/Suwayomi/Tachidesk-Server/releases) or a preview one from [the preview repository](https://github.com/Suwayomi/Tachidesk-Server-preview/releases).

Unzip the downloaded file and double click on one of the launcher scripts.

### GNU/Linux
Download the latest `linux-x64`(x86_64) release from [the releases section](https://github.com/Suwayomi/Tachidesk-Server/releases) or a preview one from [the preview repository](https://github.com/Suwayomi/Tachidesk-Server-preview/releases).

`tar xvf` the downloaded file and double click on one of the launcher scripts or run them using the terminal.

## Other methods of getting Tachidesk
### Arch Linux
You can install Tachidesk from the AUR:
```
yay -S tachidesk
```

### Debian/Ubuntu
Download the latest deb package from the release section or Install from the MPR
```
git clone https://mpr.makedeb.org/tachidesk-server.git
cd tachidesk-server
makedeb -si
```

### Ubuntu
```
sudo add-apt-repository ppa:suwayomi/tachidesk-server
sudo apt update
sudo apt install tachidesk-server
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

## Advanced Methods
### Running the jar release directly
In order to run the app you need the following:
- The jar release of Tachidesk-Server
- The Java Runtime Environment(JRE) 8 or newer
- A Browser like Google Chrome, Firefox, Edge, etc.
- ElectronJS (optional)

Download the latest `.jar` release from [the releases section](https://github.com/Suwayomi/Tachidesk-Server/releases) or a preview jar build from [the preview repository](https://github.com/Suwayomi/Tachidesk-preview/releases).

Make sure you have The Java Runtime Environment installed on your system, Double click on the jar file or run `java -jar Tachidesk-vX.Y.Z-rxxxx.jar` from a Terminal/Command Prompt window to run the app which will open a new browser window automatically.

### Using Tachidesk Remotely
You can run Tachidesk on your computer or a server and connect to it remotely through one of our clients or the bundled web interface with a web browser. This method of using Tachidesk is requires a bit of networking/firewall/port forwarding/server configuration/etc. knowledge on your side, if you can run a Minecraft server and configure it, then you are good to go.

Check out [this wiki page](https://github.com/Suwayomi/Tachidesk-Server/wiki/Configuring-Tachidesk-Server) for a guide on configuring Tachidesk-Server. 

If you face issues with your setup then we are happy to provide help, just join our discord server(a discord badge is on the top of the page, you are just a click clack away!).

## Syncing With Tachiyomi
### The Suwayomi extension and tracker
- You can install the `Suwayomi` extension inside tachiyomi.
- The extension will load your Tachidesk library.
- By manipulating extension search filters you can browse your categories.
- You can enable the Suwayomi tracker to track reading progress with your Tachidesk server.
  - Note: Tachiyomi [only allowes tracking one way](https://github.com/tachiyomiorg/tachiyomi/issues/1626), meaning that by reading chapters on other Tachidesk clients the last read chapter number will updated on the tracker but tachiyomi won't automatically mark them as read for you.

### Other methods
Checkout [this issue](https://github.com/Suwayomi/Tachidesk-Server/issues/159) for tracking progress.

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

## Disclaimer

The developer of this application does not have any affiliation with the content providers available.
