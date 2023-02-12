# Server: v0.7.0 + WebUI: r983
## TL;DR
- CBZ downloads support
- Webview implementation based on Microsoft playwright, disabled for this release
- Fixed compatibility with some chinese extensions
- Support for Tachiyomi extensions lib 1.4
- WebUI changes:
    - Uhh, idk, find out yourself...

## Tachidesk-Server Changelog
- (r1159) v0.6.6 (by @AriaMoradi)
- (r1160) add Chagelog TL;DR (by @AriaMoradi)
- (r1161) fix Changelog typos (by @AriaMoradi)
- (r1162) WebView based cloudflare interceptor ([#456](https://github.com/Suwayomi/Tachidesk-Server/pull/456) by @AriaMoradi)
- (r1163) update issue mod (by @AriaMoradi)
- (r1164) better description (by @AriaMoradi)
- (r1165) fix regex (by @AriaMoradi)
- (r1166) get default User Agent from WebView ([#457](https://github.com/Suwayomi/Tachidesk-Server/pull/457) by @AriaMoradi)
- (r1167) implementation of android.graphics.BitmapFactory ([#460](https://github.com/Suwayomi/Tachidesk-Server/pull/460) by @animeavi)
- (r1168) Basic android.graphics Rect and Canvas implementation ([#461](https://github.com/Suwayomi/Tachidesk-Server/pull/461) by @animeavi)
- (r1169) Get Playwright working ([#462](https://github.com/Suwayomi/Tachidesk-Server/pull/462) by @Syer10)
- (r1170) disable deb release (by @AriaMoradi)
- (r1171) Fix debian release ([#463](https://github.com/Suwayomi/Tachidesk-Server/pull/463) by @mahor1221)
- (r1172) Add better manga thumbnail handling ([#465](https://github.com/Suwayomi/Tachidesk-Server/pull/465) by @Syer10)
- (r1173) Use extension list fallback if extensions fail to fetch ([#469](https://github.com/Suwayomi/Tachidesk-Server/pull/469) by @Syer10)
- (r1174) fix when playwright fails on providing a UA (by @AriaMoradi)
- (r1175) Update CategoryMetaTable.kt (by @AriaMoradi)
- (r1176) fix CategoryMetaTable reference to CategoryTable ([#473](https://github.com/Suwayomi/Tachidesk-Server/pull/473) by @AriaMoradi)
- (r1177) remove possibly misleading sentence (by @AriaMoradi)
- (r1178) Clarify and Update (by @AriaMoradi)
- (r1179) Clarify and Update (by @AriaMoradi)
- (r1180) link to Tachiyomi section (by @AriaMoradi)
- (r1181) fix typo (by @AriaMoradi)
- (r1182) Improve Gradle Configuration ([#478](https://github.com/Suwayomi/Tachidesk-Server/pull/478) by @Syer10)
- (r1183) Improve Playwright handling ([#479](https://github.com/Suwayomi/Tachidesk-Server/pull/479) by @Syer10)
- (r1184) fix ambiguous reference issue on JDK 13+ (by @AriaMoradi)
- (r1185) update gradle version (by @AriaMoradi)
- (r1186) upgrade dorkbox stuff (by @AriaMoradi)
- (r1187) Fixe Dex2Jar and dorkbox dependency issues ([#487](https://github.com/Suwayomi/Tachidesk-Server/pull/487) by @akabhirav)
- (r1188) Fix logging and update system try ([#488](https://github.com/Suwayomi/Tachidesk-Server/pull/488) by @Syer10)
- (r1189) add support for Extensions Lib 1.4 ([#496](https://github.com/Suwayomi/Tachidesk-Server/pull/496) by @Syer10)
- (r1190) disable playwright for v0.6.7 (by @AriaMoradi)
- (r1191) Decouple Cache and Download behaviour ([#493](https://github.com/Suwayomi/Tachidesk-Server/pull/493) by @akabhirav)
- (r1192) rethink image cache ([#498](https://github.com/Suwayomi/Tachidesk-Server/pull/498) by @AriaMoradi)
- (r1193) fix Page index issues for some providers ([#491](https://github.com/Suwayomi/Tachidesk-Server/pull/491) by @akabhirav)
- (r1194) Download as CBZ ([#490](https://github.com/Suwayomi/Tachidesk-Server/pull/490) by @akabhirav)
- (r1195) re-order config options (by @AriaMoradi)
- (r1196) stop using depricated API (by @AriaMoradi)

## Tachidesk-WebUI Changelog
- (r964) Created a GridLayout enum and updated all locations to use it. ([#208](https://github.com/Suwayomi/Tachidesk-WebUI/pull/208) by @infix)
- (r965) fix library update progress rendering ([#210](https://github.com/Suwayomi/Tachidesk-WebUI/pull/210) by @schroda)
- (r966) Save reader settings per manga in Meta ([#216](https://github.com/Suwayomi/Tachidesk-WebUI/pull/216) by @schroda)
- (r967) make default reader settings changeable ([#217](https://github.com/Suwayomi/Tachidesk-WebUI/pull/217) by @schroda)
- (r968) [#211] Refresh Library after a update ([#212](https://github.com/Suwayomi/Tachidesk-WebUI/pull/212) by @schroda)
- (r969) add logic for metadata migration ([#218](https://github.com/Suwayomi/Tachidesk-WebUI/pull/218) by @schroda)
- (r970) set browser tab title ([#220](https://github.com/Suwayomi/Tachidesk-WebUI/pull/220) by @schroda)
- (r971) Add tooltip containing full manga title to title of manga ([#221](https://github.com/Suwayomi/Tachidesk-WebUI/pull/221) by @schroda)
- (r972) show more detailed upload dates for today and yesterday ([#222](https://github.com/Suwayomi/Tachidesk-WebUI/pull/222) by @schroda)
- (r973) add GitHub action on pushing to run lint ([#224](https://github.com/Suwayomi/Tachidesk-WebUI/pull/224) by @schroda)
- (r974) Ignore filters while searching ([#226](https://github.com/Suwayomi/Tachidesk-WebUI/pull/226) by @schroda)
- (r975) force absolute import path ([#223](https://github.com/Suwayomi/Tachidesk-WebUI/pull/223) by @schroda)
- (r976) add prettier for auto formatting ([#231](https://github.com/Suwayomi/Tachidesk-WebUI/pull/231) by @schroda)
- (r977) Fix import path ([#228](https://github.com/Suwayomi/Tachidesk-WebUI/pull/228) by @schroda)
- (r978) increase prettier line length to 120 ([#233](https://github.com/Suwayomi/Tachidesk-WebUI/pull/233) by @schroda)
- (r979) Add chapter page dropdown ([#230](https://github.com/Suwayomi/Tachidesk-WebUI/pull/230) by @schroda)
- (r980) add chapter dropdown to reader nav bar  ([#229](https://github.com/Suwayomi/Tachidesk-WebUI/pull/229) by @schroda)
- (r981) Fix lint error ([#235](https://github.com/Suwayomi/Tachidesk-WebUI/pull/235) by @schroda)
- (r982) Fix reader nav bar scroll to page ([#236](https://github.com/Suwayomi/Tachidesk-WebUI/pull/236) by @schroda)
- (r964) Created a GridLayout enum and updated all locations to use it. ([#208](https://github.com/Suwayomi/Tachidesk-WebUI/pull/208) by @infix)



# Server: v0.6.6 + WebUI: r963
## TL;DR
- Batch actions for chapters
- Improved the downloader
- WebUI changes:
    - Support for chapter actions
    - a lot of code cleanup
    - some bugfixes

## Tachidesk-Server Changelog
- (r1114) fix broken links (by @AriaMoradi)
- (r1115) fix more broken stuff (by @AriaMoradi)
- (r1116) fix more broken stuff (by @AriaMoradi)
- (r1117) fix more broken stuff (by @AriaMoradi)
- (r1118) Update winget.yml ([#393](https://github.com/Suwayomi/Tachidesk-Server/pull/393) by @vedantmgoyal2009)
- (r1119) fix jre path([#396](https://github.com/Suwayomi/Tachidesk-Server/pull/396) by @vedantmgoyal2009)
- (r1120) Fix deb package ([#397](https://github.com/Suwayomi/Tachidesk-Server/pull/397) by @mahor1221)
- (r1121) bump version (by @AriaMoradi)
- (r1122) Update Changelog (by @AriaMoradi)
- (r1123) Add libc++-dev ([#405](https://github.com/Suwayomi/Tachidesk-Server/pull/405) by @mahor1221)
- (r1124) Revert back to correct way of handling jre_dir ([#408](https://github.com/Suwayomi/Tachidesk-Server/pull/408) by @mahor1221)
- (r1125) Update winget.yml ([#410](https://github.com/Suwayomi/Tachidesk-Server/pull/410) by @vedantmgoyal2009)
- (r1126) Remove support for Sorayomi web interface ([#414](https://github.com/Suwayomi/Tachidesk-Server/pull/414) by @marcoebbinghaus)
- (r1127) Fix downloader memory leak ([#418](https://github.com/Suwayomi/Tachidesk-Server/pull/418) by @Syer10)
- (r1128) Documentation cleanup ([#417](https://github.com/Suwayomi/Tachidesk-Server/pull/417) by @Syer10)
- (r1129) Updater cleanup and improvements ([#416](https://github.com/Suwayomi/Tachidesk-Server/pull/416) by @Syer10)
- (r1130) replace quickjs with Mozilla Rhino ([#415](https://github.com/Suwayomi/Tachidesk-Server/pull/415) by @xhzhe)
- (r1131) ktlint (by @AriaMoradi)
- (r1132) move Tachiyomi's BuildConfig to kotlin dir (by @AriaMoradi)
- (r1133) remove BuildConfig as extensions now use AppInfo (by @AriaMoradi)
- (r1134) include list of mangas missing source in restore report ([#421](https://github.com/Suwayomi/Tachidesk-Server/pull/421) by @AriaMoradi)
- (r1135) Update dependencies ([#422](https://github.com/Suwayomi/Tachidesk-Server/pull/422) by @Syer10)
- (r1136) Lint ([#423](https://github.com/Suwayomi/Tachidesk-Server/pull/423) by @Syer10)
- (r1137) Fix: Error handling for popular/latest api if pageNum was supplied as zero ([#424](https://github.com/Suwayomi/Tachidesk-Server/pull/424) by @meta-boy)
- (r1138) Add cache control header to manga page response ([#430](https://github.com/Suwayomi/Tachidesk-Server/pull/430) by @martinek)
- (r1139) add MangaTable.lastFetchedAt and ChapterTable.chaptersLastFetchedAt ([#431](https://github.com/Suwayomi/Tachidesk-Server/pull/431) by @martinek)
- (r1140) Pre-load meta entries for all chapters for optimization ([#432](https://github.com/Suwayomi/Tachidesk-Server/pull/432) by @martinek)
- (r1141) POST variant for `/{sourceId}/search` endpoint ([#434](https://github.com/Suwayomi/Tachidesk-Server/pull/434) by @martinek)
- (r1142) Add request body to documentation ([#435](https://github.com/Suwayomi/Tachidesk-Server/pull/435) by @Syer10)
- (r1143) add batch download api ([#436](https://github.com/Suwayomi/Tachidesk-Server/pull/436) by @martinek)
- (r1144) Migrate to H2 v2 (by @AriaMoradi)
- (r1145) add category and global meta ([#438](https://github.com/Suwayomi/Tachidesk-Server/pull/438) by @AriaMoradi)
- (r1146) Revert H2 database to v1 (by @AriaMoradi)
- (r1147) refactor deprecated api (by @AriaMoradi)
- (r1148) Downloader Rewrite ([#437](https://github.com/Suwayomi/Tachidesk-Server/pull/437) by @Syer10)
- (r1149) Set source preference doc fix ([#441](https://github.com/Suwayomi/Tachidesk-Server/pull/441) by @Syer10)
- (r1150) Add batch chapter update endpoint ([#442](https://github.com/Suwayomi/Tachidesk-Server/pull/442) by @martinek)
- (r1151) changes needed for tachiyomi tracker (by @AriaMoradi)
- (r1152) Future proofing (by @AriaMoradi)
- (r1153) Fix settings/check-update endpoint ([#445](https://github.com/Suwayomi/Tachidesk-Server/pull/445) by @martinek)
- (r1154) Fix docs for /server/check-updates ([#447](https://github.com/Suwayomi/Tachidesk-Server/pull/447) by @martinek)
- (r1155) Batch editing and deleting any chapter ([#449](https://github.com/Suwayomi/Tachidesk-Server/pull/449) by @martinek)
- (r1156) make chapters endpoint more unifrom (by @AriaMoradi)
- (r1157) Add batch endpoint for removing downloads from download queue ([#452](https://github.com/Suwayomi/Tachidesk-Server/pull/452) by @martinek)
- (r1158) Download queue missing update fix ([#450](https://github.com/Suwayomi/Tachidesk-Server/pull/450) by @martinek)

## Tachidesk-WebUI Changelog
- (r947) Feature/swr for library screens ([#186](https://github.com/Suwayomi/Tachidesk-WebUI/pull/186) by @martinek)
- (r948) Feature/swr for simple queries ([#187](https://github.com/Suwayomi/Tachidesk-WebUI/pull/187) by @martinek)
- (r949) Check download queue for changes and reload chapters if any chapter download changes state. ([#189](https://github.com/Suwayomi/Tachidesk-WebUI/pull/189) by @martinek)
- (r950) Update typescript dependency ([#190](https://github.com/Suwayomi/Tachidesk-WebUI/pull/190) by @martinek)
- (r951) update browserlist (by @AriaMoradi)
- (r952) Feature/batch chapter download ([#191](https://github.com/Suwayomi/Tachidesk-WebUI/pull/191) by @martinek)
- (r953) Memoize empty view face so it does not change on rerender ([#193](https://github.com/Suwayomi/Tachidesk-WebUI/pull/193) by @martinek)
- (r954) Feature/batch chapter actions ([#194](https://github.com/Suwayomi/Tachidesk-WebUI/pull/194) by @martinek)
- (r955) Fix navbar back button behavior ([#195](https://github.com/Suwayomi/Tachidesk-WebUI/pull/195) by @martinek)
- (r956) Options panels refactoring ([#196](https://github.com/Suwayomi/Tachidesk-WebUI/pull/196) by @martinek)
- (r957) Refactor and fix sorting in library ([#197](https://github.com/Suwayomi/Tachidesk-WebUI/pull/197) by @martinek)
- (r958) Scroll window to top when PagedPager changes page ([#198](https://github.com/Suwayomi/Tachidesk-WebUI/pull/198) by @martinek)
- (r959) Verticall scroll navigation and fix ([#200](https://github.com/Suwayomi/Tachidesk-WebUI/pull/200) by @martinek)
- (r960) Hide overflowing text in reader title if text can't be wrapped ([#199](https://github.com/Suwayomi/Tachidesk-WebUI/pull/199) by @martinek)
- (r961) Add safezone to scroll end detection to prevent edge cases when scrolling to the end would not detect end ([#201](https://github.com/Suwayomi/Tachidesk-WebUI/pull/201) by @martinek)
- (r962) Refactor/download queue and cleanup visuals overall ([#202](https://github.com/Suwayomi/Tachidesk-WebUI/pull/202) by @martinek)
- (r963) Fix "back" pagination on double page layout in reader for spread pages ([#203](https://github.com/Suwayomi/Tachidesk-WebUI/pull/203) by @martinek)



# Server: v0.6.5 + WebUI: r946
## TL;DR
- Fixed Windows bundler

## Tachidesk-Server Changelog
- (r1113) v0.6.4 (by @AriaMoradi)
- (r1114) fix broken links (by @AriaMoradi)
- (r1115) fix more broken stuff (by @AriaMoradi)
- (r1116) fix more broken stuff (by @AriaMoradi)
- (r1117) fix more broken stuff (by @AriaMoradi)
- (r1118) Update winget.yml ([#393](https://github.com/Suwayomi/Tachidesk-Server/pull/393) by @vedantmgoyal2009)
- (r1119) fix jre path([#396](https://github.com/Suwayomi/Tachidesk-Server/pull/396) by @voltrare)
- (r1120) Fix deb package ([#397](https://github.com/Suwayomi/Tachidesk-Server/pull/397) by @mahor1221)
- (r1121) bump version (by @AriaMoradi)

## Tachidesk-WebUI Changelog
- None



# Server: v0.6.4 + WebUI: r946
## TL;DR
- No new major features
- Bug fixes and changes for packaging
- Documentation changes

## Tachidesk-Server Changelog
- (r1087) v0.6.3 (by @AriaMoradi)
- (r1088) Save categories when manga is unfavorited ([#335](https://github.com/Suwayomi/Tachidesk-Server/pull/335) by @Syer10)
- (r1089) handle solid RAR archives ([#339](https://github.com/Suwayomi/Tachidesk-Server/pull/339)) cfso100@gmail.com
- (r1090) add support for changing downloads dir ([#343](https://github.com/Suwayomi/Tachidesk-Server/pull/343) by @AriaMoradi)
- (r1091) fix Applications dir dependency ([#344](https://github.com/Suwayomi/Tachidesk-Server/pull/344) by @AriaMoradi)
- (r1092) add support for alternative web interfaces ([#342](https://github.com/Suwayomi/Tachidesk-Server/pull/342) by @AriaMoradi)
- (r1093) Add displayValues json field for select filter ([#347](https://github.com/Suwayomi/Tachidesk-Server/pull/347) by @Syer10)
- (r1094) document manga endpoints ([#348](https://github.com/Suwayomi/Tachidesk-Server/pull/348) by @Syer10)
- (r1095) add ChapterCount to manga object in categoryMangas endpoint ([#349](https://github.com/Suwayomi/Tachidesk-Server/pull/349) by @abhijeetChawla)
- (r1096) document all endpoints ([#350](https://github.com/Suwayomi/Tachidesk-Server/pull/350) by @Syer10)
- (r1097) fix copymanga ([#354](https://github.com/Suwayomi/Tachidesk-Server/pull/354) by @AriaMoradi)
- (r1098) fix formatting by kotlinter (by @AriaMoradi)
- (r1099) bump WebUI (by @AriaMoradi)
- (r1100) fix WebUI release name (by @AriaMoradi)
- (r1101) Fix documentation errors ([#358](https://github.com/Suwayomi/Tachidesk-Server/pull/358) by @Syer10)
- (r1102) Docs improvements ([#359](https://github.com/Suwayomi/Tachidesk-Server/pull/359) by @Syer10)
- (r1103) Add linux-all.tar.gz & systemd service ([#366](https://github.com/Suwayomi/Tachidesk-Server/pull/366) by @mahor1221)
- (r1104) Publish to Windows Package Managar (WinGet) ([#369](https://github.com/Suwayomi/Tachidesk-Server/pull/369) by @vedantmgoyal2009)
- (r1105) Refactor scripts ([#370](https://github.com/Suwayomi/Tachidesk-Server/pull/370) by @mahor1221)
- (r1106) Run workflow jobs toghether ([#371](https://github.com/Suwayomi/Tachidesk-Server/pull/371) by @mahor1221)
- (r1107) Update gradle action ([#372](https://github.com/Suwayomi/Tachidesk-Server/pull/372) by @mahor1221)
- (r1108) Improve DocumentationDsl, bugfix default values and add queryParams ([#378](https://github.com/Suwayomi/Tachidesk-Server/pull/378) by @Syer10)
- (r1109) Tidy up bundler script ([#380](https://github.com/Suwayomi/Tachidesk-Server/pull/380) by @mahor1221)
- (r1110) Replace linux-all with linux-assets ([#381](https://github.com/Suwayomi/Tachidesk-Server/pull/381) by @mahor1221)
- (r1111) Rename every instance of Tachidesk jar to Tachdidesk-Server.jar ([#384](https://github.com/Suwayomi/Tachidesk-Server/pull/384) by @AriaMoradi)
- (r1112) Fix mistakes from #384 ([#385](https://github.com/Suwayomi/Tachidesk-Server/pull/385) by @AriaMoradi)

## Tachidesk-WebUI Changelog
- (r943) fix default width ([#171](https://github.com/Suwayomi/Tachidesk-WebUI/pull/171) by @Robonau)
- (r944) added an update checker button for library ([#172](https://github.com/Suwayomi/Tachidesk-WebUI/pull/172) by @infix)
- (r945) fix download queue delete button ([#176](https://github.com/Suwayomi/Tachidesk-WebUI/pull/176) by @Kreach37)
- (r946) fix mangadex filters ([#177](https://github.com/Suwayomi/Tachidesk-WebUI/pull/177) by @Robonau)



# Server: v0.6.3 + WebUI: r942
## TL;DR
- Changes in Server
    - Support for array search filter changes list
    - Support for Tachiyomi extensions lib 1.3
- Changes in WebUI
    - Better search filter support
    - Fluid manga grid
    - Library comfortable grid
    - Sources view layouts
    - Various other changes...

## Tachidesk-Server Changelog
- (r1074) v0.6.2 (by @AriaMoradi)
- (r1075) support array filter changes ([#304](https://github.com/Suwayomi/Tachidesk-Server/pull/304) by @AriaMoradi)
- (r1076) fix filterlist bugs ([#306](https://github.com/Suwayomi/Tachidesk-Server/pull/306) by @AriaMoradi)
- (r1077) Update README.md ([#305](https://github.com/Suwayomi/Tachidesk-Server/pull/305) by @mahor1221)
- (r1078) fix meta update changing all keys ([#314](https://github.com/Suwayomi/Tachidesk-Server/pull/314) by @AriaMoradi)
- (r1079) add support for tachiyomi extensions Lib 1.3 ([#316](https://github.com/Suwayomi/Tachidesk-Server/pull/316) by @AriaMoradi)
- (r1080) Fix sources list of one source throws an exception ([#308](https://github.com/Suwayomi/Tachidesk-Server/pull/308) by @Syer10)
- (r1081) Improve source handling, fix errors with uninitialized mangas in broken sources ([#319](https://github.com/Suwayomi/Tachidesk-Server/pull/319) by @Syer10)
- (r1082) Add thumbnail support for stub sources ([#320](https://github.com/Suwayomi/Tachidesk-Server/pull/320) by @Syer10)
- (r1083) update description for Tachidesk-Sorayomi ([#326](https://github.com/Suwayomi/Tachidesk-Server/pull/326) by @DattatreyaReddy)
- (r1084) Add last bit of code needed for Extensions Lib 1.3 ([#330](https://github.com/Suwayomi/Tachidesk-Server/pull/330) by @Syer10)
- (r1085) Add QuickJS, replaces Duktape for Extensions Lib 1.3 ([#331](https://github.com/Suwayomi/Tachidesk-Server/pull/331) by @Syer10)
- (r1086) fix auth not actually blocking requests ([#333](https://github.com/Suwayomi/Tachidesk-Server/pull/333) by @AriaMoradi)

## Tachidesk-WebUI Changelog
- (r930) Source filter scroll fix (array of filters on submit [#149](https://github.com/Suwayomi/Tachidesk-WebUI/pull/149) by @Robonau)
- (r931) fix manga badges setting menu that turns the update/download badges on and off ([#150](https://github.com/Suwayomi/Tachidesk-WebUI/pull/150) by @Robonau)
- (r932) move sorts to copy tachiyomi ([#151](https://github.com/Suwayomi/Tachidesk-WebUI/pull/151) by @Robonau)
- (r933) add comfortable grid option ([#152](https://github.com/Suwayomi/Tachidesk-WebUI/pull/152) by @Robonau)
- (r934) source layouts ([#153](https://github.com/Suwayomi/Tachidesk-WebUI/pull/153) by @Robonau)
- (r935) List layout ([#154](https://github.com/Suwayomi/Tachidesk-WebUI/pull/154) by @Robonau)
- (r936) in library badge to manga in sources ([#156](https://github.com/Suwayomi/Tachidesk-WebUI/pull/156) by @Robonau)
- (r937) mass search ([#157](https://github.com/Suwayomi/Tachidesk-WebUI/pull/157) by @Robonau)
- (r938) 18+ tag on source/extension cards ([#160](https://github.com/Suwayomi/Tachidesk-WebUI/pull/160) by @Robonau)
- (r939) fix search source click ([#164](https://github.com/Suwayomi/Tachidesk-WebUI/pull/164) by @Robonau)
- (r940) items per row setting ([#165](https://github.com/Suwayomi/Tachidesk-WebUI/pull/165) by @Robonau)
- (r941) fix the grid width thing ([#169](https://github.com/Suwayomi/Tachidesk-WebUI/pull/169) by @Robonau)
- (r942) unified library options ([#168](https://github.com/Suwayomi/Tachidesk-WebUI/pull/168) by @infix)

# Server: v0.6.2 + WebUI: r929
## TL;DR
- Changes in WebUI
    - Moved search to Browse
    - Support for Source Filters
    - Better visuals for Download Queue
    - A live version of WebUI is now available [at this link](https://tachidesk-webui-preview.github.io/).

## Tachidesk-Server Changelog
- (r1073) Refactor debian-packager.sh, rename launcher scripts ([#303](https://github.com/Suwayomi/Tachidesk-Server/pull/303) by @mahor1221)

## Tachidesk-WebUI Changelog
- (r912) show locale date, less confusing ([#131](https://github.com/Suwayomi/Tachidesk-WebUI/pull/131) by @AriaMoradi)
- (r913) fix links to work on a bare host ([#132](https://github.com/Suwayomi/Tachidesk-WebUI/pull/132) by @AriaMoradi)
- (r914) fix direct links ([#133](https://github.com/Suwayomi/Tachidesk-WebUI/pull/133) by @AriaMoradi)
- (r915) deploy to github pages (by @AriaMoradi)
- (r916) fix typo (by @AriaMoradi)
- (r917) better naming (by @AriaMoradi)
- (r918) update notice about github pages (by @AriaMoradi)
- (r919) move text (by @AriaMoradi)
- (r920) make all links work by catching 404 (by @AriaMoradi)
- (r921) fix scrolling 8px ([#135](https://github.com/Suwayomi/Tachidesk-WebUI/pull/135) by @Robonau)
- (r922) sorting ([#136](https://github.com/Suwayomi/Tachidesk-WebUI/pull/136) by @Robonau)
- (r923) Close button fix ([#141](https://github.com/Suwayomi/Tachidesk-WebUI/pull/141)) z14942744@gmail.com
- (r924) add NavBarContextProvider ([#128](https://github.com/Suwayomi/Tachidesk-WebUI/pull/128) by @abhijeetChawla)
- (r925) Resolved Merged Conflicts ([#127](https://github.com/Suwayomi/Tachidesk-WebUI/pull/127) by @abhijeetChawla)
- (r926) more Download Queue info ([#138](https://github.com/Suwayomi/Tachidesk-WebUI/pull/138) by @Robonau)
- (r927) Source filters, move search to SourceMangas ([#142](https://github.com/Suwayomi/Tachidesk-WebUI/pull/142) by @Robonau)
- (r928) Source genre sorts design ([#147](https://github.com/Suwayomi/Tachidesk-WebUI/pull/147) by @Robonau)
- (r929) Update LibraryOptions.tsx ([#146](https://github.com/Suwayomi/Tachidesk-WebUI/pull/146) by @Robonau)



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
