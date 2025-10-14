
| Build                                                                                         | Stable                                                                                                                                                                   | Preview                                                                                                                                                                                                                                           | Support Server |
|-----------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|
| ![CI](https://github.com/Suwayomi/Suwayomi-Server/actions/workflows/build_push.yml/badge.svg) | [![stable release](https://img.shields.io/github/release/Suwayomi/Suwayomi-Server.svg?maxAge=3600&label=download)](https://github.com/Suwayomi/Suwayomi-Server/releases) | [![preview](https://img.shields.io/badge/dynamic/json?url=https://github.com/Suwayomi/Suwayomi-Server-preview/raw/main/index.json&label=download&query=$.latest&color=blue)](https://github.com/Suwayomi/Suwayomi-Server-preview/releases/latest) | [![Discord](https://img.shields.io/discord/801021177333940224.svg?label=discord&labelColor=7289da&color=2c2f33&style=flat)](https://discord.gg/DDZdqZWaHA) |

## Table of Content
- [What is Suwayomi?](#what-is-suwayomi)
  - [Features](#features)
- [Suwayomi client projects](#suwayomi-client-projects)
  - [Actively Developed Clients](#actively-developed-clients)
  - [Inactive Clients (functional but outdated)](#inactive-clients-functional-but-outdated)
  - [Abandoned Clients (functionality unknown)](#abandoned-clients-functionality-unknown)
- [Downloading and Running the app](#downloading-and-running-the-app)
  - [Using Operating System Specific Bundles](#using-operating-system-specific-bundles)
    - [Windows](#windows)
    - [macOS](#macos)
    - [GNU/Linux](#gnulinux)
  - [Other methods of getting Suwayomi](#other-methods-of-getting-suwayomi)
    - [Docker](#docker)
    - [Arch Linux](#arch-linux)
    - [Debian/Ubuntu](#debianubuntu)
    - [NixOS](#nixos)
  - [Advanced Methods](#advanced-methods)
    - [Running the jar release directly](#running-the-jar-release-directly)
    - [Using Suwayomi Remotely](#using-suwayomi-remotely)
  - [Syncing With Mihon (Tachiyomi) and Neko](#syncing-with-mihon-tachiyomi-and-neko)
    - [The Suwayomi extension and tracker](#the-suwayomi-extension-and-tracker)
    - [The Suwayomi merge source in Neko](#the-suwayomi-merge-source-in-neko)
    - [Other methods](#other-methods)
  - [Troubleshooting and Support](#troubleshooting-and-support)
  - [Contributing and Technical info](#contributing-and-technical-info)
  - [Translation](#translation)
  - [Credit](#credit)
  - [License](#license)
  - [Disclaimer](#disclaimer)
<!-- Generated with https://ecotrust-canada.github.io/markdown-toc/ -->

# What is Suwayomi?
<img src="https://github.com/Suwayomi/Suwayomi-Server/raw/master/server/src/main/resources/icon/faviconlogo.png" alt="drawing" width="200"/>

A free and open source manga reader server that runs extensions built for [Mihon (Tachiyomi)](https://mihon.app/). 

Suwayomi is an independent Mihon (Tachiyomi) compatible software and is **not a Fork of** Mihon (Tachiyomi).

Suwayomi-Server is as multi-platform as you can get. Any platform that runs java and/or has a modern browser can run it. This includes Windows, Linux, macOS, chrome OS, etc. Follow [Downloading and Running the app](#downloading-and-running-the-app) for installation instructions.

You can use Mihon (Tachiyomi) to access your Suwayomi-Server. For more info look [here](#syncing-with-mihon-tachiyomi).

## Features
> [!NOTE]
>
> These are capabilities of Suwayomi-Server, the actual working support is provided by each front-end app, checkout their respective readme for more info.

- Installing and executing Mihon (Tachiyomi)'s Extensions, So you'll get the same sources
- Searching and browsing installed sources
- A library to save your mangas and categories to put them into
- Automated library updates to check for new chapters
- Automated download of new chapters
- Viewing latest updated chapters
- Ability to download Manga for offline read
- Backup and restore support powered by Mihon (Tachiyomi)-compatible Backups
- Automated backup creations
- Tracking via [MyAnimeList](https://myanimelist.net/), [AniList](https://anilist.co/), [MangaUpdates](https://www.mangaupdates.com/), etc.
- [FlareSolverr](https://github.com/FlareSolverr/FlareSolverr) support to bypass Cloudflare protection
- Automated WebUI updates (supports the default WebUI and VUI)
- OPDS and OPDS-PSE support (endpoint: `/api/opds/v1.2`)

# Suwayomi client projects
**You need a client/user interface app as a front-end for Suwayomi-Server, if you [Directly Download Suwayomi-Server](https://github.com/Suwayomi/Suwayomi-Server/releases/latest) you'll get a bundled version of [Suwayomi-WebUI](https://github.com/Suwayomi/Suwayomi-WebUI) with it.**

Here's a list of known clients/user interfaces for Suwayomi-Server (checkout the respective GitHub repository for their features):
##### Actively Developed Clients
- [Suwayomi-WebUI](https://github.com/Suwayomi/Suwayomi-WebUI): The web front-end that Suwayomi-Server ships with by default.
- [Suwayomi-VUI](https://github.com/Suwayomi/Suwayomi-VUI): A Suwayomi-Server preview focused web frontend built with svelte
- [Tachidesk-VaadinUI](https://github.com/Suwayomi/Tachidesk-VaadinUI): A Web front-end for Suwayomi-Server built with Vaadin.
##### Inactive Clients (functional but outdated)
- [Tachidesk-JUI](https://github.com/Suwayomi/Tachidesk-JUI): The native desktop front-end for Suwayomi-Server.
- [Tachidesk-Sorayomi](https://github.com/Suwayomi/Tachidesk-Sorayomi): A Flutter front-end for Desktop(Linux, windows, etc.), Web and Android with a User Interface inspired by Mihon (Tachiyomi).
#####  Abandoned Clients (functionality unknown)
- [Tachidesk-qtui](https://github.com/Suwayomi/Tachidesk-qtui): A C++/Qt front-end for mobile devices(Android/linux), feature support is basic.
- [Tachidesk-GTK](https://github.com/mahor1221/Tachidesk-GTK): A native Rust/GTK desktop client.
- [Equinox](https://github.com/Suwayomi/Equinox): A web user interface made with Vue.js.

# Downloading and Running the app
## Using Operating System Specific Bundles
To facilitate the use of Suwayomi we provide bundle releases that include The Java Runtime Environment, ElectronJS and the Suwayomi-Launcher.

If a bundle for your operating system or cpu architecture is not provided then refer to [Advanced Methods](#advanced-methods)

### Windows
Download the latest `win64`(Windows 64-bit) release from [the releases section](https://github.com/Suwayomi/Suwayomi-Server/releases) or a preview one from [the preview repository](https://github.com/Suwayomi/Suwayomi-Server-preview/releases).

Unzip the downloaded file and double-click on one of the launcher scripts.

### macOS
Download the latest `macOS-x64`(older macOS systems) or `macOS-arm64`(Apple M1 and newer) release from [the releases section](https://github.com/Suwayomi/Suwayomi-Server/releases) or a preview one from [the preview repository](https://github.com/Suwayomi/Suwayomi-Server-preview/releases).

Unzip the downloaded file and double-click on one of the launcher scripts.

### GNU/Linux
Download the latest `linux-x64`(x86_64) release from [the releases section](https://github.com/Suwayomi/Suwayomi-Server/releases) or a preview one from [the preview repository](https://github.com/Suwayomi/Suwayomi-Server-preview/releases).

`tar xvf` the downloaded file and double-click on one of the launcher scripts or run them using the terminal.

#### WebView support (GNU/Linux)

WebView support is implemented via [KCEF](https://github.com/DATL4G/KCEF).
This is optional, and is only necessary to support some extensions.

To have a functional WebView, several dependencies are required; aside from X11 libraries necessary for rendering Chromium, some JNI bindings are necessary: gluegen and jogl (found in Ubuntu as `libgluegen2-jni` and `libjogl2-jni`).
Note that on some systems (e.g. Ubuntu), the JNI libraries are not automatically found, see below.

A KCEF server is launched on startup, which loads the X11 libraries.
If those are missing, you should see "Could not load 'jcef' library".
If so, use `ldd ~/.local/share/Tachidesk/bin/kcef/libjcef.so | grep not` to figure out which libraries are not found on your system.

The JNI bindings are only loaded when a browser is actually launched.
This is done by extensions that rely on WebView, not by Suwayomi itself.
If there is a problem loading the JNI libraries, you should see a message indicating the library and the search path.
This search path includes the current working directory, if you do not want to modify system directories.

Refer to the [Dockerfile](https://github.com/Suwayomi/Suwayomi-Server-docker/blob/main/Dockerfile) for more details.

## Other methods of getting Suwayomi
### Docker
Check our Official Docker release [Suwayomi Container](https://github.com/orgs/Suwayomi/packages/container/package/tachidesk) for running Suwayomi Server in a docker container. Source code for our container is available at [docker-tachidesk](https://github.com/Suwayomi/docker-tachidesk), an example compose file can also be found there. By default, the server will be running on http://localhost:4567 open this url in your browser.

### Arch Linux
You can install Suwayomi from the AUR:
```
yay -S suwayomi-server-bin
```

### Debian/Ubuntu
Download the latest deb package from the release section.

> [!CAUTION]
> These options are outdated and unmaintained ([relevant issue](https://github.com/Suwayomi/Suwayomi-Server/issues/1318))
> ### MPR
> ```
> git clone https://mpr.makedeb.org/tachidesk-server.git
> cd tachidesk-server
> makedeb -si
> ```
> ### Ubuntu
> ```
> sudo add-apt-repository ppa:suwayomi/tachidesk-server
> sudo apt update
> sudo apt install tachidesk-server
> ```

### NixOS
You can deploy Suwayomi on NixOS using the module `services.suwayomi-server` in your configuration:

```
{
  services.suwayomi-server = {
    enable = true;
  };
}
```

For more information, see [the NixOS manual](https://nixos.org/manual/nixos/stable/#module-services-suwayomi-server).

You can also directly use the package from [nixpkgs](https://search.nixos.org/packages?channel=unstable&type=packages&query=suwayomi-server).

## Advanced Methods
### Running the jar release directly
In order to run the app you need the following:
- The jar release of Suwayomi-Server
- The Java Runtime Environment(JRE) 21 or newer
- A Browser like Google Chrome, Firefox, Edge, etc.
- ElectronJS (optional)

Download the latest `.jar` release from [the releases section](https://github.com/Suwayomi/Suwayomi-Server/releases) or a preview jar build from [the preview repository](https://github.com/Suwayomi/Suwayomi-Server-preview/releases).

Make sure you have The Java Runtime Environment installed on your system, Double-click on the jar file or run `java -jar Suwayomi-Server-vX.Y.Z-rxxxx.jar` from a Terminal/Command Prompt window to run the app which will open a new browser window automatically.

### Using Suwayomi Remotely
You can run Suwayomi on your computer or a server and connect to it remotely through one of our clients or the bundled web interface with a web browser. This method of using Suwayomi is requiring a bit of networking/firewall/port forwarding/server configuration/etc. knowledge on your side, if you can run a Minecraft server and configure it, then you are good to go.

Check out [this wiki page](https://github.com/Suwayomi/Suwayomi-Server/wiki/Configuring-Tachidesk-Server) for a guide on configuring Suwayomi-Server. 

If you face issues with your setup then we are happy to provide help, just join our discord server(a discord badge is on the top of the page, you are just a click-clack away!).

## Syncing With Mihon (Tachiyomi) and Neko
### The Suwayomi extension and tracker
- You can install and configure the `Suwayomi` [extension](https://github.com/Suwayomi/tachiyomi-extension) inside Mihon (Tachiyomi) and forks.
- The extension will load your Suwayomi library.
- By manipulating extension search filters you can browse your categories.
- You can enable the Suwayomi tracker to track reading progress with your Suwayomi server.
  - Note: to sync from
    - Mihon (Tachiyomi) to Suwayomi: Mihon (Tachiyomi) automatically updates the chapters read status when it's updating the tracker (e.g. while reading)
    - Suwayomi to Mihon (Tachiyomi): To sync Mihon (Tachiyomi) with Suwayomi, you have to open the manga's track information, then, Mihon (Tachiyomi) will automatically update its chapter list with the state from Suwayomi

### The Suwayomi merge source in Neko
- You can enable the `Suwayomi` source in the Merge Source settings
- You can merge titles in Neko with titles from your Suwayomi library.
- You can enable 2-way automatic sync to track reading progress with your Suwayomi server.
  - Note: only applies to merged titles
    - Neko automatically updates the chapters read status in Suwayomi
    - During updates, Neko will automatically update its chapter list with the read state from Suwayomi
    - This only pulls if the status is read, to prevent marking read chapters as unread in Neko

### Other methods
Checkout [this issue](https://github.com/Suwayomi/Suwayomi-Server/issues/159) for tracking progress.

## Troubleshooting and Support
See [this troubleshooting wiki page](https://github.com/Suwayomi/Suwayomi-Server/wiki/Troubleshooting).

## Contributing and Technical info
See [CONTRIBUTING.md](./CONTRIBUTING.md).

## Translation
Feel free to translate the project on [Weblate](https://hosted.weblate.org/projects/suwayomi/suwayomi-server/)

<details><summary>Translation Progress</summary>
<a href="https://hosted.weblate.org/engage/suwayomi-server/">
<img src="https://hosted.weblate.org/widgets/suwayomi/-/suwayomi-server/multi-auto.svg" alt="Translation status" />
</a>
</details>

## Credit
This project is a spiritual successor of [TachiWeb-Server](https://github.com/Tachiweb/TachiWeb-server), Many of the ideas and the groundwork adopted in this project comes from TachiWeb.

The `AndroidCompat` module was originally developed by [@null-dev](https://github.com/null-dev) for [TachiWeb-Server](https://github.com/Tachiweb/TachiWeb-server) and is licensed under `Apache License Version 2.0` and `Copyright 2019 Andy Bao and contributors`.

Parts of [Mihon (Tachiyomi)](https://github.com/mihonapp/mihon) is adopted into this codebase, also licensed under `Apache License Version 2.0` and `Copyright 2015 Javier Tomás`.

You can obtain a copy of `Apache License Version 2.0` from  http://www.apache.org/licenses/LICENSE-2.0

Changes to both codebases is licensed under `MPL v. 2.0` as the rest of this project.

## License

    Copyright (C) Contributors to the Suwayomi project

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

## Disclaimer

The developer of this application does not have any affiliation with the content providers available.
