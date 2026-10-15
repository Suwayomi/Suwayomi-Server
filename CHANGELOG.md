# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased] (Preview)

### Added
- (**API**) Add User Accounts
- (**Database/Settings/API**) Add setting `hikariMaxPoolSize` to be able to configure the maximum number of connections to the database
- (**Database/Settings/API**) Add setting `databaseSchema` to be able to configure the database schema name
- (**Database**) Add indexes on ChapterTable and ChapterUserTable for faster queries
- (**Database**) Add indexes on the manga `(source, url)` and chapter `(manga, url)` columns for faster lookups
- (**Settings/API**) Add setting `extensionStoresAllowPrivateHosts` to opt into extension store URLs pointing at private/loopback hosts (rejected by default)
- (**Settings/API**) Add settings `javaScriptEngineStatementLimit` and `javaScriptEngineTimeout` bounding how much work a script may do and how long it may run
- (**WebView/API**) Emit an `error` event on the WebView websocket when a URL is refused or a page fails to load
- (**Settings**) The `language` setting now also accepts Arabic (`ar`) and Catalan (`ca`)

### Changed
- (**GraphQL**) Count the nodes of list fields in SQL when `totalCount` is their only selection
- (**Updates**) Index chapters by fetch time for the updates list
- (**GraphQL**) Skip the count and first/last lookups of paginated lists when `totalCount` and `pageInfo.hasNextPage`/`hasPreviousPage` aren't selected
- (**GraphQL**) Find the chapters of the chapter-per-manga data loaders in SQL instead of in memory
- (**GraphQL**) Write data loader ids into the SQL instead of binding them
- (**GraphQL**) `StringFilter`: `includes*`/`startsWith*`/`endsWith*` (and their `not*`/`*Insensitive` variants) now match literally — `%`, `_` and `\` in the value are escaped instead of acting as LIKE wildcards; the `like*` fields still take raw LIKE patterns (with `\` as the escape char), and an explicitly empty `in`/`inInsensitive` list now matches nothing instead of being silently ignored
- (**Tracker**) `login`/`loginOAuth` run through a shared token-cleanup wrapper and `logout` now really deletes `track_token`; stored `"null"`/blank tokens are cleaned up once
- (**Tracker**) Re-binding an already-tracked entry moves the existing track record instead of leaving a duplicate row for the same remote id
- (**Tracker**) Invalid `scoreString`/`status` input is skipped with a warning instead of being pushed to the remote tracker
- (**Backup**) `syncYomiApiKey`, `syncYomiHost` and `downloadConversions` are no longer written into backups and are skipped on restore — they carry per-installation credentials and must be reconfigured after a restore
- (**Backup/API**) A backup export only embeds `serverSettings` when the requesting user holds `MANAGE_SETTINGS` (REST and GraphQL)
- (**Backup**) Automated backup file names include the user id (`auto.<userId>.<username>_…​.tachibk`) so backups of users sharing a username can be told apart
- (**Backup**) Exporting a backup compresses into gzip on the fly instead of keeping an extra uncompressed copy resident (lower peak memory on large libraries)
- (**Network**) `HttpException` now extends `IOException` instead of `IllegalStateException`, so `catch(IOException)` call sites also cover HTTP errors
- (**Manga/API**) `thumbnailUrl` is `null` when a manga has no stored thumbnail — REST no longer returns a thumbnail-proxy URL that only ever answers 404 (aligned with the GraphQL contract)
- (**Settings**) `webUISubpath` no longer accepts `.`/`..` path segments; `hikariMaxPoolSize` is capped at 200; `jwtTokenExpiry`/`jwtRefreshExpiry` must be at least 1s; `socksProxyPort`, `databaseSchema` and `extensionStores` entries are format-validated
- (**Settings**) `databaseSchema` must now be a valid unquoted SQL identifier and is fail-fast: an invalid value aborts startup with an error instead of silently resetting to `suwayomi` — the silent reset pointed the server at an empty schema, making existing data look lost. If startup reports your schema as invalid, rename it in the database first (e.g. `ALTER SCHEMA "my-schema" RENAME TO my_schema`) and update `server.databaseSchema`, or remove the entry to use the default
- (**Settings**) `kcefEnabled` is only honored on Linux and Windows; on other platforms it reports off instead of breaking server startup
- (**Settings**) Changing a `requiresRestart` setting through the API is skipped with a warning instead of silently not taking effect
- (**Source/API**) Filter updates are validated and applied per user — invalid positions/values answer 400 and one user's pending filter changes no longer leak into another user's searches
- (**Chapter**) `recentChapters` paginates in SQL instead of materializing the whole chapter list in memory, and `chapterCount` is no longer serialized (it ran one `COUNT(*)` per row)
- (**Chapter**) A source answering with zero chapters reports 404 instead of 500, and an empty chapter list no longer triggers a network re-fetch on every read
- (**GraphQL**) Subscriptions must send `connection_init` before starting operations and are closed with the graphql-ws codes 4401/4408/4429/4400; operations are isolated per websocket session
- (**GraphQL**) Queries are bounded: depth ≤ 15, complexity ≤ 500, at most 20 operations per batch; oversized data-loader batches are split transparently
- (**Auth**) Login hardening (POST rate limiting, post-login session rotation, CSRF token) was evaluated and intentionally rejected: Suwayomi targets self-hosted trusted networks and the session is a same-site JSESSIONID cookie, so the change is out of scope for this maintenance pass
- (**WebView**) Restricting `loadUrl` to public hosts was evaluated and intentionally rejected: the remote-controlled WebView is a trusted operator tool (e.g. Cloudflare bypass); the enforced boundary is the http/https scheme allowlist
- (**Extension**) Extension store URL validation resolves DNS at check time while the request resolves again at connect time — DNS-rebinding bypass remains a documented structural limitation
- (**Database**) Aligning the JDBC-backed `SQLiteDatabase` with AOSP's per-thread transaction/session model was evaluated and intentionally rejected — the shared-connection semantics are documented and locked in by tests; `enableWriteAheadLogging` likewise stays flag-only (documented divergence)
- (**Downloads**) Chapter/manga directory names keep their lossy mapping (`a:b`/`a?b` → `a_b`, 240-byte truncation, case folding) — collisions were evaluated and deliberately accepted for compatibility with existing download directories
- (**Build**) Switching modules to `java-library` API confinement and replacing the pinned winget publish action with a hand-rolled `gh api` submission were evaluated and intentionally rejected (documented in `build.gradle.kts`/`winget.yml`)
- (**Server**) The temp root is now per OS user (`<tmpdir>/Tachidesk-<user>` instead of the shared `<tmpdir>/Tachidesk`), so installs run by different OS users no longer share temp files — the old shared directory is left behind unused (safe to delete) and the warm webUI/thumbnail/manga caches are rebuilt once

### Fixed
- (**Tracker**) Fix the possibility of attempting to insert multiple track searches
- (**Auth**) Stop logging routine expired-token rejections as errors
- (**Thumbnails**) Refresh the thumbnail URL when its host is unreachable
- (**API**) Answer 424 instead of 500 when a source fails
- (**Local Source**) Fix detection of Synology metadata folders as local chapters
- (**Network/WebView**) Fix configured SOCKS proxy not being used by WebView and Cloudflare bypass
- (**Tracker**) Fix track search results being cached globally instead of per user — one user's private track fields (`status`/`score`/`private`/`lastChapterRead`) could leak into or be overwritten by another user's searches; existing shared cache rows are discarded by a migration
- (**SyncYomi**) Fix a failed or rejected sync upload still advancing the watermark, and serialization failures overwriting the remote with empty data
- (**Restore**) Fix a plain (non-sync) backup restore leaving the sticky `is_syncing` flag set on restored rows
- (**Updater**) Fix a failed update check cancelling the pending update jobs of unrelated sources, and cancelled update cycles leaving permanent PENDING queue entries with `isRunning` stuck true
- (**Library**) Fix `addMangaToLibrary` skipping the default categories when another user had already categorized the same manga
- (**Local Source**) Fix deadlocks and corrupt pages when reading RAR archives (single-stream unpacking with a serialized page loader)
- (**Downloads**) Fix archive handling leaking file descriptors, silently truncating oversized entries and accepting zip-slip paths; fix the thumbnail/image cache accepting path-traversing file names and racing concurrent writes of the same file
- (**KOReader**) Restore the real `partialMD5` document matching semantics (`-1..10` progress range) so progress sync resolves the right document again
- (**GraphQL**) Fix LEFT JOIN nullable sort columns dropping rows, `last`/`before` reverse pagination and `after`+`before` windows across the paginated list queries
- (**Settings**) Fix a single corrupt stored setting row breaking the whole `settings`/`userSettings` query — it now falls back to the default with a warning
- (**Settings**) Fix `server.conf` writes being non-atomic (a crash could truncate the file) and minimum-only value corrections (`min` without `max`) never being applied or persisted
- (**Settings**) Fix an unreadable `server.conf` value (e.g. a malformed duration such as `javaScriptEngineTimeout = "abc"`) crashing startup — the setting now falls back to its default once, with a warning, and the correction is persisted
- (**Settings**) Fix `kcefEnabled=false` being rejected as platform-unsupported on every start on non-Linux/Windows systems, repeating the warning and rewriting `server.conf` without converging
- (**Settings**) Fix atomic `server.conf` writes resetting tightened file permissions (e.g. `chmod 600`) to umask defaults — existing permissions are now preserved
- (**Settings**) Fix a per-user setting's first read racing a `reset` and re-caching a stale value, and fix stored booleans other than `true`/`false` silently decoding as `false` instead of falling back to the default
- (**Extension**) Fix unchecked manifest values: out-of-range `contentWarning` ints are clamped instead of silently degrading to SAFE, and oversized `versionName`s are truncated to the column width
- (**i18n**) Fix malformed `Accept-Language` headers and `%2$d`-style format strings in language packs causing errors
- (**Manga**) Fix a manga without chapters hitting the network on every chapter-list read — an empty list is only re-fetched when the manga was never fetched (explicit refreshes still work)

### Security
- (**API**) REST write endpoints now enforce the same per-permission checks as GraphQL (`INSTALL_EXTENSIONS`, `INSTALL_EXTERNAL_EXTENSIONS`, `UNINSTALL_EXTENSIONS`, `DOWNLOAD_CHAPTERS`, source preferences/filters, …) — previously any authenticated low-privilege user could invoke them
- (**Auth**) Fix empty credentials authenticating while `authUsername`/`authPassword` are blank (the default): an empty Basic header or login form no longer passes, and the Basic fallback no longer upgrades a visitor under `UI_LOGIN`/`SIMPLE_LOGIN`
- (**Auth**) Registration codes and `updateUser` can no longer grant permissions the issuer does not hold; redeeming a code re-validates the issuer's current permissions, and changing a user's permissions or role invalidates their existing JWTs
- (**Auth**) Fix the login `redirect` parameter accepting protocol-relative (`//`), backslash and NUL/control-character targets — the redirect target is now whitelist-validated
- (**WebView**) `loadUrl` only accepts `http`/`https` URLs and non-http(s) navigation inside CEF is cancelled at the resource layer — rendered frames can no longer expose `file:` URLs or browser internals
- (**Extension**) Extension APKs must carry a cryptographically valid signature that keeps matching the original signer; unsigned packages are refused, and zip-slip entries plus path-traversing `packageName`s are rejected before files are written
- (**Extension**) Extension store URLs — and every URL a store feeds back (`index_v2`, extension list, icon/apk) — are validated against an egress policy: http(s) only, no loopback/private/link-local/multicast/CGNAT/ULA hosts by default (`extensionStoresAllowPrivateHosts` opts in)
- (**Network**) Fix the persistent cookie store matching cookie domains by suffix without a dot boundary — `notexample.com` could steal `example.com` cookies (including `cf_clearance`)
- (**API**) Backup file downloads and temporary-file retrieval now require authentication, are scoped to the owning user and verify the stored path stays inside the temp directory
- (**GraphQL**) Schema introspection requires an authenticated user under `UI_LOGIN`, and execution errors no longer echo stack traces
- (**GraphQL**) Subscription websocket sessions are isolated per session and re-evaluation of the auth token is limited to `UI_LOGIN`, closing a visitor→admin upgrade path
- (**JS**) Extension JavaScript runs without host class lookup/polyglot access and under a statement limit plus watchdog timeout — scripts can no longer hang the server or escape the engine
- (**Database**) H2 migration tool jars are verified against pinned SHA-256 digests in addition to size/zip checks, so a poisoned download or cache entry cannot enter the classpath
- (**CI**) Fix the winget publish workflow interpolating release tag names into a privileged `run:` block; third-party actions are pinned by SHA

### Deprecated
- (**Downloads/Updates/API**) Remove `maxUpdates` `download` and `update` subscription input as it is not needed anymore due to subscriptions now supporting data loader batching

## [v2.4.2366] + [WebUI: v20260929.01] - 2026-09-29

#### Bug Squashing and Optimizations!
This release focuses on fixing many bugs and optimizing startup time! 

#### [SyncYomi](https://github.com/syncyomi/syncyomi) v2 Support!
Syncyomi has released its next version of syncing your library, optimizing the sync and squashing many bugs! 

### Added
- (**API**) Add platform information to `aboutServer` query
- (**Cache/API**) Add a way to clear cache & cookies
- (**Category/API**) Add `isDefaultCategory` in preparation of incompatible changes of future versions
- (**Downloads**) Try to preserve downloaded files during a chapter list update for chapters with title and/or scanlator change
- (**Downloads/API**) Add batch GQL mutation for reordering chapter downloads (`reorderChapterDownloads`)
- (**Logs**) Add IP location logging
- (**SyncYomi**) Support sync protocol v2: the server merges, only changes since the last upload are sent and category deletions are reported explicitly

### Changed
- (**Cloudflare/flaresolverr**) Send full `POST` json body to flaresolverr instead of empty string
- (**Downloads**) Skip LocalSource downloading
- (**GraphQL**) Updated GraphiQL GraphQL Playground
- (**SystemTray**) Disable DorkBox update requests
- (**Webview**) Don't throw an exception when disabling CEF

### Fixed
- (**Category**) Fix library/category counts not matching the actual number of manga due to duplicate category-manga rows
- (**Chapter**) Fix the fallback upload date of new chapters without one being stored in seconds instead of milliseconds (sorted as early 1970); existing chapters are repaired by a migration
- (**Cloudflare/flaresolverr**) Treat a bypass as successful when a `cf_clearance` cookie is returned, so non-CloudFlare source errors are correctly passed through to the extensions.
- (**Download/API**) Fix subscription returning stale chapter data in some cases
- (**Downloads/API**) Fix graphql download queue items position field of unmodified items after a reorder
- (**Downloads/API**) Fix sending updates for dequeued downloads
- (**Extension**) Fix extension update failure potentially causing a NullPointerException on the next update attempt
- (**Extension**) Fix losing installed extension in case the update fails
- (**Extension/Android**) Implement fromHtml with flags for author's note support
- (**Extension/Settings**) Fix `extensionRepos` setting not being migrated to `extensionStores`
- (**HTML**) Fix HTML being sent with gibberish when using non-latin characters
- (**Manga/Extension**) Fix resolving manga URLs for extensions that use memo data
- (**OPDS**) Fix OPDS search charset
- (**Source/API**) Fix graphql browse mutation (`fetchSourceManga`) with filters including nested group changes
- (**SyncYomi**) Adopt the server's record versions when restoring a sync response so a manga or chapter that lost a merge once can win later; a one-time full converging sync repairs skewed version counters
- (**SyncYomi**) Chapter reads no longer bump the manga version, which let the device that read more chapters silently revert the other device's category/tracking changes
- (**SyncYomi**) Fix local changes never winning a sync on PostgreSQL (version triggers were no-ops), chapter versions being reset on chapter refresh, category removals and tracking changes never bumping the manga version, and sync restores re-stamping every row as locally modified
- (**SyncYomi**) Keep TachiyomiSY's library sort, chapter sort/filter and reading mode flags when Suwayomi's copy wins a sync
- (**SyncYomi/Backup**) Fix restoring a library that originated from another client or was re-encoded by the sync server ("Field 'libraryId' is required ... but it was missing")
- (**SyncYomi/Category**) Fix category reorder not syncing after a sync adopted a peer's 0-based category orders (collision with the default category broke the WebUI reorder)
- (**SyncYomi/Category**) Use the 0-based Mihon/SY category order convention on the sync wire and store incoming orders as 1-based ranks, so category positions converge between Suwayomi and Tachiyomi forks
- (**Tracker**) Fix Shikimori
- (**WebView**) Fix `kcef` causing the server startup to fail
- (**WebView**) Fix authentication with subpath option
- (**WebView**) Fix that UI-WebView did not share user agent (background WebView unchanged)

## [v2.3.2243] - 2026-07-13

### Added
- (**Extension**) Add ability to install jar versions of extensions

### Fixed
- (**Extension**) Fix missing memo field in getPageList

## [v2.3.2238] - 2026-07-05

### Changed
- (**Extension**) Support fully qualified extension class names
- (**Manga/API**) Return partial responses for Manga And Chapters query

### Fixed
- (**Database/H2**) Use temporary file for H2 migration tools download

## [v2.3.2232] - 2026-07-03

### Fixed
- (**Extension**) Update APK url on installed Extensions

## [v2.3.2230] - 2026-07-01

### Fixed
- (**Extension/API**) Fix GQL handling of extensions without an extension store
- (**Build/Bundler**) Fix build continuing if errors happen
- (**Build/Bundler**) Remove jogamp from build script
- (**Build/Bundler**) Fix chmod attempts on Windows Java

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

## [v0.5.2] + [WebUI: r807] - 2021-09-19

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
[WebUI: v20260929.01]: https://github.com/Suwayomi/Suwayomi-WebUI/blob/master/CHANGELOG.md#2026092901-r3518---2026-09-29
[WebUI: v20260509.01]: https://github.com/Suwayomi/Suwayomi-WebUI/blob/master/CHANGELOG.md#2026050901-r3147---2026-05-09
[WebUI: v20260508.01]: https://github.com/Suwayomi/Suwayomi-WebUI/blob/master/CHANGELOG.md#2026050801-r3136---2026-05-08
[WebUI: v20250703.01]: https://github.com/Suwayomi/Suwayomi-WebUI/blob/master/CHANGELOG.md#2025070301-r2643---2025-07-03
[WebUI: v1.5.1]: https://github.com/Suwayomi/Suwayomi-WebUI/blob/master/CHANGELOG.md#151-r2467---2025-04-07
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

[unreleased]: https://github.com/suwayomi/suwayomi-server/compare/v2.4.2366...HEAD
[v2.4.2366]: https://github.com/suwayomi/suwayomi-server/compare/v2.3.2243...v2.4.2366
[v2.3.2243]: https://github.com/suwayomi/suwayomi-server/compare/v2.3.2238...v2.3.2243
[v2.3.2238]: https://github.com/suwayomi/suwayomi-server/compare/v2.3.2232...v2.3.2238
[v2.3.2232]: https://github.com/suwayomi/suwayomi-server/compare/v2.3.2230...v2.3.2232
[v2.3.2230]: https://github.com/suwayomi/suwayomi-server/compare/v2.3.2223...v2.3.2230
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
[v0.5.4]: https://github.com/suwayomi/suwayomi-server/compare/v0.5.3...v0.5.4
[v0.5.3]: https://github.com/suwayomi/suwayomi-server/compare/v0.5.2...v0.5.3
[v0.5.2]: https://github.com/suwayomi/suwayomi-server/compare/v0.5.1...v0.5.2
[v0.5.1]: https://github.com/suwayomi/suwayomi-server/compare/v0.5.0...v0.5.1
[v0.5.0]: https://github.com/suwayomi/suwayomi-server/compare/v0.4.9...v0.5.0
[v0.4.9]: https://github.com/suwayomi/suwayomi-server/compare/v0.4.8...v0.4.9
[v0.4.8]: https://github.com/suwayomi/suwayomi-server/releases/tag/v0.4.8
