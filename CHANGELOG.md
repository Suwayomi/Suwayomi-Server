# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased] (Preview)

### Added
- .

### Changed
- .

### Fixed
- .

## [v2.3.2226] - 2026-07-01

### Fixed
- (**Extension/API**) Fix GQL handling of extensions without an extension store
- (**Build/Bundler**) Fix build continuing if errors happen

## [v2.3.2223] + [WebUI: v20260509.01] - 2026-06-30

### Major Changes

#### Added [SyncYomi](https://github.com/syncyomi/syncyomi) support
This allows you to sync your server manga with other Mihon-based forks! As long as the fork supports SyncYomi it can be sync with!

#### Support Extension API v1.6
This update allows Suwayomi to load and use v1.6 extensions, it is a minor improvement over the existing 1.4 extension API that cleans up much of what we had! It is the basis of future extension APIs that will allow for further development.

This also allows us to move to Mihon's Extension Store system and replace our Extension Repo system. Old Extension Repos are still compatible and will be automatically migrated if they move to the Extension Store system.

> [!WARNING]
> Please back up your Extension Repos, because of the new Extension Stores system you may lose them in the update process and may need to re-add them.

### Added
- (**Sync**) Added [SyncYomi](https://github.com/syncyomi/syncyomi) support
- (**OPDS**) Add option to skip chapter metadata feed providing direct stream/download links
- (**Extension/API**) Support Extensions API v1.6
- (**Tracker/API**) Add mutation to bind existing track record

### Changed
- (**Database/H2**) Use the latest H2 database engine
- (**Startup**) Crash on startup if an unrecoverable error happens
- (**WebView**) Use JCEF directly and update to newest Chromium
- (**Extension/Android**) Switch MessageQueue to LegacyMessageQueue from ConcurrentMessageQueue

### Fixed
- (**CloudFlareInterceptor**) Don't send the `cf_clearance` cookie back to Flaresolverr
- (**WebUI**) Handle serving non-default webui with "bundled"
- (**WebUI**) Wait until WebUI is ready to open in browser
- (**Downloads**) Truncate filenames by byte length to prevent "File name too long" IO errors
- (**Downloads**) Fix being unable to find downloads after manga was renamed during an update
- (**Downloads**) Fix preserving chapter download states during an update
- (**Extension**) Do not indicate an update is available when the extension is not installed
- (**Chapter**) Fix losing chapter data on failed chapter list update
- (**Chapter**) Fix database error when fetching chapter updates
- (**Manga/API**) Fix "mangas" graphql query with active sorting and using a PostgreSQL database (QUERY "mangas")
- (**API**) Fix GraphQL `Filter` `notAll` and `notAny` being inversed
- (**API**) Fix GraphQL `Filter` causing an UnsupportedOperationException when passing an empty list as a `Any` filter value
- (**Build**) Fix CURL failing silently in builds
- (**Backup/Database**) Fix backup creation slowdown when mapping chapters

## [v2.2.2100] + [WebUI: v20260508.01] - 2026-05-08

### Major Changes

#### Added UI Login Auth Method!
We now have another alternative login type! This is our most flexable option as it allows clients and the server to handle it together, it's not an all or nothing authentication method like the others. For example the web-based clients can now use thier own login pages, and the server can add data to the tokens it gives to the clients, improving performance while keeping the security standard high.

#### PostgreSQL Support!
We finally support alternative database types! H2 is a great database, but it has its issues. Using a more widespread database like PostgreSQL can avoid them while keeping the performance high! Note that PostgreSQL will need to be run externally. See our example [docker-compose.yml](https://github.com/Suwayomi/Suwayomi-Server-docker/blob/ffc7f6990e889d0a257efe3d94db085d5e73f420/docker-compose-postgresql.yml)

#### Remote Image Modification/Conversion
We now support remote image modification! This will allow you to use an external server that can change your images however you want, such as converting to JXL or WebP, or applying some upscaling or sharpening to them! This pairs well with our new option to convert images before it serves them to the client.
See our [conversion server](https://github.com/Suwayomi/Suwayomi-converter) for an example.

### Added
- (**Source/API**) Expose "baseUrl" (TYPE "SourceType")
- (**Extension**) Support author notes
- (**Extension/Android**) Add Main dispatcher implementation
- (**Extension/Android**) Add LruCache implementation
- (**Extension/Android**) Support basic BitmapFactory options
- (**Extension/Android**) Support Bitmap pixel-based access and modification
- (**Extension/Android**) Add Rect.set functionality
- (**OPDS**) Add reading progress synchronization for KOReader
- (**WebView**) Support copy & paste
- (**Authentication/API**) Add new "UI Login" authentication method (basic JWT implementation) (MUTATIONS "login", "refreshToken")
- (**Database**) Support PostgreSQL
- (**Wiki**) Add wiki to main repo to allow pull requests for improvements
- (**WebUI**) Add support to serve webUI on a subpath
- (**WebUI**) Support symlinks
- (**WebUI/API**) Expose update timestamp (TYPE "AboutWebUI")
- (**Backup/API**) Support backup flags during import (MUTATION "restoreBackup")
- (**Backup/Settings/API**) Support backup flags for automated backups (TYPE, MUTATIONS "settings")
- (**Image conversion/API**) Support image conversion during serve (TYPE, MUTATIONS "settings")
- (**Image conversion/API**) Support remote image conversion (TYPE, MUTATIONS "settings")
- (**Tracker**) Add Shikimori tracker
- (**Meta/API**) Add functionality for bulk meta updates/deletions (MUTATIONS "meta")
- (**Image**) Support JXL container format

### Changed
- (**OPDS**) Overhaule feeds for discovery, filtering and enhance UX
- (**OPDS**) Align feed generation with RFC5005 and OpenSearch specs
- (**Downloads**) Improve handling of valid existing downloads
- (**Downloads**) Optimize download queue
- (**Database**) Optimize database performance with HikariCP and transaction batching
- (**Java/JRE**) Move to Zulu JRE
- (**Java**) Update to JDK 25

### Fixed
- (**General**) Fix logging sensitive config data in cleartext
- (**Localization**) Fix falling back to the default locale in case no matching locale was found
- (**Authentication**) Fix serving page icons with set-up authentication
- (**Authentication**) Fix header/cookie based websocket authentication
- (**Authentication**) Fix "simple login" redirect causing protocol downgrade (https -> http)
- (**Downloads/API**) Fix CBZ download HEAD requests performance (HEAD "/chapter/:chapterId/download")
- (**WebView**) Fix alt key handling
- (**Source/API**) Fix handling of nullable preference keys (TYPE "preferences")
- (**Source**) Fix local manga thumbnails handling
- (**Extension**) Fix missing icon for manually installed source
- (**Extension**) Fix installation retry always fails
- (**Extension**) Fixed a java.lang.VerifyError when installing an extension that has ProGuard enabled.
- (**Backup**) Fix importing of backups with missing server settings
- (**Backup**) Fix importing of backups with invalid server settings
- (**Backup/API**) Fix missing backup creation flags (MUTATION "createBackup")
- (**Config**) Fix server startup config update failure handling
- (**WebUI**) Fix webUI setup blocking the server startup on internet connection issues
- (**WebUI**) Fix race condition in webUI update status updates
- (**WebUI**) Fix the missing webUI static folder causing a server crash on startup
- (**Chapter**) Fix out-of-order chapter pages
- (**API**) Fix graphql double values handling
- (**Tracker**) Fix Kitsu tracker to conform to tracker data structure properly
- (**Library update**) Fix stale manga data in update subscription status events
- (**Cloudflare/flaresolverr**) Fix sending POST requests as GET to flaresolverr

### WebUI
- [See WebUI changes here][WebUI: v20260508.01]

### Contributors
@cpiber, @Syer10, @lamaxama, @schroda, @AwkwardPeak7, @ItsGlassPlus1, @manti-X, @Youwes09, @renovate[bot], @D-Brox, @weblate, @Micka149, @TheRay82, @UnknownSkyrimPasserby, @KaceyKoo-gif, @333fred, @KolbyML, @Robonau, @ornaras, @SpicyCatGames, @FadedSociety, @ginocic, @zeedif, @CzechuPL, @mrintrepide, @renjfk, @thiagoalcr, @Smileskun, @dejavui, @allrobot

## [v2.1.1867] + [WebUI: v20250703.01] - 2025-07-31

### Webview!
We now have support for Webviews! The Webview uses KCEF, a variation of the widely supported JCEF and CEF. This allows us to provide the extension a background webview when it needs it, and allows us to provide users a way to login to websites in Suwayomi! This does not bypass Cloudflare though, it is easily detected as a Webview.

### Simple Login Menu
We have added an option to use a simple login menu instead of basic auth! This allows you to login to Suwayomi in places where Basic Auth is inconvenient, providing a measure of security.

### Image conversion on download
We now provide a simple image conversion setting that tells Suwayomi-Server to convert specific image types or everything it can on download. It supports the most common image formats, with more coming later.

### Backups
- Support history in backups
- Backup Suwayomi-specific data

### OPDS
- Add OPDS Chapter Filtering/Ordering
- Add OPDS internationalization

### Linux
- Add Java alternatives to deb package
- Add AppImage bundle for Linux

### Extension support
- Improve extension apk to jar conversion
- Fix data insertion when authors and artists are too long
- Fix multiple issues with extension settings
- Fix too long page URLs causing issues
- Support extension author notes and other image manipulation

### Tracking
- Sync tracking backend with Mihon
- Add private tracking

### More Changes!
- Improve cookie handling, share cookies between WebView and Extensions
- Fix PWA's when auth is enabled
- Prevent duplicated chapter pages
- Ignore hidden folders/archives for Local Source chapter list
- Improve downloads handling
- Improve library update and auto backup scheduling
- And many more fixes and features

### Contributors

@Syer10, @schroda, @Chiru-Dey, @renovate[bot], @cpiber, @KamaleiZestri, @weblate, @9811pc, @yutthaphon, @UnknownSkyrimPasserby, @TamilNeram, @dejavui, @Zereef, @marimo-nekomimi, @AwkwardPeak7, @D-Brox, @zeedif, @EdgeAtZero, @shirishsaxena, @BrutuZ

## [v2.0.1727] + [WebUI: v1.5.1] - 2025-04-21

> [!Caution]
> 
> If you previously used the MSI Installer, uninstall all Suwayomi-Server versions you have installed before installing the latest release! All your data will be untouched.

### Update to Java 21
This has been a long time coming! This release we focused on getting the server more stable and improving performance. With this we decided to finally move past Java 8 and into the future! We will continue updating to the latest Java LTS release from here on out. Make sure to update your runtimes!

### Add OPDS API
We have added API endpoints for OPDS and OPDS-PSE! With this your favourite mobile book readers will be able to download and read manga directly from your server! A little tip, it works best if you pre-download the chapters on Suwayomi-Server!

### More Tracking!
We have added support for the Bangumi and Kitsu Trackers. We hope you enjoy them, there is more to come later!

### Changing Version Scheme
We have decided to move to a major.minor.revision versioning scheme to improve compatibility on a few platforms between Stable and Preview. This shouldn't have any impact on how you use the app though, you will receive update notifications based on if you have preview or stable installed!

### More Changes!
- Fix MSI Installer
- Optimized Backup Import
- Improve JS Support
- Optimize included JRE
- And many more fixes and features

### Contributors

@schroda, @kaaass, @Syer10, @renovate[bot], @shirishsaxena, @showyee, @cpiber, @zeedif, @Robonau, @dejavui, @Belphemur, @AeonLucid

## [v1.1.1] + [WebUI: v1.1.0] - 2024-06-15

- Hotfix for WebUI updates

### Contributors

@schroda

## [v1.1.0] + [WebUI: v1.1.0] - 2024-06-15

With 1.1.0, we have been working on a few things that resolves bugs found in 1.0.0 and finalizes the tracking API.

### Tracking
This release has been working on the long awaited tracking support! With the last release we had a partial tracking api, which gave our client developers to decide what parts they liked, and what they didn't. With this feedback, we were able to finalize a API that resolves most needs of our client developers.

### And other things
- Update Manga Info in browse
- Improved support for library filters
- Improved thumbnail handling
- Many minor bugfixes

### Contributors

@schroda, @Syer10, @RatCornu, @FumoVite

## [v1.0.0] + [WebUI: v1.0.0] - 2024-02-23

We've done a lot since our last release over a year ago.

### GraphQL API
- We have redone our whole API in GraphQL. We are excited to get this out there so more Suwayomi clients like JUI and Sorayomi can use it! GraphQL is a great technology and provides much more flexibility then our previous REST API.

### Rename to Suwayomi
- We've had the rename in the works for a while, we wanted to have a better branding. Tachidesk doesn't actually make sense, its a mix of 2 languages, and Tachi means standing. We ended up deciding on Suwayomi, which is a shorthand for Suwariyomi(sitting reading).

### New Launcher for Suwayomi
- We needed a better experience when launching the application, a launcher was a great idea. We've got all the settings available in the launcher so you can configure everything you need before launching!

### More Changes!
- Automatic WebUI Updates
- Preserve download queue through server restarts
- Improve compatability with Android extensions
- Add support for ComicInfo creation and reading
- Support changing settings with WebUI and other clients
- Support more SOCKS proxy settings
- Fix support for Oracle JRE
- Performance improvements
- Partial Tracking support
- Support Custom Repos
- FlareSolverr(Cloudflare Bypass) support
- And many more fixes and features, this was a big release

### Contributors

@Syer10, @AriaMoradi, @schroda, @chancez, @Mercenar, @Robonau, @tachimanga, @brianmakesthings, @alexandrejournet, @aless2003, @vuhe, @MangaCrushTeam, @martinek, @akabhirav, @DattatreyaReddy

## [v0.7.0] + [WebUI: r983] - 2023-02-12

- CBZ downloads support
- Webview implementation based on Microsoft playwright, disabled for this release
- Fixed compatibility with some chinese extensions
- Support for Tachiyomi extensions lib 1.4
- WebUI changes:
  - Uhh, idk, find out yourself...

## [v0.6.6] + [WebUI: r963] - 2022-11-26

Huge thanks to @martinek who pulled the most of the weight this release!

- Batch actions for chapters
- Improved the downloader
- WebUI changes:
  - Support for chapter actions
  - a lot of code cleanup 
  - some bugfixes

## [v0.6.5] + [WebUI: r946] - 2022-09-18

- Fixed Windows bundler
- Fixed deb packaging stuff

## [v0.6.4] + [WebUI: r946] - 2022-08-18

- This release brings all new AI-assisted upscaled textures
  - Don't pay attention to the new game breaking bugs, we put them there intentionally
  - The product is half-finished and rushed out, we hope to fix it in upcoming paid patches and DLCs
- There are No new major features
- Added a few bug "fixes" and introduced even more bugs (features)
- Back ported some totally unnecessary community loved functionality from Tachidesk 2: Despicable Drafts and Tachidesk: Unpublished Stories

## [v0.6.3] + [WebUI: r942] - 2022-03-10

- Changes in Server
  - Support for array search filter changes list
  - Support for Tachiyomi extensions lib 1.3
- Changes in WebUI
  - Better search filter support
  - Fluid manga grid
  - Library comfortable grid
  - Sources view layouts 
  - Various other changes...

## [v0.6.2] + [WebUI: r929] - 2022-03-04

- Changes in WebUI
  - Moved search to Browse
  - Support for Source Filters
  - Better visuals for Download Queue
  - A live version of WebUI is now available [at this link](https://tachidesk-webui-preview.github.io/).

## [v0.6.1] + [WebUI: r911] - 2022-02-19

- msi and deb packages thanks to @mahor1221
- [Tachidesk-Flutter](https://github.com/Suwayomi/Tachidesk-Flutter) exists now!

## [v0.6.0] + [WebUI: r893] - 2021-12-29

- WebUI design went through a whole lot of changes, including
  - Got rid of hamburger menu, now we have a custom mobile navbar
  - Unread and Download count badges
  - Back button so better electron experience
  - There's a whole lot more that I'm too lazy to explore.
- Completely removed anime support
- Fixed category reordering
- Added support for search filters(Server side only)
- Added support for updating library(Server side only)
- A bunch of API breaking changes(hence why bumping to v0.6.0)!

## [v0.5.4] + [WebUI: r820] - 2021-10-18

- Fixed ReadComicOnline, Toonily and possibly other sources not working
- Backup and Restore now includes Updates tab data
- Removed Anime support from WebUI, Anime support will also be removed from Tachidesk-Server in a future update

## [v0.5.3] + [WebUI: r809] - 2021-09-28

- added support for a equivalent page to Tachiyomi's Updates tab
- fix launchers not working on macOS M1/arm64

## [v0.5.2] + [WebUI: r807] - 20209-19

- Fixed Local source not working on Windows
- Fixed Chapter names being shown incorrectly

## [v0.5.1] + [WebUI: r803] - 2021-09-18

- Loading sources' manga list is at least twice as fast
- Added support for Tachiyomi's Local source
- Added BasicAuth support, now you can protect your Tachidesk instance if you are running it on a public server
- Added ability to turn off cache for image requests

## [v0.5.0] + [WebUI: r789] - 2021-09-13

- You can now install APK extensions from the extensions page
- WebUI now comes with an updated Material Design looks and is faster a little bit.
- WebUI now shows Nsfw content by default, disable it in settings if you prefer to not see Nsfw stuff
- Added support for configuration of sources, this enables MangaDex, Komga, Cubari and many other sources
- Chapters in the Manga page and Sources in the source page now look nicer and will glow with mouse hover

## [v0.4.9] + [WebUI: r769] - 2021-09-03

### Tachidesk-Server
#### Public API
##### Non-breaking changes
- N/A

##### Breaking changes
- (r857) renamed: SourceDataClass.isNSFW -> SourceDataClass.isNsfw

##### Bug fixes
- N/A

#### Private API
- (r850) Bump WebUI version to r767
- (r861) Bump WebUI version to r769

##### Non-code changes
- (r851) Add this changelog file and CHANGELOG-TEMPLATE.md
- (r852-r853) CONTRIBUTING.md: Add a note about this maintaining this file changelog
- (r855) CONTRIBUTING.md: text cleanup
- (r859) CONTRIBUTING.md: remove dumb rule
- (r862) windows-bundler.sh: update jre
- (r864) add linux and macOS bundler script and launcher scripts
- (r865) fix macOS bundler script and launcher scripts
- (r866) bump electron version to v14.0.0
- (r868) add linux and macOS bundlers to the publish workflow
- (r871) publish.yml: remove node module cache, won't need it anymore
- (r873) publish.yml and build_push.yml: fix oopsies

### Tachidesk-WebUI
#### Visible changes
- (r767-r769) Support for hiding NSFW content in settings screen, extensions screen, sources screen

#### Bug fixes
- N/A

#### Internal changes
- (r767) Remove some duplicate dependency declaration from package.json

##### Non-code changes
- (r42-r45) Change README.md: some links and stuff
- (r45-r765) Add all of the commit history from when WebUI was separated from Server, jumping from r45 to r765 (r45 is exactly the same as r765)
- (r766) Steal .gitattributes from Tachidesk-Server
- (r767) Dependency cleanup in package.json

## [v0.4.8] + [WebUI: r41] - 2021-08-31

### Tachidesk-Server
#### Public API
##### Non-breaking changes
- Added support for serializing Search Filters
- SourceDataClass now has a isNsfw key

##### Breaking changes
- N/A

##### Bug fixes
- Fixed a bug where backup restore reversed chapter order
- Open Site feature now works properly ([Suwayomi/Suwayomi-WebUI#19](https://github.com/Suwayomi/Suwayomi-WebUI/issues/19))

#### Private API
- Added CloudflareInterceptor from TachiWeb-Server
- Restoring backup for mangas in library(merging manga data) is now supported

### Tachidesk-WebUI
#### Visible changes
- Better looking manga card titles
- Better reader title, next, prev buttons

#### Bug fixes
- Open Site feature now works properly ([Suwayomi/Suwayomi-WebUI#19](https://github.com/Suwayomi/Suwayomi-WebUI/issues/19))
- Re-ordering categories now works

#### Internal changes
- N/A

<!-- WEBUI LINKS -->

[WebUI: v20260509.01]: https://github.com/Suwayomi/Suwayomi-WebUI/blob/master/CHANGELOG.md#2026050901-r3147---2026-05-09
[WebUI: v20260508.01]: https://github.com/Suwayomi/Suwayomi-WebUI/blob/master/CHANGELOG.md#2026050801-r3136---2026-05-08
[WebUI: v20251230.01]: https://github.com/Suwayomi/Suwayomi-WebUI/blob/master/CHANGELOG.md#2025123001-r2937---2025-12-30
[WebUI: v20250801.01]: https://github.com/Suwayomi/Suwayomi-WebUI/blob/master/CHANGELOG.md#2025080101-r2717---2025-08-01
[WebUI: v20250731.01]: https://github.com/Suwayomi/Suwayomi-WebUI/blob/master/CHANGELOG.md#2025073101-r2715---2025-07-31
[WebUI: v20250703.01]: https://github.com/Suwayomi/Suwayomi-WebUI/blob/master/CHANGELOG.md#2025070301-r2643---2025-07-03
[WebUI: v1.5.1]: https://github.com/Suwayomi/Suwayomi-WebUI/blob/master/CHANGELOG.md#151-r2467---2025-04-07
[WebUI: v1.5.0]: https://github.com/Suwayomi/Suwayomi-WebUI/blob/master/CHANGELOG.md#150-r2461---2025-04-05
[WebUI: v1.1.0]: https://github.com/Suwayomi/Suwayomi-WebUI/blob/master/CHANGELOG.md#110-r1689---2024-06-14
[WebUI: v1.0.0]: https://github.com/Suwayomi/Suwayomi-WebUI/blob/master/CHANGELOG.md#100-r1411---2024-02-23
[WebUI: r983]: https://github.com/Suwayomi/Suwayomi-WebUI/compare/7312e92d07215c6a184577dd2779dcb7d887f889...ad9a12b2ec76fba0658d45d2d6c280caeaf046f7
[WebUI: r963]: https://github.com/Suwayomi/Suwayomi-WebUI/compare/14a9ffa55828a401e1f5ed4b7e6c574062c137bb...7312e92d07215c6a184577dd2779dcb7d887f889
[WebUI: r946]: https://github.com/Suwayomi/Suwayomi-WebUI/compare/13e6456190f3b344e51c1161b9e6088d2a74291a...14a9ffa55828a401e1f5ed4b7e6c574062c137bb
[WebUI: r942]: https://github.com/Suwayomi/Suwayomi-WebUI/compare/bdd03f6698d0f65c3b861612af0750d1e6aff8bb...13e6456190f3b344e51c1161b9e6088d2a74291a
[WebUI: r929]: https://github.com/Suwayomi/Suwayomi-WebUI/compare/aaaadebfb3f5e206543dc010dc2b90ae66c47286...bdd03f6698d0f65c3b861612af0750d1e6aff8bb
[WebUI: r911]: https://github.com/Suwayomi/Suwayomi-WebUI/compare/a3fe748c90f04e84169b8ddd008e8e2bc3281201...aaaadebfb3f5e206543dc010dc2b90ae66c47286
[WebUI: r893]: https://github.com/Suwayomi/Suwayomi-WebUI/compare/1ed001a8d8eb2af1126731bec01ebeb4ffb3707a...a3fe748c90f04e84169b8ddd008e8e2bc3281201
[WebUI: r820]: https://github.com/Suwayomi/Suwayomi-WebUI/compare/08980982e23a25feb365df6bba25645c16506c91...1ed001a8d8eb2af1126731bec01ebeb4ffb3707a
[WebUI: r809]: https://github.com/Suwayomi/Suwayomi-WebUI/compare/9a89346c01d2d2bcfc27acd34a538ee1c83ec320...08980982e23a25feb365df6bba25645c16506c91
[WebUI: r807]: https://github.com/Suwayomi/Suwayomi-WebUI/compare/03e7d4976a0334fcf9c99111202cd2066a83ce11...9a89346c01d2d2bcfc27acd34a538ee1c83ec320
[WebUI: r803]: https://github.com/Suwayomi/Suwayomi-WebUI/compare/3d3c2a29ca9955de64852a6dcf9740f5b280bde2...03e7d4976a0334fcf9c99111202cd2066a83ce11
[WebUI: r789]: https://github.com/Suwayomi/Suwayomi-WebUI/compare/ba26d852dc658aa899d0b8590d08197c77b1cde1...3d3c2a29ca9955de64852a6dcf9740f5b280bde2
[WebUI: r769]: https://github.com/Suwayomi/Suwayomi-WebUI/compare/f20c51c558d87b1124b49fd906865e5976c6a3b3...ba26d852dc658aa899d0b8590d08197c77b1cde1
[WebUI: r41]: https://github.com/Suwayomi/Suwayomi-WebUI/compare/960ffd222ecd4ae768aa86fd6c1b39c09d8f9469...f20c51c558d87b1124b49fd906865e5976c6a3b3

<!-- SERVER LINKS -->

[unreleased]: https://github.com/suwayomi/suwayomi-server/compare/v2.3.2226...HEAD
[v2.3.2226]: https://github.com/suwayomi/suwayomi-server/compare/v2.3.2223...v2.3.2226
[v2.3.2223]: https://github.com/suwayomi/suwayomi-server/compare/v2.2.2100...v2.3.2223
[v2.2.2100]: https://github.com/suwayomi/suwayomi-server/compare/v2.1.1867...v2.2.2100
[v2.1.1867]: https://github.com/suwayomi/suwayomi-server/compare/v2.0.1727...v2.1.1867
[v2.0.1727]: https://github.com/suwayomi/suwayomi-server/compare/v1.1.1...v2.0.1727
[v1.1.1]: https://github.com/suwayomi/suwayomi-server/compare/v1.1.0...v1.1.1
[v1.1.0]: https://github.com/suwayomi/suwayomi-server/compare/v1.0.0...v1.1.0
[v1.0.0]: https://github.com/suwayomi/suwayomi-server/compare/v0.7.0...v1.0.0
[v0.7.0]: https://github.com/suwayomi/suwayomi-server/compare/v0.6.6...v0.7.0
[v0.6.6]: https://github.com/suwayomi/suwayomi-server/compare/v0.6.5...v0.6.6
[v0.6.5]: https://github.com/suwayomi/suwayomi-server/compare/v0.6.4...v0.6.5
[v0.6.4]: https://github.com/suwayomi/suwayomi-server/compare/v0.6.3...v0.6.4
[v0.6.3]: https://github.com/suwayomi/suwayomi-server/compare/v0.6.2...v0.6.3
[v0.6.2]: https://github.com/suwayomi/suwayomi-server/compare/v0.6.1...v0.6.2
[v0.6.1]: https://github.com/suwayomi/suwayomi-server/compare/v0.6.0...v0.6.1
[v0.6.0]: https://github.com/suwayomi/suwayomi-server/compare/v0.5.4...v0.6.0
[v0.5.4]: https://github.com/suwayomi/suwayomi-server/compare/v0.5.4...v0.5.4
[v0.5.4]: https://github.com/suwayomi/suwayomi-server/compare/v0.5.3...v0.5.4
[v0.5.3]: https://github.com/suwayomi/suwayomi-server/compare/v0.5.2...v0.5.3
[v0.5.2]: https://github.com/suwayomi/suwayomi-server/compare/v0.5.1...v0.5.2
[v0.5.1]: https://github.com/suwayomi/suwayomi-server/compare/v0.5.0...v0.5.1
[v0.5.0]: https://github.com/suwayomi/suwayomi-server/compare/v0.4.9...v0.5.0
[v0.4.9]: https://github.com/suwayomi/suwayomi-server/compare/v0.4.8...v0.4.9
[v0.4.8]: https://github.com/suwayomi/suwayomi-server/releases/tag/v0.4.8
