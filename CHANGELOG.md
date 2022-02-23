# Server: v0.6.1 + WebUI: r911
## TL;DR
- msi and deb packages thanks to @mahor1221
- [Tachidesk-Flutter](https://github.com/Suwayomi/Tachidesk-Flutter) exists now!

## Tachidesk-Server Changelog
- (r1047) update (by @AriaMoradi)
- (r1048) bump version (by @AriaMoradi)
- (r1049) Update README.md (by @AriaMoradi)
- (r1050) Update README.md (by @AriaMoradi)
- (r1051) refactor getChapter ([#268](https://github.com/Suwayomi/Tachidesk-Server/pull/268) by @AriaMoradi)
- (r1052) Improve documentation with Http codes ([#261](https://github.com/Suwayomi/Tachidesk-Server/pull/261) by @Syer10)
- (r1053) Add Route to stop and reset the updater ([#260](https://github.com/Suwayomi/Tachidesk-Server/pull/260) by @ntbm)
- (r1054) ignore non image files ([#269](https://github.com/Suwayomi/Tachidesk-Server/pull/269) by @AriaMoradi)
- (r1055) fix compile erorr (by @AriaMoradi)
- (r1056) update dex2jar (by @AriaMoradi)
- (r1057) Update Gradle and Dependencies ([#281](https://github.com/Suwayomi/Tachidesk-Server/pull/281) by @Syer10)
- (r1058) Handlers must return a result ([#282](https://github.com/Suwayomi/Tachidesk-Server/pull/282) by @Syer10)
- (r1059) Allow app compilation on Java 18+ ([#286](https://github.com/Suwayomi/Tachidesk-Server/pull/286) by @Syer10)
- (r1060) Automated MSI package building ([#277](https://github.com/Suwayomi/Tachidesk-Server/pull/277) by @mahor1221)
- (r1061) Automated debian package building ([#287](https://github.com/Suwayomi/Tachidesk-Server/pull/287) by @mahor1221)
- (r1062) fix Debian package errors ([#288](https://github.com/Suwayomi/Tachidesk-Server/pull/288) by @mahor1221)
- (r1063) Fix build_push.yml Hopefully ([#289](https://github.com/Suwayomi/Tachidesk-Server/pull/289) by @mahor1221)
- (r1064) Improve windows-bundler.sh ([#290](https://github.com/Suwayomi/Tachidesk-Server/pull/290) by @mahor1221)
- (r1065) add Tachidesk-Flutter to readme ([#292](https://github.com/Suwayomi/Tachidesk-Server/pull/292)) @DattatreyaReddy)
- (r1066) no online fetch on backup ([#293](https://github.com/Suwayomi/Tachidesk-Server/pull/293) by @AriaMoradi)
- (r1067) auto-remove duplicate chapters ([#294](https://github.com/Suwayomi/Tachidesk-Server/pull/294) by @AriaMoradi)
- (r1068) remove gson ([#295](https://github.com/Suwayomi/Tachidesk-Server/pull/295) by @AriaMoradi)

## Tachidesk-WebUI Changelog
- (r894) migrate ReaderNavbar to Mui 5 ([#84](https://github.com/Suwayomi/Tachidesk-WebUI/pull/84) by @AriaMoradi)
- (r895) migrate SpinnerImage to Mui 5 ([#97](https://github.com/Suwayomi/Tachidesk-WebUI/pull/97) by @AriaMoradi)
- (r896) migrate VerticalPager to Mui 5 ([#94](https://github.com/Suwayomi/Tachidesk-WebUI/pull/94) by @AriaMoradi)
- (r897) migrate PagedPager to Mui 5 ([#93](https://github.com/Suwayomi/Tachidesk-WebUI/pull/93) by @AriaMoradi)
- (r898) MangaCard imges don't stretch now ([#110](https://github.com/Suwayomi/Tachidesk-WebUI/pull/110) by @abhijeetChawla)
- (r899) show correct title ([#111](https://github.com/Suwayomi/Tachidesk-WebUI/pull/111) by @AriaMoradi)
- (r900) migrate DoublePage to Mui 5 ([#88](https://github.com/Suwayomi/Tachidesk-WebUI/pull/88) by @AriaMoradi)
- (r901) migrate DoublePagedPager to Mui 5 ([#91](https://github.com/Suwayomi/Tachidesk-WebUI/pull/91) by @AriaMoradi)
- (r902) migrate Reader to Mui 5 ([#100](https://github.com/Suwayomi/Tachidesk-WebUI/pull/100) by @AriaMoradi)
- (r903) migrate HorizantalPager to Mui 5 ([#92](https://github.com/Suwayomi/Tachidesk-WebUI/pull/92) by @AriaMoradi)
- (r904) migrate PageNumber to Mui 5 ([#90](https://github.com/Suwayomi/Tachidesk-WebUI/pull/90) by @AriaMoradi)
- (r905) Chapter filter is woking ([#114](https://github.com/Suwayomi/Tachidesk-WebUI/pull/114) by @abhijeetChawla)
- (r906) added extension search ([#115](https://github.com/Suwayomi/Tachidesk-WebUI/pull/115) by @abhijeetChawla)
- (r907) cleanup ([#117](https://github.com/Suwayomi/Tachidesk-WebUI/pull/117) by @AriaMoradi)
- (r908) handle search shortcuts ([#116](https://github.com/Suwayomi/Tachidesk-WebUI/pull/116) by @AriaMoradi)
- (r909) Refactor for Removing unnecesary UseEffect ([#118](https://github.com/Suwayomi/Tachidesk-WebUI/pull/118) by @abhijeetChawla)
- (r910) refactor ChapterList ([#125](https://github.com/Suwayomi/Tachidesk-WebUI/pull/125) by @abhijeetChawla)
- (r911) refactor ChapterOptions ([#126](https://github.com/Suwayomi/Tachidesk-WebUI/pull/126) by @abhijeetChawla)



# Server: v0.6.0 + WebUI: r893
## TL;DR
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

## Tachidesk-Server Changelog
- (r996) cleanup (by @AriaMoradi)
- (r999) better cleaning algorithm (by @AriaMoradi)
- (r1007) remove anime support (by @AriaMoradi)
- (r1009) Fix tests ([#226](https://github.com/Suwayomi/Tachidesk-Server/pull/226) by @ntbm)
- (r1010) Expose unread and download count of Manga in category api ([#227](https://github.com/Suwayomi/Tachidesk-Server/pull/227) by @ntbm)
- (r1011) add Cache Header to Thumbnail Response for improved library performance ([#228](https://github.com/Suwayomi/Tachidesk-Server/pull/228) by @ntbm)
- (r1013) Fix unread and download counts casing ([#230](https://github.com/Suwayomi/Tachidesk-Server/pull/230) by @Syer10)
- (r1014) Fix broken test ([#231](https://github.com/Suwayomi/Tachidesk-Server/pull/231) by @ntbm)
- (r1016) Fix category reorder Endpoint. Added Test for Category Reorder ([#232](https://github.com/Suwayomi/Tachidesk-Server/pull/232) by @ntbm)
- (r1017) change windows bundle names (by @AriaMoradi)
- (r1018) improve tests (by @AriaMoradi)
- (r1019) allow injecting Sources (by @AriaMoradi)
- (r1020) update (by @AriaMoradi)
- (r1021) fix credit (by @AriaMoradi)
- (r1022) cleanup (by @AriaMoradi)
- (r1023) refactor (by @AriaMoradi)
- (r1024) refactor (by @AriaMoradi)
- (r1025) implement Source Filters (by @AriaMoradi)
- (r1026) ignore build artifacts generated by teting (by @AriaMoradi)
- (r1027) convert request type (by @AriaMoradi)
- (r1028) Update CONTRIBUTING.md (by @AriaMoradi)
- (r1029) stop supporting zero based image storage ([#242](https://github.com/Suwayomi/src/pull/242) by @AriaMoradi)
- (r1030) add manga data to download queue object ([#244](https://github.com/Suwayomi/src/pull/244) by @AriaMoradi)
- (r1031) Fix Manga Meta, add Manga Meta test ([#245](https://github.com/Suwayomi/src/pull/245) by @Syer10)
- (r1032) add pagination to recentChapters ([#246](https://github.com/Suwayomi/src/pull/246) by @AriaMoradi)
- (r1033) update (by @AriaMoradi)
- (r1034) Implement Update of Library/Category ([#235](https://github.com/Suwayomi/src/pull/235) by @ntbm)
- (r1035) update (by @AriaMoradi)
- (r1036) Mention the existence of Mahor's Tachidesk-GTK (by @AriaMoradi)
- (r1037) Add a Kotlin DSL for endpoint documentation ([#249](https://github.com/Suwayomi/Tachidesk-Server/pull/249) by @Syer10)
- (r1038) update (by @AriaMoradi)
- (r1039) update (by @AriaMoradi)
- (r1040) cleanup directory names ([#251](https://github.com/Suwayomi/Tachidesk-Server/pull/251) by @AriaMoradi)
- (r1041) Fix first page not being detected correctly ([#253](https://github.com/Suwayomi/Tachidesk-Server/pull/253) by @AriaMoradi)
- (r1042) Update README.md (by @AriaMoradi)
- (r1043) Update README.md (by @AriaMoradi)
- (r1044) migrate application directories ([#255](https://github.com/Suwayomi/Tachidesk-Server/pull/255) by @AriaMoradi)
- (r1045) add support for MultiSelectListPreference ([#258](https://github.com/Suwayomi/Tachidesk-Server/pull/258) by @AriaMoradi)
- (r1046) empty searchTerm support ([#259](https://github.com/Suwayomi/Tachidesk-Server/pull/259) by @AriaMoradi)


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
- (r848) move info (by @AriaMoradi)
- (r849) add Search to Library ([#55](https://github.com/Suwayomi/Tachidesk-WebUI/pull/55) by @ntbm)
- (r850) add aspect ratio to the manga card. ([#56](https://github.com/Suwayomi/Tachidesk-WebUI/pull/56) by @abhijeetChawla)
- (r851) better wording (by @AriaMoradi)
- (r852) reorder nav buttons (by @AriaMoradi)
- (r853) nicer gradient (by @AriaMoradi)
- (r854) refactor MangaCard (by @AriaMoradi)
- (r855) closes #58 (by @AriaMoradi
- (r856) Add Resume Reading FAB Manga screen ([#59](https://github.com/Suwayomi/Tachidesk-WebUI/pull/59) by @abhijeetChawla)
- (r857) add filter and badge for `downloadCount` ([#62](https://github.com/Suwayomi/Tachidesk-WebUI/pull/62) by @abhijeetChawla)
- (r858) add issue template (by @AriaMoradi)
- (r859) Change color of navbar in light mode ([#65](https://github.com/Suwayomi/Tachidesk-WebUI/pull/65) by @abhijeetChawla)
- (r860) fix manga FAB margins ([#66](https://github.com/Suwayomi/Tachidesk-WebUI/pull/66) by @AriaMoradi)
- (r861) remove extra scrollbar on mobile ([#67](https://github.com/Suwayomi/Tachidesk-WebUI/pull/67) by @AriaMoradi)
- (r862) Fix Bad messages in Library Appbar search ([#70](https://github.com/Suwayomi/Tachidesk-WebUI/pull/70) by @ntbm)
- (r863) ban the style prop (by @AriaMoradi)
- (r864) Updates pagination update ([#68](https://github.com/Suwayomi/Tachidesk-WebUI/pull/68) by @AriaMoradi)
- (r865) make the whole chapter card into a button ([#73](https://github.com/Suwayomi/Tachidesk-WebUI/pull/73) by @AriaMoradi)
- (r866) fix chapter actions not working if manga is not fetched online ([#74](https://github.com/Suwayomi/Tachidesk-WebUI/pull/74) by @AriaMoradi)
- (r867) migrate some components to Mui5 new styling system ([#72](https://github.com/Suwayomi/Tachidesk-WebUI/pull/72) by @abhijeetChawla)
- (r868) load first page on read manga ([#76](https://github.com/Suwayomi/Tachidesk-WebUI/pull/76) by @AriaMoradi)
- (r869) Revert "migrate some components to Mui5 new styling system ([#72](https://github.com/Suwayomi/Tachidesk-WebUI/pull/72))" (by @AriaMoradi)
- (r870) migrate Backup to Mui 5 ([#106](https://github.com/Suwayomi/Tachidesk-WebUI/pull/106) by @AriaMoradi)
- (r871) migrate EmptyView to Mui 5 ([#95](https://github.com/Suwayomi/Tachidesk-WebUI/pull/95) by @AriaMoradi)
- (r872) migrate CategorySelect to Mui 5 ([#85](https://github.com/Suwayomi/Tachidesk-WebUI/pull/85) by @AriaMoradi)
- (r873) migrate LibraryOptions to Mui 5 ([#83](https://github.com/Suwayomi/Tachidesk-WebUI/pull/83) by @AriaMoradi)
- (r874) migrate ChapterCard.tsx to Mui 5 ([#80](https://github.com/Suwayomi/Tachidesk-WebUI/pull/80) by @AriaMoradi)
- (r875) migrate App.tsx to Mui 5 ([#79](https://github.com/Suwayomi/Tachidesk-WebUI/pull/79) by @AriaMoradi)
- (r876) migrate SourceConfigure to Mui 5 ([#103](https://github.com/Suwayomi/Tachidesk-WebUI/pull/103) by @AriaMoradi)
- (r877) migrate Settings to Mui 5 ([#102](https://github.com/Suwayomi/Tachidesk-WebUI/pull/102) by @AriaMoradi)
- (r878) migrate Updates to Mui 5 ([#104](https://github.com/Suwayomi/Tachidesk-WebUI/pull/104) by @AriaMoradi)
- (r879) Save tabs number in Url to persist tab when go to other paths ([#78](https://github.com/Suwayomi/Tachidesk-WebUI/pull/78) by @abhijeetChawla)
- (r880) migrate LangSelect to Mui 5 ([#86](https://github.com/Suwayomi/Tachidesk-WebUI/pull/86) by @AriaMoradi)
- (r881) migrate ExtensionCard.tsx to Mui 5 ([#81](https://github.com/Suwayomi/Tachidesk-WebUI/pull/81) by @AriaMoradi)
- (r882) migrate SingleSearch to Mui 5 ([#101](https://github.com/Suwayomi/Tachidesk-WebUI/pull/101) by @AriaMoradi)
- (r883) migrate LoadingPlaceholder to Mui 5 ([#96](https://github.com/Suwayomi/Tachidesk-WebUI/pull/96) by @AriaMoradi)
- (r884) migrate About to Mui 5 ([#105](https://github.com/Suwayomi/Tachidesk-WebUI/pull/105) by @AriaMoradi)
- (r885) migrate SourceCard to Mui 5 ([#82](https://github.com/Suwayomi/Tachidesk-WebUI/pull/82) by @AriaMoradi)
- (r886) migrate Manga to Mui 5 ([#99](https://github.com/Suwayomi/Tachidesk-WebUI/pull/99) by @AriaMoradi)
- (r887) migrate Browse to Mui 5 ([#98](https://github.com/Suwayomi/Tachidesk-WebUI/pull/98) by @AriaMoradi)
- (r888) migrate DesktopSideBar to Mui 5 ([#87](https://github.com/Suwayomi/Tachidesk-WebUI/pull/87) by @AriaMoradi)
- (r889) cleanup library  ([#107](https://github.com/Suwayomi/Tachidesk-WebUI/pull/107) by @AriaMoradi)
- (r890) support for new searchTerm (by @AriaMoradi)
- (r891) Revert "support for new searchTerm" (by @AriaMoradi)
- (r892) add support for emptySearch ([#109](https://github.com/Suwayomi/Tachidesk-WebUI/pull/109) by @AriaMoradi)
- (r893) add support for MultiSelectListPreference ([#108](https://github.com/Suwayomi/Tachidesk-WebUI/pull/108) by @AriaMoradi)



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
