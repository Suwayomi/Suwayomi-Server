# Server: v0.5.4-next + WebUI: r855
## TL;DR
- N/A

## Tachidesk-Server Changelog
- (r992) update (by @AriaMoradi)
- (r993) update (by @AriaMoradi)
- (r994) update WebUI (by @AriaMoradi)
- (r995) bump version (by @AriaMoradi)
- (r996) cleanup (by @AriaMoradi)
- (r997) Update README.md ([#223](https://github.com/Suwayomi/Tachidesk-Server/pull/223)) mahorforuzesh@pm.me
- (r998) Update README.md (by @AriaMoradi)
- (r999) better cleaning algorithm (by @AriaMoradi)
- (r1000) Update CONTRIBUTING.md (by @AriaMoradi)
- (r1001) Update CONTRIBUTING.md (by @AriaMoradi)
- (r1002) Update CONTRIBUTING.md (by @AriaMoradi)
- (r1003) Update README.md (by @AriaMoradi)
- (r1004) Update README.md (by @AriaMoradi)
- (r1005) Update README.md (by @AriaMoradi)
- (r1006) Update README.md (by @AriaMoradi)
- (r1007) remove anime support (by @AriaMoradi)
- (r1008) update (by @AriaMoradi)
- (r1009) Fix tests ([#226](https://github.com/Suwayomi/Tachidesk-Server/pull/226) by @ntbm)
- (r1010) Expose unread and download count of Manga in category api ([#227](https://github.com/Suwayomi/Tachidesk-Server/pull/227) by @ntbm)
- (r1011) add Cache Header to Thumbnail Response for improved library performance ([#228](https://github.com/Suwayomi/Tachidesk-Server/pull/228) by @ntbm)
- (r1012) Update README.md (by @AriaMoradi)
- (r1013) Fix unread and download counts casing ([#230](https://github.com/Suwayomi/Tachidesk-Server/pull/230) by @Syer10)
- (r1014) Fix broken test ([#231](https://github.com/Suwayomi/Tachidesk-Server/pull/231) by @ntbm)
- (r1015) update (by @AriaMoradi)
- (r1016) Fix category reorder Endpoint. Added Test for Category Reorder ([#232](https://github.com/Suwayomi/Tachidesk-Server/pull/232) by @ntbm)
- (r1017) change windows bundle names (by @AriaMoradi)
- (r1018) improve tests (by @AriaMoradi)
- (r1019) allow injecting Sources (by @AriaMoradi)


## Tachidesk-WebUI 
- (r821) add Permanent sidebar for desktop widths([#46](https://github.com/Suwayomi/Tachidesk-WebUI/pull/46) by @abhijeetChawla)
- (r822) Fix Local Source being missing (by @AriaMoradi)
- (r823) fix the ugliness of bare messages (by @AriaMoradi)
- (r824) add pull request template (by @AriaMoradi)
- (r825) add Unread badges ([#48](https://github.com/Suwayomi/Tachidesk-WebUI/pull/48) by @ntbm)
- (r826) Back button implementation ([#47](https://github.com/Suwayomi/Tachidesk-WebUI/pull/47) by @abhijeetChawla)
- (r827) remove redundant '/manga' prefix from paths (by @AriaMoradi)
- (r828) refactor (by @AriaMoradi)
- (r829) put Sources and Extensions in the same screen (by @AriaMoradi)
- (r830) Set Fallback Image for broken Thumbnails ([#50](https://github.com/Suwayomi/Tachidesk-WebUI/pull/50) by @ntbm)
- (r831) Update README.md (by @AriaMoradi)
- (r832) Update README.md (by @AriaMoradi)
- (r833) Apply Api changes for unread badges ([#52](https://github.com/Suwayomi/Tachidesk-WebUI/pull/52) by @ntbm)
- (r834) add EmptyView to DownloadQueue, refactro strings ([#53](https://github.com/Suwayomi/Tachidesk-WebUI/pull/53) by @abhijeetChawla)
- (r835) Bottom navbar for mobile ([#51](https://github.com/Suwayomi/Tachidesk-WebUI/pull/51) by @abhijeetChawla)
- (r836) Implement Unread Filter for Library ([#54](https://github.com/Suwayomi/Tachidesk-WebUI/pull/54) by @ntbm)
- (r837) fix navbar broken logic (by @AriaMoradi)
- (r838) fix navbar (by @AriaMoradi)
- (r839) refactor (by @AriaMoradi)
- (r840) refactor (by @AriaMoradi)
- (r841) refactor (by @AriaMoradi)
- (r842) show different NavbarItems depending on device width (by @AriaMoradi)
- (r843) remove text decoration (by @AriaMoradi)
- (r844) fancy icon based on if path selected (by @AriaMoradi)
- (r845) custom Extension icon, google's version is shit (by @AriaMoradi)
- (r846) refactor (by @AriaMoradi)
- (r847) add CONTRIBUTING.md (by @AriaMoradi)
- (r848) move info (by @AriaMoradi)
- (r849) add Search to Library ([#55](https://github.com/Suwayomi/Tachidesk-WebUI/pull/55) by @ntbm)
- (r850) add aspect ratio to the manga card. ([#56](https://github.com/Suwayomi/Tachidesk-WebUI/pull/56) by @abhijeetChawla)
- (r851) better wording (by @AriaMoradi)
- (r852) reorder nav buttons (by @AriaMoradi)
- (r853) nicer gradient (by @AriaMoradi)
- (r854) refactor MangaCard (by @AriaMoradi)
- (r855) closes #58 (by @AriaMoradi



# Server: v0.5.4 + WebUI: r820
## TL;DR
- Fixed ReadComicOnline, Toonily and possibly other sources not working
- Backup and Restore now includes Updates tab data
- Removed Anime support from WebUI, Anime support will also be removed from Tachidesk-Server in a future update

## Tachidesk-Server Changelog
- (r973) convert android.jar lib to a maven repo
- (r978) mimic Tachiyomi's behaviour more closely, fixes ReadComicOnline (EN)
- (r980) fix export chapter ordering, include new props in backup
- (r982) remove isNsfw annotation detection
- (r984) use correct time conversion units when doing backups
- (r989) Support using a CatalogueSource instead of only HttpSources ([#219](https://github.com/Suwayomi/Tachidesk-Server/pull/219) by @Syer10)
- (r991) Use a custom task to run electron ([#220](https://github.com/Suwayomi/Tachidesk-Server/pull/220) by @Syer10)

## Tachidesk-WebUI Changelog
- (r810) fix wrong strings in set Server Address dialog, fixes [#39](https://github.com/Suwayomi/Tachidesk-WebUI/issues/39)
- (r811) fix chapterFetch loop
- (r812) fix overlapping requests
- (r813) fix typo
- (r814) Better portrait support ([#41](https://github.com/Suwayomi/Tachidesk-WebUI/issues/41) by @minhe7735)
- (r815) fixes Reader navbar colors when in light mode ([#43](https://github.com/Suwayomi/Tachidesk-WebUI/issues/43) by @abhijeetChawla)
- (r816) default languages cleanup, force Local source enabled
- (r817) force Local source at LangSelect
- (r818) rename ExtensionLangSelect: generic name for generic use
- (r819) don't show anime anymore
- (r820) Remove Anime support



# Server: v0.5.3 + WebUI: r809
## TL;DR
- added support for a equivalent page to Tachiyomi's Updates tab
- fix launchers not working on macOS M1/arm64

## Tachidesk-Server Changelog
- (r956) fix macOS-arm64 bundle launchers not working
- (r957) Workaround StdLib issue and add KtLint to all modules ([#206](https://github.com/Suwayomi/Tachidesk-Server/pull/206) by @Syer10)
- (r960-r963) Add recently updated chapters(Updates) endpoint


## Tachidesk-WebUI Changelog
- (r808) fix chapter list not calling onlineFetch=true
- (r809) add support for Updates



# Server: v0.5.2 + WebUI: r807
## TL;DR
- Fixed Local source not working on Windows
- Fixed Chapter numbers being shown incorrectly

## Tachidesk-Server
### Public API
#### Non-breaking changes
- N/A

#### Breaking changes
- N/A

#### Bug fixes
- (r948) Fix ManaToki (KO) and NewToki (KO) (issue [#202](https://github.com/Suwayomi/Tachidesk-Server/issue/202))
- (r949) Local source: fix windows paths

### Private API
- (r941) Update BytecodeEditor to use Java NIO Paths ([#200](https://github.com/Suwayomi/Tachidesk-Server/pull/200) by @Syer10)
- (r942) Gradle Updates ([#199](https://github.com/Suwayomi/Tachidesk-Server/pull/199) by @Syer10)


## Tachidesk-WebUI
#### Visible changes
- (r804) update text positioning on Reader and Player ([#35](https://github.com/Suwayomi/Tachidesk-WebUI/pull/35) by @voltrare)
- (r806) Source card for Local source is different
- (r807) add Local source guide

#### Bug fixes
- (r805) fix chapter name

#### Internal changes
- N/A



# Server: v0.5.1 + WebUI: r803
## TL;DR
- Loading sources' manga list is at least twice as fast
- Added support for Tachiyomi's Local source
- Added BasicAuth support, now you can protect your Tachidesk instance if you are running it on a public server
- Added ability to turn off cache for image requests

## Tachidesk-Server
### Public API
#### Non-breaking changes
- (r915) add BasicAuth support
- (r918) add ability to delete downloaded chapters
- (r923-r930) add Local Source
- (r938) add ability to turn off cache for image requests

#### Breaking changes
- N/A

#### Bug fixes
- (r917) detect if a downloaded chapter is missing

### Private API
- (r913) remove expand char limit on MangaTable columns
- (r914) migrate to Javalin 4
- (r921) depricate zero based chapters
- (r937) add ChapterRecognition from tachiyomi, closes #10



## Tachidesk-WebUI
#### Visible changes
- (r790) nice looking progress percentage
- (r791) show a Delete button for downloaded chapters
- (r792) Update hover effect using more of Material-UI color pallete ([#29](https://github.com/Suwayomi/Tachidesk-WebUI/pull/29) by @voltrare)
- (r793) Optimize images ([#32](https://github.com/Suwayomi/Tachidesk-WebUI/pull/32) by @phanirithvij)
- (r794) try fix #30 ([#31](https://github.com/Suwayomi/Tachidesk-WebUI/pull/31) by @phanirithvij)
- (r795) fix viewing page number when the string is long
- (r796) show proper display name for source
- (r797) fail gracefully when a thumbnail has errors
- (r798) fix when a source fails to load mangas
- (r800) add Local source ([#31](https://github.com/Suwayomi/Tachidesk-WebUI/pull/31))
- (r803) add support for useCache

#### Bug fixes
- N/A

#### Internal changes
- N/A



# Server: v0.5.0 + WebUI: r789
## TL;DR
- You can now install APK extensions from the extensions page
- WebUI now comes with an updated Material Design looks and is faster a little bit.
- WebUI now shows Nsfw content by default, disable it in settings if you prefer to not see Nsfw stuff
- Added support for configuration of sources, this enables MangaDex, Komga, Cubari and many other sources
- Chapters in the Manga page and Sources in the source page now look nicer and will glow with mouse hover

## Tachidesk-Server
### Public API
#### Non-breaking changes
- (r888) add installing APK from external sources endpoint

#### Breaking changes
- (r877 [#188](https://github.com/Suwayomi/Tachidesk-Server/pull/188) by @Syer10) `MangaDataClass.genre` changed type to `List<String>`

#### Bug fixes
- (r899-r901) fix when an external apk is installed and it doesn't have the default tachiyomi-extensions name
- (r905) fix a bug where if two sources return the same URL, a false duplicate might be detected

### Private API
- (r887) the `run` task won't call `downloadWebUI` now
- (r902) cleanup print/ln instances
- (r906) better handling of uninstalling Extensions

## Tachidesk-WebUI
#### Visible changes
- (r770) add support for the new genre type
- (r771) set the default value of `showNsfw` to `true` so we won't have visual artifacts with a clean install
- (r774 [#21](https://github.com/Suwayomi/Tachidesk-WebUI/pull/21) by @voltrare) `ReaderNavbar.jsx`: Swap close and retract Navbar buttons
- (r775 [#23](https://github.com/Suwayomi/Tachidesk-WebUI/pull/23) by @voltrare) `yarn.lock`: Fixes version inconsistency after commit 9b866811b
- (r776 [#23](https://github.com/Suwayomi/Tachidesk-WebUI/pull/23) by @voltrare) add margin between Source and Extension cards, make the Search button look nicer
- (r777) add support for installing external APK files
- (r778) fix the makeToaster?
- (r779) Action button for installing external extension
- (r780 Suwayomi/Tachidesk-WebUI#25) add on hover, active effect to Chapter/Episode card
- (r782-r785) updating material-ui to v5 changed the theme
- (r785-r788) better `SourceCard` looks on mobile, move `SourceDataClass.isConfigurable` gear button to `SourceMangas`
- (r789) implement source configuration

#### Bug fixes
- N/A

#### Internal changes
- (r782-r785) update dependencies, migrate material-ui from v4 to v5



# Server: v0.4.9 + WebUI: r769
## Tachidesk-Server
### Public API
#### Non-breaking changes
- N/A

#### Breaking changes
- (r857) renamed: `SourceDataClass.isNSFW` -> `SourceDataClass.isNsfw`

#### Bug fixes
- N/A

### Private API
- (r850) Bump WebUI version to r767
- (r861) Bump WebUI version to r769

#### Non-code changes
- (r851) Add this changelog file and `CHANGELOG-TEMPLATE.md`
- (r852-r853) `CONTRIBUTING.md`: Add a note about this maintaining this file changelog
- (r855) `CONTRIBUTING.md`: text cleanup
- (r859) `CONTRIBUTING.md`: remove dumb rule
- (r862) `windows-bundler.sh`: update jre
- (r864) add linux and macOS bundler script and launcher scripts
- (r865) fix macOS bundler script and launcher scripts
- (r866) bump electron version to v14.0.0
- (r868) add linux and macOS bundlers to the publish workflow
- (r871) `publish.yml`: remove node module cache, won't need it anymore
- (r873) `publish.yml` and `build_push.yml`: fix oopsies


## Tachidesk-WebUI
#### Visible changes
- (r767-r769) Support for hiding NSFW content in settings screen, extensions screen, sources screen

#### Bug fixes
- N/A

#### Internal changes
- (r767) Remove some duplicate dependency declaration from `package.json`

#### Non-code changes
- (r42-r45) Change `README.md`: some links and stuff 
- (r45-r765) Add all of the commit history from when WebUI was separated from Server, jumping from r45 to r765 (r45 is exactly the same as r765)
- (r766) Steal `.gitattributes` from Tachidesk-Server
- (r767) Dependency cleanup in `package.json`




# Server: v0.4.8 + WebUI: r41
## Tachidesk-Server
### Public API
#### Non-breaking changes
- Added support for serializing Search Filters
- `SourceDataClass` now has a `isNsfw` key

#### Breaking changes
- N/A

#### Bug fixes
- Fixed a bug where backup restore reversed chapter order
- Open Site feature now works properly (https://github.com/Suwayomi/Tachidesk-WebUI/issues/19)

### Private API
- Added `CloudflareInterceptor` from TachiWeb-Server
- Restoring backup for mangas in library(merging manga data) is now supported

## Tachidesk-WebUI
#### Visible changes
- Better looking manga card titles
- Better reader title, next, prev buttons

#### Bug fixes
- Open Site feature now works properly (https://github.com/Suwayomi/Tachidesk-WebUI/issues/19)
- Re-ordering categories now works

#### Internal changes
- N/A
