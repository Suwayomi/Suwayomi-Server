# Server: v1.0.0 + WevUI: r1409
## TL;DR
- GraphQL API
- Rename to Suwayomi
- New Launcher for Suwayomi
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
- [FlareSolverr(Cloudflare Bypass)](https://github.com/FlareSolverr/FlareSolverr) support
- And many more fixes and features, this was a big release
- WebUI changes:
    - Uhh, idk, find out yourself...

## Suwayomi-Server Changelog
- ([r1494](https://github.com/Suwayomi/Suwayomi-Server/commit/1c417e909a3e10628e0febbe69a2dc1d8a1e98c0)) Support Comic Info creation on download ([#887](https://github.com/Suwayomi/Suwayomi-Server/pull/887) by @Syer10)
- ([r1493](https://github.com/Suwayomi/Suwayomi-Server/commit/fc53d69f82058208b43c53d3940f60c3ac4cac87)) Add auth and version support to socks proxy ([#883](https://github.com/Suwayomi/Suwayomi-Server/pull/883) by @AriaMoradi)
- ([r1492](https://github.com/Suwayomi/Suwayomi-Server/commit/dda86cdb930db172a53a63c326df94a8c9c8394f)) Seperate out migrations to allow run-once migrations ([#882](https://github.com/Suwayomi/Suwayomi-Server/pull/882) by @Syer10)
- ([r1491](https://github.com/Suwayomi/Suwayomi-Server/commit/525a974e3aa9e789c80e475d8684a258e5b44bcd)) Start Server after routes are defined ([#881](https://github.com/Suwayomi/Suwayomi-Server/pull/881) by @Syer10)
- ([r1490](https://github.com/Suwayomi/Suwayomi-Server/commit/b18c155e22fb77da9c74d75d0744c7cb7017fdd6)) Fix Downloader Memory Leak ([#880](https://github.com/Suwayomi/Suwayomi-Server/pull/880) by @Syer10)
- ([r1489](https://github.com/Suwayomi/Suwayomi-Server/commit/07e011092a25e31cbe170e65659d75c7a0befc8a)) Support Token Expiry properly ([#878](https://github.com/Suwayomi/Suwayomi-Server/pull/878) by @Syer10)
- ([r1488](https://github.com/Suwayomi/Suwayomi-Server/commit/6803ac0611b674b4edea1c1fc76627b324710ead)) move qtui to inactive list as it hasen't had commits in 2 years (by @AriaMoradi)
- ([r1487](https://github.com/Suwayomi/Suwayomi-Server/commit/af0dde5ae8b3021eb2c2b260f957afc1c66a709d)) Add Source Meta ([#875](https://github.com/Suwayomi/Suwayomi-Server/pull/875) by @Syer10)
- ([r1486](https://github.com/Suwayomi/Suwayomi-Server/commit/ea6edaecc4caa8e930df14078ed9abc1d90f8ece)) Fix local source being accidentally removed ([#874](https://github.com/Suwayomi/Suwayomi-Server/pull/874) by @Syer10)
- ([r1485](https://github.com/Suwayomi/Suwayomi-Server/commit/eb2054bd5e340cfe4d9647fe2735ec05073c4a31)) Add VUI as a webUI flavor ([#873](https://github.com/Suwayomi/Suwayomi-Server/pull/873) by @schroda)
- ([r1484](https://github.com/Suwayomi/Suwayomi-Server/commit/b277b3e3af502dd88e49234cbb9df61465abd9c9)) Add thumbnail fetch timestamp to the gql manga type ([#872](https://github.com/Suwayomi/Suwayomi-Server/pull/872) by @schroda)
- ([r1483](https://github.com/Suwayomi/Suwayomi-Server/commit/9dc3a4e6ee6d53ee522d525d2a34072f752c59f2)) Use correct name for scores data loader ([#870](https://github.com/Suwayomi/Suwayomi-Server/pull/870) by @schroda)
- ([r1482](https://github.com/Suwayomi/Suwayomi-Server/commit/6fbd2f10799206a4a316f4fda6012b33a330e738)) Feature/remove download ahead logic ([#867](https://github.com/Suwayomi/Suwayomi-Server/pull/867) by @schroda, @Syer10)
- ([r1481](https://github.com/Suwayomi/Suwayomi-Server/commit/9edbc7f1d7b810ae9ac223a90686208b56a9693f)) Feature/support different webui flavors ([#863](https://github.com/Suwayomi/Suwayomi-Server/pull/863) by @schroda)
- ([r1480](https://github.com/Suwayomi/Suwayomi-Server/commit/8aa75be0d321ca84044306589a813a18394fc008)) Cleanup gql subscription session state correctly ([#859](https://github.com/Suwayomi/Suwayomi-Server/pull/859) by @schroda)
- ([r1479](https://github.com/Suwayomi/Suwayomi-Server/commit/dc124fb15c543a0a59e51819b05f3bc1b2d89d58)) Make flaresolverr session options configurable ([#854](https://github.com/Suwayomi/Suwayomi-Server/pull/854) by @chancez)
- ([r1478](https://github.com/Suwayomi/Suwayomi-Server/commit/9109d1ca3e67695a6e3ed6d37a9ac322276a51c8)) Use a session with flaresolverr ([#853](https://github.com/Suwayomi/Suwayomi-Server/pull/853) by @chancez)
- ([r1477](https://github.com/Suwayomi/Suwayomi-Server/commit/02296f1d1c14cebda3d4d0c3f0f58897f32d8a42)) Change flaresolverr settings to be non optional ([#852](https://github.com/Suwayomi/Suwayomi-Server/pull/852) by @schroda)
- ([r1476](https://github.com/Suwayomi/Suwayomi-Server/commit/63e1082b97787e4406332e54b00a11fb2c74e381)) Minor fixes for FlareSolverr ([#851](https://github.com/Suwayomi/Suwayomi-Server/pull/851) by @Syer10)
- ([r1475](https://github.com/Suwayomi/Suwayomi-Server/commit/285f228660b897df373612a5edf9ccbaea5934d5)) Gracefully shutdown server in case webUI can't be setup ([#850](https://github.com/Suwayomi/Suwayomi-Server/pull/850) by @schroda)
- ([r1474](https://github.com/Suwayomi/Suwayomi-Server/commit/c18cf069b1ce6733c2ccdc4dd3f7c102b46b117b)) Prevent invalid webUI from stopping the server ([#849](https://github.com/Suwayomi/Suwayomi-Server/pull/849) by @schroda)
- ([r1473](https://github.com/Suwayomi/Suwayomi-Server/commit/fc64f4758913239100e86f23f2ecb5dbfb797b80)) Fix/excessive logging ([#848](https://github.com/Suwayomi/Suwayomi-Server/pull/848) by @schroda)
- ([r1472](https://github.com/Suwayomi/Suwayomi-Server/commit/562b940d9161660f30f0d1bf4f6594177f040f79)) Remove dot before cookie ([#845](https://github.com/Suwayomi/Suwayomi-Server/pull/845) by @Syer10)
- ([r1471](https://github.com/Suwayomi/Suwayomi-Server/commit/d658e07583f3aed2bac0afded7c2b302e8c6411b)) Implement FlareSolverr ([#844](https://github.com/Suwayomi/Suwayomi-Server/pull/844) by @Syer10)
- ([r1470](https://github.com/Suwayomi/Suwayomi-Server/commit/9121a6341c6f478df79391d0ae718fbe1a12c650)) Fix Tracker Status and Scores ([#843](https://github.com/Suwayomi/Suwayomi-Server/pull/843) by @Syer10)
- ([r1469](https://github.com/Suwayomi/Suwayomi-Server/commit/4bec027f113eac78b0686834f0648c2af364fd93)) Change Track.bind to use trackerId + remoteId ([#842](https://github.com/Suwayomi/Suwayomi-Server/pull/842) by @Syer10)
- ([r1468](https://github.com/Suwayomi/Suwayomi-Server/commit/b9053e3057d86510e0a3aff6ac3101e8dc703006)) Fix graphql tracking ([#840](https://github.com/Suwayomi/Suwayomi-Server/pull/840) by @Syer10)
- ([r1467](https://github.com/Suwayomi/Suwayomi-Server/commit/062113847800ac2b6f92172e2b20c24487b3543c)) Improve Tracker Icons Implementation ([#836](https://github.com/Suwayomi/Suwayomi-Server/pull/836) by @Syer10)
- ([r1466](https://github.com/Suwayomi/Suwayomi-Server/commit/ce42e89e25c520ac8ddf35972f04ffe8f9e168f5)) Add MangaUpdates ([#834](https://github.com/Suwayomi/Suwayomi-Server/pull/834) by @Syer10)
- ([r1465](https://github.com/Suwayomi/Suwayomi-Server/commit/46e1e4c043717fe9b299a79b00a8c692f9aa27f1)) Table for Track Searches ([#833](https://github.com/Suwayomi/Suwayomi-Server/pull/833) by @Syer10)
- ([r1464](https://github.com/Suwayomi/Suwayomi-Server/commit/621468a183305ec4febf831c512f78ed8f5876ec)) Apply natural sort to local manga pages in Directory format ([#826](https://github.com/Suwayomi/Suwayomi-Server/pull/826) by @Mercenar)
- ([r1463](https://github.com/Suwayomi/Suwayomi-Server/commit/d8876cf96a9fb5fb4c5ea67e2722a85a58009bf6)) Add mutex to "updateExtensionDatabase" ([#829](https://github.com/Suwayomi/Suwayomi-Server/pull/829) by @schroda)
- ([r1462](https://github.com/Suwayomi/Suwayomi-Server/commit/57d5bc6480dd0aa62b8a315785f1dec2b02257e5)) Add support for configuring which categories are downloaded automatically ([#832](https://github.com/Suwayomi/Suwayomi-Server/pull/832) by @chancez)
- ([r1461](https://github.com/Suwayomi/Suwayomi-Server/commit/f224918f339980cf97dc26d2490860eadff39555)) Create bin folder ([#822](https://github.com/Suwayomi/Suwayomi-Server/pull/822) by @Syer10)
- ([r1460](https://github.com/Suwayomi/Suwayomi-Server/commit/7b290dc465c7c85f09203bfed75f74d54f123e0b)) Update User Agent ([#821](https://github.com/Suwayomi/Suwayomi-Server/pull/821) by @Syer10)
- ([r1459](https://github.com/Suwayomi/Suwayomi-Server/commit/b1412dda34214eb28b6802eed9a59b2da03ded8c)) Update Java 8 ([#820](https://github.com/Suwayomi/Suwayomi-Server/pull/820) by @Syer10)
- ([r1458](https://github.com/Suwayomi/Suwayomi-Server/commit/28e4ac8dcb5b3b5f07390f6d27def9d6a3e8bb2e)) Remove Playwright ([#643](https://github.com/Suwayomi/Suwayomi-Server/pull/643) by @Syer10)
- ([r1457](https://github.com/Suwayomi/Suwayomi-Server/commit/79eeb6d703e264f537c81b2dc8f327b1ba6a2b8e)) [skip ci] add VUI to README.md ([#819](https://github.com/Suwayomi/Suwayomi-Server/pull/819) by @Robonau)
- ([r1456](https://github.com/Suwayomi/Suwayomi-Server/commit/0d0e735d0e7510b72ecdbd44a92db2949f15b17b)) Fix brotli ([#818](https://github.com/Suwayomi/Suwayomi-Server/pull/818) by @Syer10)
- ([r1455](https://github.com/Suwayomi/Suwayomi-Server/commit/d994502d06f3754297c72030f5feabe776f3da99)) Update Electron ([#817](https://github.com/Suwayomi/Suwayomi-Server/pull/817) by @Syer10)
- ([r1454](https://github.com/Suwayomi/Suwayomi-Server/commit/dfbd7a65aeafb5ec782388e84f43377f443833ef)) [skip ci] Correct wrong tracker oauth example url ([#814](https://github.com/Suwayomi/Suwayomi-Server/pull/814) by @schroda)
- ([r1453](https://github.com/Suwayomi/Suwayomi-Server/commit/f99f94c8d7e87e90969d518082a43c699be856f7)) Enable tracking ([#813](https://github.com/Suwayomi/Suwayomi-Server/pull/813) by @schroda)
- ([r1452](https://github.com/Suwayomi/Suwayomi-Server/commit/41c643496a5240c235b07263cd8bcdd63a99dd4b)) Add more chapter fields to MangaType ([#812](https://github.com/Suwayomi/Suwayomi-Server/pull/812) by @schroda)
- ([r1451](https://github.com/Suwayomi/Suwayomi-Server/commit/e5476f8a01dc831a7cefba208b9a596b55c41301)) Extension repo fixes and improvements ([#811](https://github.com/Suwayomi/Suwayomi-Server/pull/811) by @Syer10)
- ([r1450](https://github.com/Suwayomi/Suwayomi-Server/commit/188fb188cebe52462831503263ed38c8a6944609)) Set Mac Launcher Executable ([#810](https://github.com/Suwayomi/Suwayomi-Server/pull/810) by @Syer10)
- ([r1449](https://github.com/Suwayomi/Suwayomi-Server/commit/c852592b340a7d4615bae904430de52ae1c8e37b)) Prevent adding duplicated extensions to the db table ([#808](https://github.com/Suwayomi/Suwayomi-Server/pull/808) by @schroda)
- ([r1448](https://github.com/Suwayomi/Suwayomi-Server/commit/3a1e0c5a639fbe559e3407c331fbe7135cb3a59e)) Remove extension obsolete flag when updating db after extension list fetch ([#807](https://github.com/Suwayomi/Suwayomi-Server/pull/807) by @schroda)
- ([r1447](https://github.com/Suwayomi/Suwayomi-Server/commit/6376972130e6babb5b65e62634435ce07edbc4b8)) Remove caching of extensions for gql mutation ([#806](https://github.com/Suwayomi/Suwayomi-Server/pull/806) by @schroda)
- ([r1446](https://github.com/Suwayomi/Suwayomi-Server/commit/c70c860a82f8ccc681e5e13f5f7edc6e6f9c669c)) Create Client IDs ([#804](https://github.com/Suwayomi/Suwayomi-Server/pull/804) by @Syer10)
- ([r1445](https://github.com/Suwayomi/Suwayomi-Server/commit/5a178ada742c26e269736a2afc6744c444cde947)) add trackers support ([#720](https://github.com/Suwayomi/Suwayomi-Server/pull/720) by @tachimanga, @Syer10)
- ([r1444](https://github.com/Suwayomi/Suwayomi-Server/commit/230427e75851148b75ba9eca91019eb24242640b)) Support Custom Repos ([#803](https://github.com/Suwayomi/Suwayomi-Server/pull/803) by @Syer10)
- ([r1443](https://github.com/Suwayomi/Suwayomi-Server/commit/abf1af41a39152b2aa5b69a794f855be8bec8937)) Update bundled webui ([#802](https://github.com/Suwayomi/Suwayomi-Server/pull/802) by @Syer10)
- ([r1442](https://github.com/Suwayomi/Suwayomi-Server/commit/61e2548bb7842977b5dc20e7421723ad811ecbca)) Deb fixes ([#801](https://github.com/Suwayomi/Suwayomi-Server/pull/801) by @Syer10)
- ([r1441](https://github.com/Suwayomi/Suwayomi-Server/commit/f739c542928b04147885f72f0c95305464852653)) Rename the project ([#795](https://github.com/Suwayomi/Suwayomi-Server/pull/795) by @Syer10)
- ([r1440](https://github.com/Suwayomi/Suwayomi-Server/commit/3ed84de320c59b79d3068b9d9c577aa022a6212c)) [skip ci] Add API info ([#798](https://github.com/Suwayomi/Suwayomi-Server/pull/798) by @brianmakesthings)
- ([r1439](https://github.com/Suwayomi/Suwayomi-Server/commit/621b4c09467dba6874eb0b85dedaa19698d19324)) Correctly calculate the first chapter to download index ([#796](https://github.com/Suwayomi/Suwayomi-Server/pull/796) by @schroda)
- ([r1438](https://github.com/Suwayomi/Suwayomi-Server/commit/11be9691018b6c353194c12a7298f0414620e974)) Fix/download subscription returning outdated data for finished downloads ([#794](https://github.com/Suwayomi/Suwayomi-Server/pull/794) by @schroda)
- ([r1437](https://github.com/Suwayomi/Suwayomi-Server/commit/ea958cd8f7ccb147c5d21c5e8815ab32c5eea71d)) Correctly emit the current status immediately ([#792](https://github.com/Suwayomi/Suwayomi-Server/pull/792) by @schroda)
- ([r1436](https://github.com/Suwayomi/Suwayomi-Server/commit/56048dcdb05b99083e2ba7ff9fef8025edfa9bf6)) Update Github Actions ([#788](https://github.com/Suwayomi/Suwayomi-Server/pull/788) by @Syer10)
- ([r1435](https://github.com/Suwayomi/Suwayomi-Server/commit/fb545947ecbada33261bc84eac3b9b98ca0ef49a)) Feature/gql improve webui update status ([#783](https://github.com/Suwayomi/Suwayomi-Server/pull/783) by @schroda)
- ([r1434](https://github.com/Suwayomi/Suwayomi-Server/commit/df57070b7026fcf4cf6a219eee8653b8c28ea5be)) Make sure to always send finished chapter downloads with the download status ([#782](https://github.com/Suwayomi/Suwayomi-Server/pull/782) by @schroda)
- ([r1433](https://github.com/Suwayomi/Suwayomi-Server/commit/9b27d7ee23df1b8250e9fd0cde91056f9ed798a4)) Improve Http Client Configuration ([#786](https://github.com/Suwayomi/Suwayomi-Server/pull/786) by @Syer10)
- ([r1432](https://github.com/Suwayomi/Suwayomi-Server/commit/a2d3fa6e1d7a7918891c561a56044923f4ed87de)) Use new Tachiyomi backup filename format ([#787](https://github.com/Suwayomi/Suwayomi-Server/pull/787) by @Syer10)
- ([r1431](https://github.com/Suwayomi/Suwayomi-Server/commit/94b670eb8100693462e1d5c77f459bb764747059)) Fix/gql about webui query same response type as webui update info ([#781](https://github.com/Suwayomi/Suwayomi-Server/pull/781) by @schroda)
- ([r1430](https://github.com/Suwayomi/Suwayomi-Server/commit/d65ed6ced7732a68e54d67be02ef7b69e33271de)) Fix Bundler Script ([#780](https://github.com/Suwayomi/Suwayomi-Server/pull/780) by @Syer10)
- ([r1429](https://github.com/Suwayomi/Suwayomi-Server/commit/db50eb75265c81d076febec093a29c65b205a9d3)) Disable download ahead limit by default ([#778](https://github.com/Suwayomi/Suwayomi-Server/pull/778) by @schroda)
- ([r1428](https://github.com/Suwayomi/Suwayomi-Server/commit/d21b2018cb3ab3a28a4c29ffa3b845ddc5f15e5d)) Add mutation to clear the cached images ([#775](https://github.com/Suwayomi/Suwayomi-Server/pull/775) by @schroda)
- ([r1427](https://github.com/Suwayomi/Suwayomi-Server/commit/9110c07ed9b9ee51c10075b0affcc337e4b2a386)) Correctly select enum webui flavor via "ui name" ([#772](https://github.com/Suwayomi/Suwayomi-Server/pull/772) by @schroda)
- ([r1426](https://github.com/Suwayomi/Suwayomi-Server/commit/2298e7127959683c9d91d1ad4af7c5ca65bb04f7)) Feature/gql about webui query ([#773](https://github.com/Suwayomi/Suwayomi-Server/pull/773) by @schroda)
- ([r1425](https://github.com/Suwayomi/Suwayomi-Server/commit/909bd76e08988ad881bd2b31a3eee06c4bdd683d)) Cleanup parent folders when deleting downloaded chapters ([#776](https://github.com/Suwayomi/Suwayomi-Server/pull/776) by @schroda)
- ([r1424](https://github.com/Suwayomi/Suwayomi-Server/commit/50cd0c4e108256d88760a073e45f095ca32a0303)) Fix Queries Containing % ([#766](https://github.com/Suwayomi/Suwayomi-Server/pull/766) by @Syer10)
- ([r1423](https://github.com/Suwayomi/Suwayomi-Server/commit/460fc235e3e3a494e06ad753caff7def67834df2)) Add Cache-Control to Extension Icons ([#765](https://github.com/Suwayomi/Suwayomi-Server/pull/765) by @Syer10)
- ([r1422](https://github.com/Suwayomi/Suwayomi-Server/commit/c38a3d9eba983fd98f27291e0ac084d1711c00d3)) Update served webUI after update ([#764](https://github.com/Suwayomi/Suwayomi-Server/pull/764) by @schroda)
- ([r1421](https://github.com/Suwayomi/Suwayomi-Server/commit/b303291e94cc6cc5e0b868439568eb44d01f56b8)) Always get the latest commit count for jar name ([#763](https://github.com/Suwayomi/Suwayomi-Server/pull/763) by @schroda)
- ([r1420](https://github.com/Suwayomi/Suwayomi-Server/commit/7993da038e4d5723e33a182e68b72888b5ec4728)) Fix/initial auto backup never triggered in case server was not running ([#762](https://github.com/Suwayomi/Suwayomi-Server/pull/762) by @schroda)
- ([r1419](https://github.com/Suwayomi/Suwayomi-Server/commit/05bf4f552542053689ea491951c0afb1686e40b5)) Fix/auto download new chapters initial fetch ([#761](https://github.com/Suwayomi/Suwayomi-Server/pull/761) by @schroda)
- ([r1418](https://github.com/Suwayomi/Suwayomi-Server/commit/db36896f9253497b9a5ac009a35621e3b8da839a)) Fix chapter duplicates if its a different url but same chapter list size ([#759](https://github.com/Suwayomi/Suwayomi-Server/pull/759) by @Syer10)
- ([r1417](https://github.com/Suwayomi/Suwayomi-Server/commit/16dbad8bdf3768e911d82591e3b46949ac0d99c1)) Fix path to Preference file if it contains a invalid path character ([#750](https://github.com/Suwayomi/Suwayomi-Server/pull/750) by @Syer10)
- ([r1416](https://github.com/Suwayomi/Suwayomi-Server/commit/8a4c717d248f9265251e5083abeb5a0f4616c801)) Check for all downloaded pages during a chapter download ([#752](https://github.com/Suwayomi/Suwayomi-Server/pull/752) by @schroda)
- ([r1415](https://github.com/Suwayomi/Suwayomi-Server/commit/442a290966677bd45273d48b7b5694bbc3451e17)) Improve Extensions List ([#753](https://github.com/Suwayomi/Suwayomi-Server/pull/753) by @Syer10)
- ([r1414](https://github.com/Suwayomi/Suwayomi-Server/commit/0785f4d0f5e440301573f50cd7fc248fe571c1d2)) Chapter Fetch Improvements ([#754](https://github.com/Suwayomi/Suwayomi-Server/pull/754) by @Syer10)
- ([r1413](https://github.com/Suwayomi/Suwayomi-Server/commit/21e325af9c317eaf1f43b0300bd440550d83c330)) Correctly handle download of new chapters of not started entries ([#755](https://github.com/Suwayomi/Suwayomi-Server/pull/755) by @schroda)
- ([r1412](https://github.com/Suwayomi/Suwayomi-Server/commit/3e9d29ea7f1f99127244be02485ea00d95b8b4a3)) Remove username and password from config log ([#756](https://github.com/Suwayomi/Suwayomi-Server/pull/756) by @schroda)
- ([r1411](https://github.com/Suwayomi/Suwayomi-Server/commit/4324373e6173223746c27b8ef8670c12cf485916)) Fix/chapter list fetch updating and inserting chapters into database ([#749](https://github.com/Suwayomi/Suwayomi-Server/pull/749) by @schroda)
- ([r1410](https://github.com/Suwayomi/Suwayomi-Server/commit/673053d29151c473f64fd0626a3dbe72ed5a94e5)) Migrate preferences only if necessary ([#748](https://github.com/Suwayomi/Suwayomi-Server/pull/748) by @schroda)
- ([r1409](https://github.com/Suwayomi/Suwayomi-Server/commit/5b3975f8861f3e130ee1eeb7c227fab8dc0cbd93)) Only batch update in case list is not empty ([#747](https://github.com/Suwayomi/Suwayomi-Server/pull/747) by @schroda)
- ([r1408](https://github.com/Suwayomi/Suwayomi-Server/commit/7ed8f4385957fe8aea52995a4fdde60b3ae95c90)) Fix/backup import failure not resetting status ([#746](https://github.com/Suwayomi/Suwayomi-Server/pull/746) by @schroda)
- ([r1407](https://github.com/Suwayomi/Suwayomi-Server/commit/dcbb1c0dd1130386f4d37b634aa175e749904cc7)) Handle backups with categories having default category name ([#745](https://github.com/Suwayomi/Suwayomi-Server/pull/745) by @schroda)
- ([r1406](https://github.com/Suwayomi/Suwayomi-Server/commit/5d4d417f3e8113a31216e779aacbbb549d867ddc)) Extract downloaded webUI zip in temp folder for validation ([#744](https://github.com/Suwayomi/Suwayomi-Server/pull/744) by @schroda)
- ([r1405](https://github.com/Suwayomi/Suwayomi-Server/commit/5943c6a2c63ca897c8c8d832d695b8b6a0f0da38)) Feature/improve browsing source performance ([#743](https://github.com/Suwayomi/Suwayomi-Server/pull/743) by @schroda)
- ([r1404](https://github.com/Suwayomi/Suwayomi-Server/commit/6d33d726630c0ef69a0b85006b9e9a394c119026)) #733: Improve perfs on getChapterList with onlineFetch (Less databases calls) ([#737](https://github.com/Suwayomi/Suwayomi-Server/pull/737) by @alexandrejournet)
- ([r1403](https://github.com/Suwayomi/Suwayomi-Server/commit/9d2b098837cab10b3ff28bc02b3f00d30064656c)) Fix/updater update stuck in running status after failure ([#731](https://github.com/Suwayomi/Suwayomi-Server/pull/731) by @schroda)
- ([r1402](https://github.com/Suwayomi/Suwayomi-Server/commit/17bc2d23318b6b92da23feaaa2b50b7badd9b828)) Fetch mangas during the update ([#729](https://github.com/Suwayomi/Suwayomi-Server/pull/729) by @schroda)
- ([r1401](https://github.com/Suwayomi/Suwayomi-Server/commit/1c192b8db6890c3cc1c9aa0a598f9f6edd4c6677)) Fix Updater ([#742](https://github.com/Suwayomi/Suwayomi-Server/pull/742) by @Syer10)
- ([r1400](https://github.com/Suwayomi/Suwayomi-Server/commit/76595233fc028a24156a3421baa67c85c90aa486)) Prevent mangas from being added to the default category ([#741](https://github.com/Suwayomi/Suwayomi-Server/pull/741) by @schroda)
- ([r1399](https://github.com/Suwayomi/Suwayomi-Server/commit/6531b80998a1166c7fd821d894823a739617d68a)) Delete outdated thumbnails when inserting mangas into database ([#739](https://github.com/Suwayomi/Suwayomi-Server/pull/739) by @schroda)
- ([r1398](https://github.com/Suwayomi/Suwayomi-Server/commit/05707e29d7fe102025476211ad6af9842fae0842)) Add missing settings to gql ([#738](https://github.com/Suwayomi/Suwayomi-Server/pull/738) by @schroda)
- ([r1397](https://github.com/Suwayomi/Suwayomi-Server/commit/616ed4637d69dd1f329827a1df790240e62e0fff)) Handle disabled download ahead limit for new chapters auto download ([#734](https://github.com/Suwayomi/Suwayomi-Server/pull/734) by @schroda)
- ([r1396](https://github.com/Suwayomi/Suwayomi-Server/commit/912c340a01a05adb41b88ea8907b4085b290ca96)) Fix update subscription returning stale data ([#727](https://github.com/Suwayomi/Suwayomi-Server/pull/727) by @schroda)
- ([r1395](https://github.com/Suwayomi/Suwayomi-Server/commit/583a2f0fad7e842b11ac738b7165b3e3f892a0f0)) Migrate to XML Settings from Preferences ([#722](https://github.com/Suwayomi/Suwayomi-Server/pull/722) by @Syer10)
- ([r1394](https://github.com/Suwayomi/Suwayomi-Server/commit/60015bc041c6bdac4b214c3b23d297e29c7b174a)) Return source for preference mutation ([#728](https://github.com/Suwayomi/Suwayomi-Server/pull/728) by @schroda)
- ([r1393](https://github.com/Suwayomi/Suwayomi-Server/commit/029f445d0a7b5a58de8c5c88b4aec71106fe9821)) Revert Dex2Jar ([#721](https://github.com/Suwayomi/Suwayomi-Server/pull/721) by @Syer10)
- ([r1392](https://github.com/Suwayomi/Suwayomi-Server/commit/150416b578173c0e046c403a9ca8fb1f4e3f797b)) Fix/default log level ([#719](https://github.com/Suwayomi/Suwayomi-Server/pull/719) by @schroda)
- ([r1391](https://github.com/Suwayomi/Suwayomi-Server/commit/6684576de1c872882250bfc0bddd32247e0702cd)) Fix more missing functions ([#718](https://github.com/Suwayomi/Suwayomi-Server/pull/718) by @schroda)
- ([r1390](https://github.com/Suwayomi/Suwayomi-Server/commit/289acc9296ffa0189e35a525a3f7be52205ed912)) Fix Kavita ([#716](https://github.com/Suwayomi/Suwayomi-Server/pull/716) by @Syer10)
- ([r1389](https://github.com/Suwayomi/Suwayomi-Server/commit/2cf9a407e80863fc12a4202490b19142264b7c2f)) Fix MangaDex and Other Sources ([#715](https://github.com/Suwayomi/Suwayomi-Server/pull/715) by @Syer10)
- ([r1388](https://github.com/Suwayomi/Suwayomi-Server/commit/682c36464716e42b8f3e54c2cd008776132cc03f)) Address Build Warnings and Cleanup ([#707](https://github.com/Suwayomi/Suwayomi-Server/pull/707) by @Syer10)
- ([r1387](https://github.com/Suwayomi/Suwayomi-Server/commit/e70730e9a8d141557af115481617c286578e3933)) Query for mangas in specific categories ([#712](https://github.com/Suwayomi/Suwayomi-Server/pull/712) by @schroda)
- ([r1386](https://github.com/Suwayomi/Suwayomi-Server/commit/0ba6c88d69fa8a80cfb077baf56ef7bcf5d6c944)) Fix/graphql mangas query genre based filtering ([#713](https://github.com/Suwayomi/Suwayomi-Server/pull/713) by @schroda)
- ([r1385](https://github.com/Suwayomi/Suwayomi-Server/commit/c56bdea1e2ccbdbaf8ccf40694a377a426a1e2dd)) Do not log ping messages ([#709](https://github.com/Suwayomi/Suwayomi-Server/pull/709) by @schroda)
- ([r1384](https://github.com/Suwayomi/Suwayomi-Server/commit/a449a01a24db2a3160bddeb6edc051c4f6e2a615)) Fix/web interface manager get latest compatible version ([#706](https://github.com/Suwayomi/Suwayomi-Server/pull/706) by @schroda)
- ([r1383](https://github.com/Suwayomi/Suwayomi-Server/commit/849acfca3d44c3d035e232dd4ccc55818c789198)) Switch to a new Ktlint Formatter ([#705](https://github.com/Suwayomi/Suwayomi-Server/pull/705) by @Syer10)
- ([r1382](https://github.com/Suwayomi/Suwayomi-Server/commit/3cd3cb01861f609b323910713b9e686d2a27a4f4)) Fix/graphql subscriptions logging ([#704](https://github.com/Suwayomi/Suwayomi-Server/pull/704) by @schroda)
- ([r1381](https://github.com/Suwayomi/Suwayomi-Server/commit/feead100f2ef191ea9f44d4f22a5e99473132487)) Update dependencies ([#701](https://github.com/Suwayomi/Suwayomi-Server/pull/701) by @Syer10)
- ([r1380](https://github.com/Suwayomi/Suwayomi-Server/commit/a9987e6ab0322b2956f74fb04de980a1293a80d2)) Support more image types ([#700](https://github.com/Suwayomi/Suwayomi-Server/pull/700) by @Syer10)
- ([r1379](https://github.com/Suwayomi/Suwayomi-Server/commit/ef0a6f54b845779e6d4650b10722424fa6953cef)) Feature/auto download ahead ([#681](https://github.com/Suwayomi/Suwayomi-Server/pull/681) by @schroda)
- ([r1378](https://github.com/Suwayomi/Suwayomi-Server/commit/c8865ad185a7b76cb20793d5f8db395657cd7dde)) Implement Non-Final 1.5 Extensions API ([#699](https://github.com/Suwayomi/Suwayomi-Server/pull/699) by @Syer10)
- ([r1377](https://github.com/Suwayomi/Suwayomi-Server/commit/354968fba764cc845568ea4b2521dc1ec79beb18)) Update version "name" and "code" when installing external extension ([#698](https://github.com/Suwayomi/Suwayomi-Server/pull/698) by @schroda)
- ([r1376](https://github.com/Suwayomi/Suwayomi-Server/commit/f985ed2131889480b3fa06caab010482ff75cc5f)) Order chapters to download by manga and source order ([#697](https://github.com/Suwayomi/Suwayomi-Server/pull/697) by @schroda)
- ([r1375](https://github.com/Suwayomi/Suwayomi-Server/commit/be2628875f2e966156b5073733ced1806c832bdc)) Correctly select results using cursors while sorting ([#696](https://github.com/Suwayomi/Suwayomi-Server/pull/696) by @schroda)
- ([r1374](https://github.com/Suwayomi/Suwayomi-Server/commit/9430c8c580d1e06a596609e14af69754f327684a)) [skip ci] Added new Tachidesk-VaadinUI Client ([#695](https://github.com/Suwayomi/Suwayomi-Server/pull/695) by @aless2003)
- ([r1373](https://github.com/Suwayomi/Suwayomi-Server/commit/ea2cf5d4ff3b05068d09a415a10052b1ef7e20b8)) Fix File Upload ([#694](https://github.com/Suwayomi/Suwayomi-Server/pull/694) by @Syer10)
- ([r1372](https://github.com/Suwayomi/Suwayomi-Server/commit/3b36974d84760a72e669a8690f539c8c35b133e5)) Fixed Bitmap missing method when using Baozi Manhua extensions. ([#687](https://github.com/Suwayomi/Suwayomi-Server/pull/687) by @vuhe)
- ([r1371](https://github.com/Suwayomi/Suwayomi-Server/commit/41fea1d2a01f8ad6fe6076d06dcd0b839b7091a4)) remove @Synchronized in CloudflareInterceptor.kt for performance ([#688](https://github.com/Suwayomi/Suwayomi-Server/pull/688) by @MangaCrushTeam)
- ([r1370](https://github.com/Suwayomi/Suwayomi-Server/commit/d81fafc9f636175069785555e20ed4bcb8468a29)) Correctly detect initial fetch of chapters ([#689](https://github.com/Suwayomi/Suwayomi-Server/pull/689) by @schroda)
- ([r1369](https://github.com/Suwayomi/Suwayomi-Server/commit/0a73177996567ecffd8d2a0ed3aae397e0cbe38c)) Update graphqlkotlin to v6.5.6 ([#685](https://github.com/Suwayomi/Suwayomi-Server/pull/685) by @schroda)
- ([r1368](https://github.com/Suwayomi/Suwayomi-Server/commit/c9423ef425e327e3908b2ab4cbc2febcc4d139e2)) Send every download status change to the subscriber ([#684](https://github.com/Suwayomi/Suwayomi-Server/pull/684) by @schroda)
- ([r1367](https://github.com/Suwayomi/Suwayomi-Server/commit/7086055ec33f84b6d667ac193e734f14e1d113d9)) Handle finished downloads that weren't removed from the queue ([#683](https://github.com/Suwayomi/Suwayomi-Server/pull/683) by @schroda)
- ([r1366](https://github.com/Suwayomi/Suwayomi-Server/commit/553b35d218d4b8a3569d37f5264004e448d259b4)) Feature/improve automatic chapter downloads ([#680](https://github.com/Suwayomi/Suwayomi-Server/pull/680) by @schroda)
- ([r1365](https://github.com/Suwayomi/Suwayomi-Server/commit/c910026308543f8c80e375868638aa5c1dcad76c)) Do not reset already loaded config when updating config file ([#679](https://github.com/Suwayomi/Suwayomi-Server/pull/679) by @schroda)
- ([r1364](https://github.com/Suwayomi/Suwayomi-Server/commit/35be9f14e4ff185d75639344f6a2cedeb4a5636a)) Return correct latest compatible webUI version ([#677](https://github.com/Suwayomi/Suwayomi-Server/pull/677) by @schroda)
- ([r1363](https://github.com/Suwayomi/Suwayomi-Server/commit/abcbec9c2ab109cddac367224461ff42c92552ce)) Fix/downloader not creating folder or cbz file ([#676](https://github.com/Suwayomi/Suwayomi-Server/pull/676) by @schroda)
- ([r1362](https://github.com/Suwayomi/Suwayomi-Server/commit/ff6f5d7e89b33a20614ccbe3b76ed3f358839cf0)) Add more fields to the manga graphql type ([#675](https://github.com/Suwayomi/Suwayomi-Server/pull/675) by @schroda)
- ([r1361](https://github.com/Suwayomi/Suwayomi-Server/commit/56deea9fb30961eb37de52ce2b85d3ec671ca018)) Feature/graphql logging ([#674](https://github.com/Suwayomi/Suwayomi-Server/pull/674) by @schroda)
- ([r1360](https://github.com/Suwayomi/Suwayomi-Server/commit/1c9a139006f7a9e399c964b2a88650fb757d8369)) Always return "ArchiveProvider" in case "downloadAsCbz" is enabled ([#671](https://github.com/Suwayomi/Suwayomi-Server/pull/671) by @schroda)
- ([r1359](https://github.com/Suwayomi/Suwayomi-Server/commit/4d89c324b98a3d9ad4c2cf05d81623a24534aecb)) Fix Oracle JRE Extension Install ([#670](https://github.com/Suwayomi/Suwayomi-Server/pull/670) by @Syer10)
- ([r1358](https://github.com/Suwayomi/Suwayomi-Server/commit/a76ce0391160e11dc4159a5d60b401542a2200b7)) Throw error instead of returning null ([#666](https://github.com/Suwayomi/Suwayomi-Server/pull/666) by @schroda)
- ([r1357](https://github.com/Suwayomi/Suwayomi-Server/commit/9ee3f46ff0512e5d7fb853d35c112168f3a1d839)) Feature/graphql chapter pages mutation handle downloaded chapters ([#665](https://github.com/Suwayomi/Suwayomi-Server/pull/665) by @schroda)
- ([r1356](https://github.com/Suwayomi/Suwayomi-Server/commit/3343007cf850a471b0a8b8a6674e7d492282c5fc)) Add mutation to install external extension ([#667](https://github.com/Suwayomi/Suwayomi-Server/pull/667) by @schroda)
- ([r1355](https://github.com/Suwayomi/Suwayomi-Server/commit/c42d314b76b53c50e158111485ebf2ef973f1471)) Move source download dirs to new download subfolder ([#660](https://github.com/Suwayomi/Suwayomi-Server/pull/660) by @schroda)
- ([r1354](https://github.com/Suwayomi/Suwayomi-Server/commit/8db6c2153e479e7e3b104570326c835f418eeaae)) Fix some settings not being applied properly ([#661](https://github.com/Suwayomi/Suwayomi-Server/pull/661) by @Syer10)
- ([r1353](https://github.com/Suwayomi/Suwayomi-Server/commit/5baf54335b3c3551687bd6169f9001c3b418f2fd)) Feature/updater provide more info about update ([#657](https://github.com/Suwayomi/Suwayomi-Server/pull/657) by @schroda)
- ([r1352](https://github.com/Suwayomi/Suwayomi-Server/commit/d9019b8f46fb61862846fa04673dee2db174b716)) Correctly emit changed values ([#656](https://github.com/Suwayomi/Suwayomi-Server/pull/656) by @schroda)
- ([r1351](https://github.com/Suwayomi/Suwayomi-Server/commit/a31446557d4f3151ecacd960a37d9425010f2d90)) Feature/graphql server settings ([#629](https://github.com/Suwayomi/Suwayomi-Server/pull/629) by @schroda)
- ([r1350](https://github.com/Suwayomi/Suwayomi-Server/commit/321fbe22dd2b666291614c70cf11bd4a16b54088)) Feature/listen to server config value changes ([#617](https://github.com/Suwayomi/Suwayomi-Server/pull/617) by @schroda)
- ([r1349](https://github.com/Suwayomi/Suwayomi-Server/commit/01ab912bd9940c7ea191ad2dd0b0dc7b0166c628)) Remove unnecessary "downloadNewChapters" call in "fetchChapters" mutation ([#652](https://github.com/Suwayomi/Suwayomi-Server/pull/652) by @schroda)
- ([r1348](https://github.com/Suwayomi/Suwayomi-Server/commit/557bad60bc0cef220ea7a20b3bde5e98a341f24a)) Prevent last page read to be greater than max page count ([#655](https://github.com/Suwayomi/Suwayomi-Server/pull/655) by @schroda)
- ([r1347](https://github.com/Suwayomi/Suwayomi-Server/commit/f2dd67d87f38c30c8df6f3718ce392197afbff9a)) Feature/decouple thumbnail downloads and cache ([#581](https://github.com/Suwayomi/Suwayomi-Server/pull/581) by @schroda)
- ([r1346](https://github.com/Suwayomi/Suwayomi-Server/commit/b8b92c8d698e4ed548ce2000140ae7a8f3d9b349)) Suspend setupBundledWebUI() ([#650](https://github.com/Suwayomi/Suwayomi-Server/pull/650) by @Syer10)
- ([r1345](https://github.com/Suwayomi/Suwayomi-Server/commit/74ff112e7a9cf0dddf5896ef86a08ecc9c9ce7c3)) Feature/graphql web UI ([#649](https://github.com/Suwayomi/Suwayomi-Server/pull/649) by @schroda)
- ([r1344](https://github.com/Suwayomi/Suwayomi-Server/commit/684bb1875c667a8887bfeebcf8c38e1d9c23b8fd)) Fix/webinterfacemanager update to bundled webui ([#648](https://github.com/Suwayomi/Suwayomi-Server/pull/648) by @schroda)
- ([r1343](https://github.com/Suwayomi/Suwayomi-Server/commit/f6fec2424c6d3548bc178c790aad6259309579f5)) Fix/extracting assets from apks ([#644](https://github.com/Suwayomi/Suwayomi-Server/pull/644) by @schroda)
- ([r1342](https://github.com/Suwayomi/Suwayomi-Server/commit/2889029b706798c8ebe497c04aee39e63b33b9a9)) Fix/downloader manager persisting queue ([#639](https://github.com/Suwayomi/Suwayomi-Server/pull/639) by @schroda)
- ([r1341](https://github.com/Suwayomi/Suwayomi-Server/commit/b56b4fa8134f4ab6246a8f7202afd70d5d2094ea)) Update Local Source to latest Tachiyomi ([#637](https://github.com/Suwayomi/Suwayomi-Server/pull/637) by @Syer10)
- ([r1340](https://github.com/Suwayomi/Suwayomi-Server/commit/00bc055d6938856342b108720d2804259635d4b4)) Fix/load extension log load failure ([#641](https://github.com/Suwayomi/Suwayomi-Server/pull/641) by @schroda)
- ([r1339](https://github.com/Suwayomi/Suwayomi-Server/commit/6fd291c7e3904cea8e08c21dd391bb36c87f8d9d)) Fetch downloaded chapters page again in case the stored file can't be retrieved ([#640](https://github.com/Suwayomi/Suwayomi-Server/pull/640) by @schroda)
- ([r1338](https://github.com/Suwayomi/Suwayomi-Server/commit/dbdb787076acf8c06368b6b624bf87f32631ed64)) Restore download queue async ([#638](https://github.com/Suwayomi/Suwayomi-Server/pull/638) by @schroda)
- ([r1337](https://github.com/Suwayomi/Suwayomi-Server/commit/fc788a718d5dd95163d88ff5e0ae2951ba08f833)) Add 128 px icon ([#636](https://github.com/Suwayomi/Suwayomi-Server/pull/636) by @Syer10)
- ([r1336](https://github.com/Suwayomi/Suwayomi-Server/commit/e093fe6a0689f90e40cc6bd7808cca4ea804e98c)) Add CookieManager implementation ([#635](https://github.com/Suwayomi/Suwayomi-Server/pull/635) by @Syer10)
- ([r1335](https://github.com/Suwayomi/Suwayomi-Server/commit/cdce3680429fb5e5332c4d74346452483ae7a99f)) Fix Graphql-WS errors and Improve Downloader Subscription ([#634](https://github.com/Suwayomi/Suwayomi-Server/pull/634) by @Syer10)
- ([r1334](https://github.com/Suwayomi/Suwayomi-Server/commit/689847d864ccdb29b70833e988b9811fe41af377)) Update dependencies ([#611](https://github.com/Suwayomi/Suwayomi-Server/pull/611) by @Syer10)
- ([r1333](https://github.com/Suwayomi/Suwayomi-Server/commit/3675580d876c53fa8b964db01b0b873e1afb0e2f)) Add Subscriptions to GraphiQL and Update ([#631](https://github.com/Suwayomi/Suwayomi-Server/pull/631) by @Syer10)
- ([r1332](https://github.com/Suwayomi/Suwayomi-Server/commit/92f494d0fe4420cf8ada1ebe4a20a5762ebc4969)) Implement Graphql-WS Subscriptions ([#630](https://github.com/Suwayomi/Suwayomi-Server/pull/630) by @Syer10)
- ([r1331](https://github.com/Suwayomi/Suwayomi-Server/commit/06d7a6d892ec98b15583a980065acb1bfed2cefb)) Info Queries ([#627](https://github.com/Suwayomi/Suwayomi-Server/pull/627) by @Syer10)
- ([r1330](https://github.com/Suwayomi/Suwayomi-Server/commit/e2754200af7eb8af99bd3ca2956465f6da838027)) Use Tachidesk-Launcher ([#618](https://github.com/Suwayomi/Suwayomi-Server/pull/618) by @Syer10)
- ([r1329](https://github.com/Suwayomi/Suwayomi-Server/commit/cdb083ff4805b215892ab38a90609b3389e64f04)) Downloader Queries and Mutations ([#610](https://github.com/Suwayomi/Suwayomi-Server/pull/610) by @Syer10)
- ([r1328](https://github.com/Suwayomi/Suwayomi-Server/commit/c3fb08d634f263dbc7f1f3c4832ae695d6d4ea2d)) Library Update Queries and Mutations ([#609](https://github.com/Suwayomi/Suwayomi-Server/pull/609) by @Syer10)
- ([r1327](https://github.com/Suwayomi/Suwayomi-Server/commit/78a167aacf29e86d959c3b7679714c6b255a3931)) Fix/webui setup failure in case bundled webui is missing ([#625](https://github.com/Suwayomi/Suwayomi-Server/pull/625) by @schroda)
- ([r1326](https://github.com/Suwayomi/Suwayomi-Server/commit/5a913fdfbbfb96223625b4372b49e4e407311adf)) Make path to local source changeable ([#626](https://github.com/Suwayomi/Suwayomi-Server/pull/626) by @schroda)
- ([r1325](https://github.com/Suwayomi/Suwayomi-Server/commit/f0a190e8d2e1a0f50f6f97d85b698f4799b1d383)) Update to bundled webUI version if necessary ([#619](https://github.com/Suwayomi/Suwayomi-Server/pull/619) by @schroda)
- ([r1324](https://github.com/Suwayomi/Suwayomi-Server/commit/a2715fb85140bbdd65b7e9e079ffd7342a5df868)) Feature/webui update download failure do not immediately fallback to bundled version ([#620](https://github.com/Suwayomi/Suwayomi-Server/pull/620) by @schroda)
- ([r1323](https://github.com/Suwayomi/Suwayomi-Server/commit/47e5b03f45734a5963a65ebb771a3be5c06f0b5f)) Fix some manga filters ([#624](https://github.com/Suwayomi/Suwayomi-Server/pull/624) by @Syer10)
- ([r1322](https://github.com/Suwayomi/Suwayomi-Server/commit/251141a5c3b66babc0b99a208349bbe16582b2d0)) Fix/downloader ([#622](https://github.com/Suwayomi/Suwayomi-Server/pull/622) by @schroda)
- ([r1321](https://github.com/Suwayomi/Suwayomi-Server/commit/6ac8f4c45d4be32065ef1c73396bb2c78060cce5)) Use mathematical modulo implementation for calculations ([#616](https://github.com/Suwayomi/Suwayomi-Server/pull/616) by @schroda)
- ([r1320](https://github.com/Suwayomi/Suwayomi-Server/commit/7ebefa7c42578a1500da8bedf7603a4b924ca699)) Fix/updater scheduling auto updates ([#615](https://github.com/Suwayomi/Suwayomi-Server/pull/615) by @schroda)
- ([r1319](https://github.com/Suwayomi/Suwayomi-Server/commit/9e4c90f220a49e63bf0f9b21588757a08e6c4502)) Always update the last webUI update check timestamp ([#614](https://github.com/Suwayomi/Suwayomi-Server/pull/614) by @schroda)
- ([r1318](https://github.com/Suwayomi/Suwayomi-Server/commit/50f988641bd0b93dd740e49561bbe1a8e864a587)) Fix/ha scheduler rescheduling ha tasks ([#613](https://github.com/Suwayomi/Suwayomi-Server/pull/613) by @schroda)
- ([r1317](https://github.com/Suwayomi/Suwayomi-Server/commit/e53b9d4790859a001bc2eee3d8c9ecaec3615cbd)) Fix/ha scheduler not triggering missed executions due to not meeting the threshold ([#612](https://github.com/Suwayomi/Suwayomi-Server/pull/612) by @schroda)
- ([r1316](https://github.com/Suwayomi/Suwayomi-Server/commit/027805c4d50c8cb300693f56c6b4db6a6ab51fba)) Preserve download queue through server restarts ([#599](https://github.com/Suwayomi/Suwayomi-Server/pull/599) by @schroda)
- ([r1315](https://github.com/Suwayomi/Suwayomi-Server/commit/c02496c4f0df624ebf08521973314c43b945a3b8)) Fix/updater automated update max interval of 23 hours ([#606](https://github.com/Suwayomi/Suwayomi-Server/pull/606) by @schroda)
- ([r1314](https://github.com/Suwayomi/Suwayomi-Server/commit/2a83f290a5714c203e67f1bea0518920af736967)) Use "backupInterval" to disable auto backups ([#608](https://github.com/Suwayomi/Suwayomi-Server/pull/608) by @schroda)
- ([r1313](https://github.com/Suwayomi/Suwayomi-Server/commit/d4f9b0b1bc044d13dea0b80d638890dc2460a8c0)) Feature/log to file ([#607](https://github.com/Suwayomi/Suwayomi-Server/pull/607) by @schroda)
- ([r1312](https://github.com/Suwayomi/Suwayomi-Server/commit/2452b03a49572b73a30bea0ea87cf576f864fe3f)) Schedule automated update only once per hour ([#605](https://github.com/Suwayomi/Suwayomi-Server/pull/605) by @schroda)
- ([r1311](https://github.com/Suwayomi/Suwayomi-Server/commit/2ce423b6cbdf5dcd68d27db93bbab394cec06a40)) Correctly check if a new version is available for the preview channel ([#604](https://github.com/Suwayomi/Suwayomi-Server/pull/604) by @schroda)
- ([r1310](https://github.com/Suwayomi/Suwayomi-Server/commit/e9206158b83c67dd4b89fc7f1c36b41e84a94e31)) Feature/move server frontend mapping to the frontend ([#591](https://github.com/Suwayomi/Suwayomi-Server/pull/591) by @schroda)
- ([r1309](https://github.com/Suwayomi/Suwayomi-Server/commit/8690e918dd1a05637234544e055b0870aa660aa5)) Feature/automatically download new chapters ([#596](https://github.com/Suwayomi/Suwayomi-Server/pull/596) by @schroda)
- ([r1308](https://github.com/Suwayomi/Suwayomi-Server/commit/c1d702a51c69538f9bd785e515aa3d881d582370)) Feature/improve automated backup ([#597](https://github.com/Suwayomi/Suwayomi-Server/pull/597) by @schroda)
- ([r1307](https://github.com/Suwayomi/Suwayomi-Server/commit/0338ac3810df42c47374d08917c883b10b95bc8e)) Extract assets from apk file ([#602](https://github.com/Suwayomi/Suwayomi-Server/pull/602) by @schroda)
- ([r1306](https://github.com/Suwayomi/Suwayomi-Server/commit/526fef85e4f887fc9b27a1ddf0d097c027ef2cc8)) Feature/global update trigger automatically ([#593](https://github.com/Suwayomi/Suwayomi-Server/pull/593) by @schroda)
- ([r1305](https://github.com/Suwayomi/Suwayomi-Server/commit/49f2d8588ad8797ebb559c704fd01cbc2c76ae9e)) Feature/automated backups ([#595](https://github.com/Suwayomi/Suwayomi-Server/pull/595) by @schroda)
- ([r1304](https://github.com/Suwayomi/Suwayomi-Server/commit/9a80992aec5edfc5293f1fed79d5e34cad14cb74)) Correctly read resource in build jar and dev mode ([#594](https://github.com/Suwayomi/Suwayomi-Server/pull/594) by @schroda)
- ([r1303](https://github.com/Suwayomi/Suwayomi-Server/commit/32d0890dba4f6bf9bf6da55717ec687e82fc9ce1)) Proxy thumbnail urls ([#589](https://github.com/Suwayomi/Suwayomi-Server/pull/589) by @Syer10)
- ([r1302](https://github.com/Suwayomi/Suwayomi-Server/commit/b4d37f9ba2e53627047fa5597dd17980e56ac8c2)) Make sure "UserConfig" is up-to-date ([#590](https://github.com/Suwayomi/Suwayomi-Server/pull/590) by @schroda)
- ([r1301](https://github.com/Suwayomi/Suwayomi-Server/commit/5372ef8f0c4c6250d6a08bccc1b913c67cddeab5)) Manga for Source data loader ([#588](https://github.com/Suwayomi/Suwayomi-Server/pull/588) by @Syer10)
- ([r1300](https://github.com/Suwayomi/Suwayomi-Server/commit/a11b654c3d1d1cbf299fb4496039175fc0ad075c)) Backup creation and restore gql endpoints ([#587](https://github.com/Suwayomi/Suwayomi-Server/pull/587) by @Syer10)
- ([r1299](https://github.com/Suwayomi/Suwayomi-Server/commit/1a9a0b3394c84387d7dde93c3ffbbb10f95dd37b)) Exclude "default" category from reordering ([#586](https://github.com/Suwayomi/Suwayomi-Server/pull/586) by @schroda)
- ([r1298](https://github.com/Suwayomi/Suwayomi-Server/commit/890920a57b922057dc3f336de93a1c507a14f435)) Freeze graphql playground scripts to working versions ([#585](https://github.com/Suwayomi/Suwayomi-Server/pull/585) by @schroda)
- ([r1297](https://github.com/Suwayomi/Suwayomi-Server/commit/7fe7de5fdf71dcfcd9d0ab575b393e8a66cf1807)) Fix fetchSourceManga filtering (by @Syer10)
- ([r1296](https://github.com/Suwayomi/Suwayomi-Server/commit/b9b115d0ea1e581ca33264b19d9bf6b5d417e2da)) Rewrite filter and preference mutations ([#577](https://github.com/Suwayomi/Suwayomi-Server/pull/577) by @Syer10)
- ([r1295](https://github.com/Suwayomi/Suwayomi-Server/commit/08af195f1160b2753fb6bf9f7c6558a7b893b507)) Fix graphql/plugin-explorer urls ([#584](https://github.com/Suwayomi/Suwayomi-Server/pull/584) by @schroda)
- ([r1294](https://github.com/Suwayomi/Suwayomi-Server/commit/71cde729fc708155f5fd733c0b85a610bdcc9d26)) Delete tmp files on request failure ([#582](https://github.com/Suwayomi/Suwayomi-Server/pull/582) by @schroda)
- ([r1293](https://github.com/Suwayomi/Suwayomi-Server/commit/077f0a03f69a6e48d5a0274a6a40f319ce9e60a2)) Update "dex2jar" to v61 ([#583](https://github.com/Suwayomi/Suwayomi-Server/pull/583) by @schroda)
- ([r1292](https://github.com/Suwayomi/Suwayomi-Server/commit/812eb8001b66ef412644cafa64743b3f7845ae95)) Add fetch chapter pages ([#576](https://github.com/Suwayomi/Suwayomi-Server/pull/576) by @Syer10)
- ([r1291](https://github.com/Suwayomi/Suwayomi-Server/commit/b59af683ac418ba01ed4437b8ad3ca1588d25221)) Do not count mangas as part of categories that aren't in the library ([#574](https://github.com/Suwayomi/Suwayomi-Server/pull/574) by @schroda)
- ([r1290](https://github.com/Suwayomi/Suwayomi-Server/commit/561d680e783cdf03a9c48907a56a4b436cb43908)) Exclude mangas with specific state from global update ([#537](https://github.com/Suwayomi/Suwayomi-Server/pull/537) by @schroda)
- ([r1289](https://github.com/Suwayomi/Suwayomi-Server/commit/7c3eff2ba72d37be67e31984c7579684f86fc879)) Complete source mutations ([#567](https://github.com/Suwayomi/Suwayomi-Server/pull/567) by @Syer10)
- ([r1288](https://github.com/Suwayomi/Suwayomi-Server/commit/300c0a8f35496e9329af16c3c30de50872918bf4)) Category Mutations ([#566](https://github.com/Suwayomi/Suwayomi-Server/pull/566) by @Syer10)
- ([r1287](https://github.com/Suwayomi/Suwayomi-Server/commit/51bfdc094748079387e299ebed0c69c9c1554e85)) Feature/make config settings changeable during runtime ([#545](https://github.com/Suwayomi/Suwayomi-Server/pull/545) by @schroda)
- ([r1286](https://github.com/Suwayomi/Suwayomi-Server/commit/a64566c0f3ae6491e7f91a56cb6634ed47dfc3da)) fill in the cover according to spec ([#571](https://github.com/Suwayomi/Suwayomi-Server/pull/571) by @AriaMoradi)
- ([r1285](https://github.com/Suwayomi/Suwayomi-Server/commit/dbb9a80ea6f778b0c298e941f7885f186f0a48f8)) use commons-compress everywhere ([#570](https://github.com/Suwayomi/Suwayomi-Server/pull/570) by @AriaMoradi)
- ([r1284](https://github.com/Suwayomi/Suwayomi-Server/commit/e930c54246894f853620d06b62759a54d1243161)) improve zip parsing ([#569](https://github.com/Suwayomi/Suwayomi-Server/pull/569) by @AriaMoradi)
- ([r1283](https://github.com/Suwayomi/Suwayomi-Server/commit/dfff047cbfb14e7d1506b0ce37e31584fffc6a60)) Fix cascade migration ([#565](https://github.com/Suwayomi/Suwayomi-Server/pull/565) by @Syer10)
- ([r1282](https://github.com/Suwayomi/Suwayomi-Server/commit/44fb2b02bcd355efcba0687541239fa9ba30ba1c)) Fix global meta delete ([#564](https://github.com/Suwayomi/Suwayomi-Server/pull/564) by @Syer10)
- ([r1281](https://github.com/Suwayomi/Suwayomi-Server/commit/6a7efafd9f20500d87507122f8ff88c349868936)) Improve database column references and default category handling ([#563](https://github.com/Suwayomi/Suwayomi-Server/pull/563) by @Syer10)
- ([r1280](https://github.com/Suwayomi/Suwayomi-Server/commit/241abc3956a1c335a85077c5dbf7c0a82ccac4f2)) Add items that are related to the deleted meta ([#562](https://github.com/Suwayomi/Suwayomi-Server/pull/562) by @Syer10)
- ([r1279](https://github.com/Suwayomi/Suwayomi-Server/commit/1e82c879bf3d93e28add11fbe23422d77f452e57)) Add default category to the database ([#561](https://github.com/Suwayomi/Suwayomi-Server/pull/561) by @Syer10)
- ([r1278](https://github.com/Suwayomi/Suwayomi-Server/commit/a81d01d2e3f1f8b6ba89d99a1206d538cebf1672)) Don't use data fetchers in mutations ([#559](https://github.com/Suwayomi/Suwayomi-Server/pull/559) by @Syer10)
- ([r1277](https://github.com/Suwayomi/Suwayomi-Server/commit/2230796504ce938c510c5cdab0a3c7875db99702)) Extension mutations ([#560](https://github.com/Suwayomi/Suwayomi-Server/pull/560) by @Syer10)
- ([r1276](https://github.com/Suwayomi/Suwayomi-Server/commit/458ca7c7cfd2ea69dcb46038b26d57ff41f7157f)) Fix update chapters ([#557](https://github.com/Suwayomi/Suwayomi-Server/pull/557) by @Syer10)
- ([r1275](https://github.com/Suwayomi/Suwayomi-Server/commit/3f91663ecf7f4e34b0ba87010e26c3b597adcc81)) Rewrite meta and add meta mutations ([#556](https://github.com/Suwayomi/Suwayomi-Server/pull/556) by @Syer10)
- ([r1274](https://github.com/Suwayomi/Suwayomi-Server/commit/04a671382afe70a70d9a2c63337fc7ab965b4859)) Improve GQL Playground ([#558](https://github.com/Suwayomi/Suwayomi-Server/pull/558) by @Syer10)
- ([r1273](https://github.com/Suwayomi/Suwayomi-Server/commit/945ec818e590132587ed8323d036268bdfabefec)) Remove category filter ([#551](https://github.com/Suwayomi/Suwayomi-Server/pull/551) by @Syer10)
- ([r1272](https://github.com/Suwayomi/Suwayomi-Server/commit/ff7ac8a78580d33d9c04b087304a2e3eae73e190)) Fetch Manga and Chapters in GQL ([#555](https://github.com/Suwayomi/Suwayomi-Server/pull/555) by @Syer10)
- ([r1271](https://github.com/Suwayomi/Suwayomi-Server/commit/603105e2ea480a39ba20904e0d05383a62d35836)) Fix StringFilter ([#554](https://github.com/Suwayomi/Suwayomi-Server/pull/554) by @Syer10)
- ([r1270](https://github.com/Suwayomi/Suwayomi-Server/commit/5475567b485cacc2da8bda5c737dddc2865d3e3e)) Cleanup download type ([#553](https://github.com/Suwayomi/Suwayomi-Server/pull/553) by @Syer10)
- ([r1269](https://github.com/Suwayomi/Suwayomi-Server/commit/2aec0adb086c29c278f1ca94545ea52e15dc73ab)) Category mangas ([#552](https://github.com/Suwayomi/Suwayomi-Server/pull/552) by @Syer10)
- ([r1268](https://github.com/Suwayomi/Suwayomi-Server/commit/54fc3761bf0413d69ce07a2f1b7fe926dbe9148f)) Put graphql under api ([#549](https://github.com/Suwayomi/Suwayomi-Server/pull/549) by @Syer10)
- ([r1267](https://github.com/Suwayomi/Suwayomi-Server/commit/99e1912bfeb7547f480725ee9fb6b3372f44fb66)) Fix manga/source and manga/chapters for graphql ([#548](https://github.com/Suwayomi/Suwayomi-Server/pull/548) by @Syer10)
- ([r1266](https://github.com/Suwayomi/Suwayomi-Server/commit/ecc1cabafddd2bc9b03b742ef32dfc777130a8f3)) Merge pull request #547 from Suwayomi/graphql ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @AriaMoradi)
- ([r1265](https://github.com/Suwayomi/Suwayomi-Server/commit/1a5b847b239633de0e32945597a4b255e94704f8)) Update README.md (by @AriaMoradi)
- ([r1264](https://github.com/Suwayomi/Suwayomi-Server/commit/d3409e7133735e5f495be1a872d335dc776e89f1)) Update README.md (by @AriaMoradi)
- ([r1263](https://github.com/Suwayomi/Suwayomi-Server/commit/4e553e3eb3cb661ceeeca9323c220bcc550c1fc6)) better description about the Tachiyomi extension (by @AriaMoradi)
- ([r1262](https://github.com/Suwayomi/Suwayomi-Server/commit/4577bbc572ac1a73e000e2cdbb52f447e0344ef2)) More mutations ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1261](https://github.com/Suwayomi/Suwayomi-Server/commit/da8ca2349688237cba6d390c65ef32c62f9c1be3)) Start working on mutations ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1260](https://github.com/Suwayomi/Suwayomi-Server/commit/988853be63fdb0e462018ee087a91b0de078895d)) Seems like this should return null if it errors ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1259](https://github.com/Suwayomi/Suwayomi-Server/commit/cde5dc5bfa4ce6cce6d565b41589672a754460c0)) Update "dex2jar" to v60 ([#538](https://github.com/Suwayomi/Suwayomi-Server/pull/538) by @schroda)
- ([r1258](https://github.com/Suwayomi/Suwayomi-Server/commit/b617250effc103adcf915de47b9098f0f1063e22)) Delete updates query since the chapters query can now mimic it ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1257](https://github.com/Suwayomi/Suwayomi-Server/commit/313da995365d41100eec73025d927b0a9f84e275)) Add in library filter for chapters ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1256](https://github.com/Suwayomi/Suwayomi-Server/commit/442e24521682354aa5000d47fd95893fd97dbebf)) Update TODO ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1255](https://github.com/Suwayomi/Suwayomi-Server/commit/050ab170193517bf38823365e7f376fabe6e3a88)) Complete SourceQuery ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1254](https://github.com/Suwayomi/Suwayomi-Server/commit/c80f488a13a19903c0ceaf013b6d8443613ae6f4)) Complete ChapterQuery ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1253](https://github.com/Suwayomi/Suwayomi-Server/commit/cf73804c7162749cbf99045acd8570a8970e4875)) Complete ExtensionQuery ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1252](https://github.com/Suwayomi/Suwayomi-Server/commit/a90e5d13ea71310db9b2eb76f21aeecf9188a5b3)) Complete MetaQuery ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1251](https://github.com/Suwayomi/Suwayomi-Server/commit/891fb0b4794adc233273ce2286012decd646523f)) Simplify keyset pagination ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1250](https://github.com/Suwayomi/Suwayomi-Server/commit/58a623d44dc9e08a460246cba0c477f5790bf39d)) Fix keyset pagination for non-unique order by modes ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1249](https://github.com/Suwayomi/Suwayomi-Server/commit/0e84b8a1541663083e92ae7fde6eaace42c92a01)) Lint ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1248](https://github.com/Suwayomi/Suwayomi-Server/commit/a4dfcf80e4741180867cf523876989962ae3ef5a)) Implement manga status filter ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1247](https://github.com/Suwayomi/Suwayomi-Server/commit/d8567eadb2ef2355416a3a31c31c74124565a73a)) Simplify queries ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1246](https://github.com/Suwayomi/Suwayomi-Server/commit/0b88207ad524e8e34c96caf35a67252cd3c5fba2)) Fix empty results errors ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1245](https://github.com/Suwayomi/Suwayomi-Server/commit/671466a737ae47645bf4b0f93f01c6e26bf6900f)) Complete CategoryQuery ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1244](https://github.com/Suwayomi/Suwayomi-Server/commit/84881a0d52e82271c7bd616c080691fa1a27bcdf)) Complete MangaQuery ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1243](https://github.com/Suwayomi/Suwayomi-Server/commit/a589049cc7f5357cb41ead6eb79186c6ad00cec0)) Move things around and introduce Cursor type ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1242](https://github.com/Suwayomi/Suwayomi-Server/commit/17877e0f17f461ab0dffe606934ceb823f12bde7)) Fix case insensitive ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1241](https://github.com/Suwayomi/Suwayomi-Server/commit/1ed9bef2a1ede5646ca1829205af0e0e6a027e64)) Fix the playground explorer and add a updated default query ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1240](https://github.com/Suwayomi/Suwayomi-Server/commit/a6dddf311c4c6f89794305793983b02905279a74)) Basically finish MangaQuery, only paging left ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1239](https://github.com/Suwayomi/Suwayomi-Server/commit/e8c2bad18796f5c329e41a99cd650415bb2260e4)) Handle missing objects in graphql ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1238](https://github.com/Suwayomi/Suwayomi-Server/commit/52bda2c08051e187584073858e247657592f79e9)) Start working on graphql paging ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1237](https://github.com/Suwayomi/Suwayomi-Server/commit/607919f40f7ed008299ca71792f7171edbb9c895)) Implement more query parameters ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1236](https://github.com/Suwayomi/Suwayomi-Server/commit/d830638ee6e1ada9c4b80f6b9c308d7a42471c03)) Use actual MangaStatus enum ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1235](https://github.com/Suwayomi/Suwayomi-Server/commit/106bda20972d453a35b1aa964d83a6ae5b96703e)) Proper conversion Scalar for Long to String and back ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1234](https://github.com/Suwayomi/Suwayomi-Server/commit/7debb27374887a5bf6aa65ad55466ce7fedd5da9)) Might not need a updates query ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1233](https://github.com/Suwayomi/Suwayomi-Server/commit/05b5a7f598723f30fd5c8a0b783900a1aed6661d)) Add updates ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1232](https://github.com/Suwayomi/Suwayomi-Server/commit/3bbda7ba549e4c9eaa2314a22f5ba3a3915a9ee3)) More todos ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1231](https://github.com/Suwayomi/Suwayomi-Server/commit/9312f5fd14b9cb8e90be7e3ddb6cc35d6bb21e30)) Add global meta ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1230](https://github.com/Suwayomi/Suwayomi-Server/commit/399eb07e359fa7d87d4f566a936143eac78815ee)) Fix imports ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1229](https://github.com/Suwayomi/Suwayomi-Server/commit/eb197ebceef573defff5b3738a195c218bcb29ad)) Switch database logger to SLF4J ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1228](https://github.com/Suwayomi/Suwayomi-Server/commit/4c30d8ab05ccb59dee13b714c6a8e6df18096d03)) Some TODOs with ideas ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1227](https://github.com/Suwayomi/Suwayomi-Server/commit/3a67ddf0f697967e4dbc33f3ce84aac142f4b791)) Add Extensions to Graphql ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1226](https://github.com/Suwayomi/Suwayomi-Server/commit/6541c7b5b7a2219b14ac0524f77939e9b1ee36ef)) Serialize Long as String in graphql ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1225](https://github.com/Suwayomi/Suwayomi-Server/commit/37f41ade43827fed827a381f1e28bd27d04a1797)) Directly use the database for sources in graphql ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1224](https://github.com/Suwayomi/Suwayomi-Server/commit/007d20d41754efcf6eef269aa9947771a17c4e2a)) Add Sources to Graphql ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1223](https://github.com/Suwayomi/Suwayomi-Server/commit/00370a81fa38e7f63a48a759c32dd7acb1e80ce8)) Minor cleanup ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1222](https://github.com/Suwayomi/Suwayomi-Server/commit/d4599c3331cab595b810b870a48cf94a91a14751)) Use Graphiql with the Explorer plugin for the query builder ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1221](https://github.com/Suwayomi/Suwayomi-Server/commit/bce76bbcf36a21fc1916566a8e23303f7ccb5bca)) Use Kotlin Coroutines Flow instead of Project reactor ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @Syer10)
- ([r1220](https://github.com/Suwayomi/Suwayomi-Server/commit/847a5fe71b088cf1a355653471ccd82a0ff12168)) Subscriptions! ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @martinek)
- ([r1219](https://github.com/Suwayomi/Suwayomi-Server/commit/e2fa0032391191a2ed8536213f989bf9acc5824e)) Rewrite graphql controller execute as function without docs ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @martinek)
- ([r1218](https://github.com/Suwayomi/Suwayomi-Server/commit/0c555e88d379b14161727237a09f4840a46bf1a2)) Update graphql-playground endpoint ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @martinek)
- ([r1217](https://github.com/Suwayomi/Suwayomi-Server/commit/bf7f1a04b33f5d35080a01e1e8912f254b48345f)) Add categories to graphql ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @martinek)
- ([r1216](https://github.com/Suwayomi/Suwayomi-Server/commit/623172af6d2d26ecb67b9ea2ea70a129d6ff1d35)) Add mutation for updating chapters ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @martinek)
- ([r1215](https://github.com/Suwayomi/Suwayomi-Server/commit/4fb689d9e4ff54143a8685d77513cbe2265c6428)) Add chapter and manga meta field ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @martinek)
- ([r1214](https://github.com/Suwayomi/Suwayomi-Server/commit/6054c489c6feda3e2d585ad1de97d51dba0fc126)) Add graphql playground ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @martinek)
- ([r1213](https://github.com/Suwayomi/Suwayomi-Server/commit/21719f4408d73a22cae2f197b078b3af1f08cbd8)) Add basic graphql implementation with manga and chapters loading with data loaders ([#547](https://github.com/Suwayomi/Suwayomi-Server/pull/547) by @martinek)
- ([r1212](https://github.com/Suwayomi/Suwayomi-Server/commit/f2a650ba02e1ffd25313e354a98bb72ead322fd9)) fix typo (by @AriaMoradi)
- ([r1211](https://github.com/Suwayomi/Suwayomi-Server/commit/871c28b1ea51c2e03bf5754a949e467df555b90d)) cleanup notes (by @AriaMoradi)
- ([r1210](https://github.com/Suwayomi/Suwayomi-Server/commit/d3aa32147a017113c1a4fecf437518133a116e12)) Add logic to only update specific categories ([#520](https://github.com/Suwayomi/Suwayomi-Server/pull/520) by @schroda)
- ([r1209](https://github.com/Suwayomi/Suwayomi-Server/commit/9a50f2e4089c6766388adefc397f8fd59bcc063c)) Notify clients even if no manga gets updated ([#531](https://github.com/Suwayomi/Suwayomi-Server/pull/531) by @schroda)
- ([r1208](https://github.com/Suwayomi/Suwayomi-Server/commit/dcde4947e83f4850a037184aff9c158233d23a5c)) Emit update to clients after adding all mangas to the queue ([#521](https://github.com/Suwayomi/Suwayomi-Server/pull/521) by @schroda)
- ([r1207](https://github.com/Suwayomi/Suwayomi-Server/commit/5b61bdc3a81d5704ca283271b4ea25874fce778f)) add size field to Category data class ([#519](https://github.com/Suwayomi/Suwayomi-Server/pull/519) by @schroda)
- ([r1206](https://github.com/Suwayomi/Suwayomi-Server/commit/ec1d65f4c3e3d74199ab5066870c7b531a66b76f)) update library grouped by source ([#511](https://github.com/Suwayomi/Suwayomi-Server/pull/511) by @schroda)
- ([r1205](https://github.com/Suwayomi/Suwayomi-Server/commit/a0081dec07ed0fabd6fa8a18beb873563ac0af08)) fix manga unread and download count ([#509](https://github.com/Suwayomi/Suwayomi-Server/pull/509) by @akabhirav)
- ([r1204](https://github.com/Suwayomi/Suwayomi-Server/commit/783787e5141ebc3e469d3a27c504ce7c210c0a7b)) Send last read chapter in Mangas in Category API ([#507](https://github.com/Suwayomi/Suwayomi-Server/pull/507) by @akabhirav)
- ([r1203](https://github.com/Suwayomi/Suwayomi-Server/commit/ac99dd55a2991d49f62c3b388582b6a40ee1857d)) Fix random page sent when manga is downloaded ([#508](https://github.com/Suwayomi/Suwayomi-Server/pull/508) by @akabhirav)
- ([r1202](https://github.com/Suwayomi/Suwayomi-Server/commit/c56f984952f6591cbf5d53be37a7d3868633b09b)) Fix SharedPreferences.Editor.clear and SharedPreferences.Editor.remove ([#505](https://github.com/Suwayomi/Suwayomi-Server/pull/505) by @Syer10)
- ([r1201](https://github.com/Suwayomi/Suwayomi-Server/commit/9269ca726eabcf91aa041d37ff33fc805812c611)) It's not us, I swear ;;; (by @AriaMoradi)
- ([r1200](https://github.com/Suwayomi/Suwayomi-Server/commit/eca3205dcf43a0e32e5f5976e2ef49205beff8a2)) Update winget.yml ([#500](https://github.com/Suwayomi/Suwayomi-Server/pull/500) by @DattatreyaReddy)
- ([r1199](https://github.com/Suwayomi/Suwayomi-Server/commit/13f5486d0b929b3c2513ba4e0a2e01f20281fbbd)) Fix CBZ download bug for newly added mangas in Library ([#499](https://github.com/Suwayomi/Suwayomi-Server/pull/499) by @akabhirav)
- ([r1198](https://github.com/Suwayomi/Suwayomi-Server/commit/d4e71274f94a066309cb4881042cf4673075a5d0)) update changelog (by @AriaMoradi)

## Suwayomi-WebUI Changelog
- ([r1409](https://github.com/Suwayomi/Suwayomi-WebUI/commit/21bb931a74155a75b14fb4c307dcd18108a3de09)) Use full available width for reader component ([#618](https://github.com/Suwayomi/Suwayomi-WebUI/pull/618) by @schroda)
- ([r1408](https://github.com/Suwayomi/Suwayomi-WebUI/commit/629b742140e21ab7cd51abd947f32b8d6dc54780)) Feature/settings add new socks proxy settings ([#617](https://github.com/Suwayomi/Suwayomi-WebUI/pull/617) by @schroda)
- ([r1407](https://github.com/Suwayomi/Suwayomi-WebUI/commit/ab0ecf4da0a2511fd0a8d8885bce07e53dfd5840)) Center page number correctly ([#616](https://github.com/Suwayomi/Suwayomi-WebUI/pull/616) by @schroda)
- ([r1406](https://github.com/Suwayomi/Suwayomi-WebUI/commit/ceec260d14d4c48d0c5f8b093af7039e849cd304)) Feature/reader setting add scale small pages ([#615](https://github.com/Suwayomi/Suwayomi-WebUI/pull/615) by @schroda)
- ([r1405](https://github.com/Suwayomi/Suwayomi-WebUI/commit/9531825babc7f4ccc28c3a84022d6ef0aa01ff22)) Fix size of pages in continues reader mode ([#613](https://github.com/Suwayomi/Suwayomi-WebUI/pull/613) by @schroda)
- ([r1404](https://github.com/Suwayomi/Suwayomi-WebUI/commit/d8ee676d550b677a11568773065e7254b2f12096)) Prevent invisible pages ([#614](https://github.com/Suwayomi/Suwayomi-WebUI/pull/614) by @schroda)
- ([r1403](https://github.com/Suwayomi/Suwayomi-WebUI/commit/97ab4004a61b243cc3420818488e9685728315ce)) Do not update chapter in case it has not been loaded yet ([#612](https://github.com/Suwayomi/Suwayomi-WebUI/pull/612) by @schroda)
- ([r1402](https://github.com/Suwayomi/Suwayomi-WebUI/commit/5a9618aefe6876b1b9cab8660ac05202cbb6bafa)) Correctly update function refs when state changes ([#611](https://github.com/Suwayomi/Suwayomi-WebUI/pull/611) by @schroda)
- ([r1401](https://github.com/Suwayomi/Suwayomi-WebUI/commit/e8bfc28350328776f379cde1cb48e3c83d88bf87)) [VersionMapping] Require server version "r1487" for preview ([#610](https://github.com/Suwayomi/Suwayomi-WebUI/pull/610) by @schroda)
- ([r1400](https://github.com/Suwayomi/Suwayomi-WebUI/commit/02dc9ca7365f96d0ab2ad61bfb5c1b0824ece151)) Add "thumbnailUrlLastFetched" to thumbnail url ([#607](https://github.com/Suwayomi/Suwayomi-WebUI/pull/607) by @schroda)
- ([r1399](https://github.com/Suwayomi/Suwayomi-WebUI/commit/e5ffbb462c191a9f94e57bd837371d9d10374690)) Feature/gql remove download ahead limit ([#608](https://github.com/Suwayomi/Suwayomi-WebUI/pull/608) by @schroda)
- ([r1398](https://github.com/Suwayomi/Suwayomi-WebUI/commit/fd651b61655315395775f6fdbe6aba672651111f)) Feature/add vui as webui flavor ([#609](https://github.com/Suwayomi/Suwayomi-WebUI/pull/609) by @schroda)
- ([r1397](https://github.com/Suwayomi/Suwayomi-WebUI/commit/05077b4a2be05f10f7593b769bba77cc8eaf9ae1)) Correctly link to custom repos settings ([#603](https://github.com/Suwayomi/Suwayomi-WebUI/pull/603) by @schroda)
- ([r1396](https://github.com/Suwayomi/Suwayomi-WebUI/commit/99c9d9d12f3c4a70066f4821d49be99815d3d2f9)) Use set reader width on small devices ([#602](https://github.com/Suwayomi/Suwayomi-WebUI/pull/602) by @schroda)
- ([r1395](https://github.com/Suwayomi/Suwayomi-WebUI/commit/aea112e29298ba6a694e9af2375340220a6717b3)) Create correct manga thumbnail url ([#601](https://github.com/Suwayomi/Suwayomi-WebUI/pull/601) by @schroda)
- ([r1394](https://github.com/Suwayomi/Suwayomi-WebUI/commit/4b54b0939576490948620f01996e593a73581985)) Rename "ExtensionSettings" to "BrowseSettings" ([#600](https://github.com/Suwayomi/Suwayomi-WebUI/pull/600) by @schroda)
- ([r1393](https://github.com/Suwayomi/Suwayomi-WebUI/commit/e383cc9ea8d3a3f59c17551833e5438809ff1ed2)) Add info text to download ahead setting ([#599](https://github.com/Suwayomi/Suwayomi-WebUI/pull/599) by @schroda)
- ([r1392](https://github.com/Suwayomi/Suwayomi-WebUI/commit/c099e01fb70b95412661a98e7669aceda8b2aa87)) Add manga fetch timestamp to thumbnail url ([#598](https://github.com/Suwayomi/Suwayomi-WebUI/pull/598) by @schroda)
- ([r1391](https://github.com/Suwayomi/Suwayomi-WebUI/commit/2acf2b6d33742b9b09becce3912347f0d9f3f1f3)) Feature/download ahead trigger chapter downloads client side ([#597](https://github.com/Suwayomi/Suwayomi-WebUI/pull/597) by @schroda)
- ([r1390](https://github.com/Suwayomi/Suwayomi-WebUI/commit/0edec685f9d96eb0cdf94d6e176b730fc743fb65)) Correctly select next chapter id for download ahead ([#596](https://github.com/Suwayomi/Suwayomi-WebUI/pull/596) by @schroda)
- ([r1389](https://github.com/Suwayomi/Suwayomi-WebUI/commit/d244ba65381e68206229dcacc1e20b7c9409ae5a)) Fit double page reader pages correctly to windows width ([#595](https://github.com/Suwayomi/Suwayomi-WebUI/pull/595) by @schroda)
- ([r1388](https://github.com/Suwayomi/Suwayomi-WebUI/commit/4ffd3584c6f2c70cda9fd6e2a1630ed91e1d0c74)) Handle RTL reading direction for double page reader ([#594](https://github.com/Suwayomi/Suwayomi-WebUI/pull/594) by @schroda)
- ([r1387](https://github.com/Suwayomi/Suwayomi-WebUI/commit/444ebf80071c083588c79e31f18d98eb740b2564)) Fix/reader outdated chapter page count ([#593](https://github.com/Suwayomi/Suwayomi-WebUI/pull/593) by @schroda)
- ([r1386](https://github.com/Suwayomi/Suwayomi-WebUI/commit/5017ba99328d3f200a92f2ffcfcdd50d2ec2bdaa)) Handle backup creation on same domain as server ([#592](https://github.com/Suwayomi/Suwayomi-WebUI/pull/592) by @schroda)
- ([r1385](https://github.com/Suwayomi/Suwayomi-WebUI/commit/7caea4e5262ecbd42a06a37de57623a2cafa9b61)) Download ahead only in case current and next chapter are downloaded (by @schroda)
- ([r1384](https://github.com/Suwayomi/Suwayomi-WebUI/commit/d5b633f5c97b4ba596703dc50a3150745ad621da)) Feature/improve create changelog script ([#591](https://github.com/Suwayomi/Suwayomi-WebUI/pull/591) by @schroda)
- ([r1383](https://github.com/Suwayomi/Suwayomi-WebUI/commit/bf3815e3bd35aaf33d4e7039d7e9021419d062eb)) Correctly update cache after updating an extension ([#590](https://github.com/Suwayomi/Suwayomi-WebUI/pull/590) by @schroda)
- ([r1382](https://github.com/Suwayomi/Suwayomi-WebUI/commit/7fb1dae9c4571586f5fc00a915a1119b4e670a47)) decrease reader's up and down arrows scrolling distance ([#588](https://github.com/Suwayomi/Suwayomi-WebUI/pull/588) by @JiPaix, @schroda)
- ([r1381](https://github.com/Suwayomi/Suwayomi-WebUI/commit/3077824ca70ffe8e1949d559dd376f5c89a6782c)) Update dependencies ([#587](https://github.com/Suwayomi/Suwayomi-WebUI/pull/587) by @schroda)
- ([r1380](https://github.com/Suwayomi/Suwayomi-WebUI/commit/76e5c674174c9f055a768cdfa9570f3e001b3cfc)) Translations update from Hosted Weblate ([#548](https://github.com/Suwayomi/Suwayomi-WebUI/pull/548) by @weblate, @jesusFx, @Yuhyeong, @a18ccms, @plum7x, @HiyoriTUK)
- ([r1379](https://github.com/Suwayomi/Suwayomi-WebUI/commit/b074de26a23758b8900591f31200c8359fe6b5da)) Fix/install external extension does not update extension list ([#580](https://github.com/Suwayomi/Suwayomi-WebUI/pull/580) by @schroda)
- ([r1378](https://github.com/Suwayomi/Suwayomi-WebUI/commit/506e0aa0e38c0bcb26931b85ef1d6d688b2d95ca)) Update extension list after removing an obsolete extension ([#579](https://github.com/Suwayomi/Suwayomi-WebUI/pull/579) by @schroda)
- ([r1377](https://github.com/Suwayomi/Suwayomi-WebUI/commit/cee566d9b294d6499e938488fadb497c5dbc2b3f)) [Codegen] Update manga chapter total count on initial refresh ([#582](https://github.com/Suwayomi/Suwayomi-WebUI/pull/582) by @schroda)
- ([r1376](https://github.com/Suwayomi/Suwayomi-WebUI/commit/c483e71bf1275c298979111ac244c2ff99794c20)) Remove automatic manga update ([#583](https://github.com/Suwayomi/Suwayomi-WebUI/pull/583) by @schroda)
- ([r1375](https://github.com/Suwayomi/Suwayomi-WebUI/commit/d6468bca57e9cdfd69531fb1e994a4bbc16a42bc)) Add manga migrate option to menu on mobile devices ([#584](https://github.com/Suwayomi/Suwayomi-WebUI/pull/584) by @schroda)
- ([r1374](https://github.com/Suwayomi/Suwayomi-WebUI/commit/6716339c0e754bd5aa3fc5bea4484354c6fb4697)) Set default value for resetting to 50% ([#585](https://github.com/Suwayomi/Suwayomi-WebUI/pull/585) by @schroda)
- ([r1373](https://github.com/Suwayomi/Suwayomi-WebUI/commit/891d6f4165a18656e5fc6e040ead218f8675dde0)) Fix/manga migration opening search twice ([#586](https://github.com/Suwayomi/Suwayomi-WebUI/pull/586) by @schroda)
- ([r1372](https://github.com/Suwayomi/Suwayomi-WebUI/commit/10ec7de628978eec481a829a119da80a8af6e86e)) Correctly calculate width ([#578](https://github.com/Suwayomi/Suwayomi-WebUI/pull/578) by @schroda)
- ([r1371](https://github.com/Suwayomi/Suwayomi-WebUI/commit/f335b59dca5af5f82a62ab8db0b88734a0e53097)) Actually send library db cleanup mutation ([#577](https://github.com/Suwayomi/Suwayomi-WebUI/pull/577) by @schroda)
- ([r1370](https://github.com/Suwayomi/Suwayomi-WebUI/commit/18ca66cb4046128cd24fd0c916d2c27a4ec62fdf)) Feature/settings add flaresolverr ([#568](https://github.com/Suwayomi/Suwayomi-WebUI/pull/568) by @schroda)
- ([r1369](https://github.com/Suwayomi/Suwayomi-WebUI/commit/771d6beb18dbecc3a3723eace34fe1f121385396)) Add missing gap to VerticalReader mode with fit to window setting ([#574](https://github.com/Suwayomi/Suwayomi-WebUI/pull/574) by @schroda)
- ([r1368](https://github.com/Suwayomi/Suwayomi-WebUI/commit/ab4cddfedde67aa3c287b10e8a81c300ffcc808f)) Force a reconnect in case a heartbeat is missing ([#569](https://github.com/Suwayomi/Suwayomi-WebUI/pull/569) by @schroda)
- ([r1367](https://github.com/Suwayomi/Suwayomi-WebUI/commit/15825fbe530bf1de58565db63545341b84225da9)) Prevent pages from being bigger than the 100% in width ([#573](https://github.com/Suwayomi/Suwayomi-WebUI/pull/573) by @schroda)
- ([r1366](https://github.com/Suwayomi/Suwayomi-WebUI/commit/2dab88eb7ef26eba29c96c9bbf6ef13fce95eea6)) Decrease default "reader width" to 50% ([#576](https://github.com/Suwayomi/Suwayomi-WebUI/pull/576) by @schroda)
- ([r1365](https://github.com/Suwayomi/Suwayomi-WebUI/commit/4d75474b39b1fda4c3e33e1fd58fce6df1e8db30)) Fix reader width ([#567](https://github.com/Suwayomi/Suwayomi-WebUI/pull/567) by @chancez, @schroda)
- ([r1364](https://github.com/Suwayomi/Suwayomi-WebUI/commit/e0c5e0521dbb56e3cfa2d7b3738908acf250feab)) Feature/manga migration ([#536](https://github.com/Suwayomi/Suwayomi-WebUI/pull/536) by @schroda)
- ([r1363](https://github.com/Suwayomi/Suwayomi-WebUI/commit/5224ad139168220bf141dc3e9e513dd73f27722f)) Add missing id to request ([#571](https://github.com/Suwayomi/Suwayomi-WebUI/pull/571) by @schroda)
- ([r1362](https://github.com/Suwayomi/Suwayomi-WebUI/commit/6edf3bad64b55c61bbd0f5d0f8baf6e18cd688a1)) [ESLint] Allow zero warnings ([#575](https://github.com/Suwayomi/Suwayomi-WebUI/pull/575) by @schroda)
- ([r1361](https://github.com/Suwayomi/Suwayomi-WebUI/commit/5032a0ae94d32fdf9e6d4239df9e5e448a1719cf)) Make reader width configurable ([#565](https://github.com/Suwayomi/Suwayomi-WebUI/pull/565) by @chancez)
- ([r1360](https://github.com/Suwayomi/Suwayomi-WebUI/commit/dcbf5a1899c415905be93b428faf93a4a87f7d0b)) Fix/library manga selection type error ([#566](https://github.com/Suwayomi/Suwayomi-WebUI/pull/566) by @schroda)
- ([r1359](https://github.com/Suwayomi/Suwayomi-WebUI/commit/69a62b6c2cfd32033e9b546d2005af9cd4c961ca)) Add webUI settings again ([#564](https://github.com/Suwayomi/Suwayomi-WebUI/pull/564) by @schroda)
- ([r1358](https://github.com/Suwayomi/Suwayomi-WebUI/commit/24379c1e46ce87aaa007b35529c80396d4d04af2)) Infinitely try to reconnect gql subscriptions ([#563](https://github.com/Suwayomi/Suwayomi-WebUI/pull/563) by @schroda)
- ([r1357](https://github.com/Suwayomi/Suwayomi-WebUI/commit/05f57730db4901d3b20b1e33f6b41afcc13baebf)) Support configuring automatic downloads by category ([#562](https://github.com/Suwayomi/Suwayomi-WebUI/pull/562) by @chancez)
- ([r1356](https://github.com/Suwayomi/Suwayomi-WebUI/commit/de596426bf705118a5f1d8d4da7d3d0942251d67)) [Codegen] Update generated files ([#561](https://github.com/Suwayomi/Suwayomi-WebUI/pull/561) by @schroda)
- ([r1355](https://github.com/Suwayomi/Suwayomi-WebUI/commit/f8e1bfc401ac4869e8d5e790d526cfa059680a42)) Correctly change the category of a manga from the library ([#560](https://github.com/Suwayomi/Suwayomi-WebUI/pull/560) by @schroda)
- ([r1354](https://github.com/Suwayomi/Suwayomi-WebUI/commit/1c2a196950553b365cacfdb7cb2974aac41558fe)) Fix/adding manga to library not updating category ([#559](https://github.com/Suwayomi/Suwayomi-WebUI/pull/559) by @schroda)
- ([r1353](https://github.com/Suwayomi/Suwayomi-WebUI/commit/e10cbf9e8f0f133ce84a9b647a919afac361df38)) Add extension settings screen ([#557](https://github.com/Suwayomi/Suwayomi-WebUI/pull/557) by @schroda)
- ([r1352](https://github.com/Suwayomi/Suwayomi-WebUI/commit/f0e1bd32493e6ea68bcd759a817f3200acc1dd2b)) Feature/extension list show info when no repo is defined ([#556](https://github.com/Suwayomi/Suwayomi-WebUI/pull/556) by @schroda)
- ([r1351](https://github.com/Suwayomi/Suwayomi-WebUI/commit/42e3d64f80fc5de93a464c758ecbb46d75bf9e61)) Clear extensions cache after extension repos change ([#555](https://github.com/Suwayomi/Suwayomi-WebUI/pull/555) by @schroda)
- ([r1350](https://github.com/Suwayomi/Suwayomi-WebUI/commit/bed586383f5b9f5720a91a8ac0ab5de987b8be8a)) Update extension repo regex to server changes ([#554](https://github.com/Suwayomi/Suwayomi-WebUI/pull/554) by @schroda)
- ([r1349](https://github.com/Suwayomi/Suwayomi-WebUI/commit/6708a958cebcc7d94ae3f860d4ad00580db3e9f0)) Render selection fab in case only one category exists ([#553](https://github.com/Suwayomi/Suwayomi-WebUI/pull/553) by @schroda)
- ([r1348](https://github.com/Suwayomi/Suwayomi-WebUI/commit/38b364bb550473697b2c6a7729a5942c44047f53)) Remove reader webtoon mode page gaps ([#552](https://github.com/Suwayomi/Suwayomi-WebUI/pull/552) by @schroda)
- ([r1347](https://github.com/Suwayomi/Suwayomi-WebUI/commit/738a22233bc1cf7fd3bdeb542ac57b73814f3674)) Internationalize failed img retry text ([#551](https://github.com/Suwayomi/Suwayomi-WebUI/pull/551) by @schroda)
- ([r1346](https://github.com/Suwayomi/Suwayomi-WebUI/commit/199b62341fb8c54ca17924b77ae56e21d61c2c33)) Feature/add retry button for failed image requests ([#550](https://github.com/Suwayomi/Suwayomi-WebUI/pull/550) by @schroda)
- ([r1345](https://github.com/Suwayomi/Suwayomi-WebUI/commit/9962e0713d4753782dad65b56c34c3ea4a81977c)) Adding page loading with Double Page Mode. ([#480](https://github.com/Suwayomi/Suwayomi-WebUI/pull/480) by @rickymcmuffin, @schroda)
- ([r1344](https://github.com/Suwayomi/Suwayomi-WebUI/commit/be5497af94a18336b52c892dbc714ecb24461969)) Add new library sort options ([#547](https://github.com/Suwayomi/Suwayomi-WebUI/pull/547) by @schroda)
- ([r1343](https://github.com/Suwayomi/Suwayomi-WebUI/commit/3973eda0897e2a1f080d2280b606d069faa3b27d)) [ServerMapping][Codegen] Update to latest server gql MangaType changes ([#546](https://github.com/Suwayomi/Suwayomi-WebUI/pull/546) by @schroda)
- ([r1342](https://github.com/Suwayomi/Suwayomi-WebUI/commit/955cc682fd7186587acd90b00fb929da0e1cdbba)) Apply filters when searching in SourceMangas ([#545](https://github.com/Suwayomi/Suwayomi-WebUI/pull/545) by @schroda)
- ([r1341](https://github.com/Suwayomi/Suwayomi-WebUI/commit/89e8472ebc6edc50ea9af74429f378394687abda)) Add disclaimer to custom repositories setting ([#544](https://github.com/Suwayomi/Suwayomi-WebUI/pull/544) by @schroda)
- ([r1340](https://github.com/Suwayomi/Suwayomi-WebUI/commit/fd1fa63064bd91b61de54947e68f50ae5aa8a59a)) Feature/show extension repo only in case more than one repo is set ([#543](https://github.com/Suwayomi/Suwayomi-WebUI/pull/543) by @schroda)
- ([r1339](https://github.com/Suwayomi/Suwayomi-WebUI/commit/684ae69470af71b74c746bd59264ca6f99cdd568)) Update tokens ([#542](https://github.com/Suwayomi/Suwayomi-WebUI/pull/542) by @schroda)
- ([r1338](https://github.com/Suwayomi/Suwayomi-WebUI/commit/1b3cc6bfe3857ba808f93089839c7e15851633e7)) Translations update from Hosted Weblate ([#541](https://github.com/Suwayomi/Suwayomi-WebUI/pull/541) by @weblate, @zmmx)
- ([r1337](https://github.com/Suwayomi/Suwayomi-WebUI/commit/8faa756ab51ac6200424727998defba001803e40)) Feature/improve custom extension repos support ([#540](https://github.com/Suwayomi/Suwayomi-WebUI/pull/540) by @schroda)
- ([r1336](https://github.com/Suwayomi/Suwayomi-WebUI/commit/39b79c6ebef0f71f64c51f69e9e1c1f22c09f1a7)) Feature/settings support custom extension repos ([#539](https://github.com/Suwayomi/Suwayomi-WebUI/pull/539) by @schroda)
- ([r1335](https://github.com/Suwayomi/Suwayomi-WebUI/commit/b42c3103f1c62f2eb4151ded7f3025b25ff26c4c)) Feature/rebrand to suwayomi ([#500](https://github.com/Suwayomi/Suwayomi-WebUI/pull/500) by @schroda)
- ([r1334](https://github.com/Suwayomi/Suwayomi-WebUI/commit/779dafe1dc04f1b6e9ee37e656f90e82542e58fd)) Pass correct group sizes to "GroupedVirtuoso" ([#537](https://github.com/Suwayomi/Suwayomi-WebUI/pull/537) by @schroda)
- ([r1333](https://github.com/Suwayomi/Suwayomi-WebUI/commit/db65b9df44521cb78e76c64328a308e071b65aac)) Feature/merge source and extensions screen on desktop ([#535](https://github.com/Suwayomi/Suwayomi-WebUI/pull/535) by @schroda)
- ([r1332](https://github.com/Suwayomi/Suwayomi-WebUI/commit/9caca6e753a8217e9928f0d4ca6d4fe5f893295a)) Update dependencies ([#534](https://github.com/Suwayomi/Suwayomi-WebUI/pull/534) by @schroda)
- ([r1331](https://github.com/Suwayomi/Suwayomi-WebUI/commit/6c46c562522b443d5194496203752dc67e40348f)) Handle showing disabled state of automatic chapter deletion ([#533](https://github.com/Suwayomi/Suwayomi-WebUI/pull/533) by @schroda)
- ([r1330](https://github.com/Suwayomi/Suwayomi-WebUI/commit/05fa3d0732d660b92ad125fd9bead7cab8a312d7)) Fix/chapter not getting deleted after being read ([#532](https://github.com/Suwayomi/Suwayomi-WebUI/pull/532) by @schroda)
- ([r1329](https://github.com/Suwayomi/Suwayomi-WebUI/commit/7d3d82556a33109d7d823175189b6b5f43ad4b87)) Handle extension update failure ([#530](https://github.com/Suwayomi/Suwayomi-WebUI/pull/530) by @schroda)
- ([r1328](https://github.com/Suwayomi/Suwayomi-WebUI/commit/37ce494fda66f0898f4fb7738e928b1f45bf0fd4)) Log promise failures instead of ignoring them ([#531](https://github.com/Suwayomi/Suwayomi-WebUI/pull/531) by @schroda)
- ([r1327](https://github.com/Suwayomi/Suwayomi-WebUI/commit/37d6b84cf41ceae1a6679c3fdfdc89e53cd348dc)) Use correct titles for manga actions in selection mode ([#529](https://github.com/Suwayomi/Suwayomi-WebUI/pull/529) by @schroda)
- ([r1326](https://github.com/Suwayomi/Suwayomi-WebUI/commit/edc2a62a7605209118eaaa778f0ef5fa21b29eb9)) Feature/cleanup files ([#528](https://github.com/Suwayomi/Suwayomi-WebUI/pull/528) by @schroda)
- ([r1325](https://github.com/Suwayomi/Suwayomi-WebUI/commit/ecea80f41e8962fb5636cfbe9d1d878dd9f24b63)) Merge manga action menus ([#527](https://github.com/Suwayomi/Suwayomi-WebUI/pull/527) by @schroda)
- ([r1324](https://github.com/Suwayomi/Suwayomi-WebUI/commit/a3a52064ccaac827ada5c64865de6acc95e6016e)) Feature/cleanup chapter actions ([#525](https://github.com/Suwayomi/Suwayomi-WebUI/pull/525) by @schroda)
- ([r1323](https://github.com/Suwayomi/Suwayomi-WebUI/commit/d15be603b1afde980acd4d53ed3a03a3178f3e8e)) Use up-to-date manga data for selection fab actions ([#526](https://github.com/Suwayomi/Suwayomi-WebUI/pull/526) by @schroda)
- ([r1322](https://github.com/Suwayomi/Suwayomi-WebUI/commit/356303ab5adf0f33f3dcfa5c24fb821268b82842)) Allow browser context menu for images in reader ([#524](https://github.com/Suwayomi/Suwayomi-WebUI/pull/524) by @schroda)
- ([r1321](https://github.com/Suwayomi/Suwayomi-WebUI/commit/120e97e882a9a12552363b5ba97965c9c2700669)) Feature/restore backup inform about missing sources ([#523](https://github.com/Suwayomi/Suwayomi-WebUI/pull/523) by @schroda)
- ([r1320](https://github.com/Suwayomi/Suwayomi-WebUI/commit/a1dca02feac1d0952e8bd4923c1539c9e9d268c9)) Add missing extension key field to mutation result ([#522](https://github.com/Suwayomi/Suwayomi-WebUI/pull/522) by @schroda)
- ([r1319](https://github.com/Suwayomi/Suwayomi-WebUI/commit/f81b20b4fdec6956466e1adf60a999def4a2b174)) Fix/library continue read button causes page refresh ([#521](https://github.com/Suwayomi/Suwayomi-WebUI/pull/521) by @schroda)
- ([r1318](https://github.com/Suwayomi/Suwayomi-WebUI/commit/f12f025e9d8850dd978cd9f8f482928581a63765)) Add option to remove non library mangas from categories ([#520](https://github.com/Suwayomi/Suwayomi-WebUI/pull/520) by @schroda)
- ([r1317](https://github.com/Suwayomi/Suwayomi-WebUI/commit/e025efb9dc8a04d208b7549c121259d93832be92)) Feature/add manga to library category select dialog ([#519](https://github.com/Suwayomi/Suwayomi-WebUI/pull/519) by @schroda)
- ([r1316](https://github.com/Suwayomi/Suwayomi-WebUI/commit/1345cfe62498d0e859f62fc09679295080250578)) Update manga category selection in case categories changed ([#518](https://github.com/Suwayomi/Suwayomi-WebUI/pull/518) by @schroda)
- ([r1315](https://github.com/Suwayomi/Suwayomi-WebUI/commit/446deeae06b4c8f5f189685969105b185043f0a7)) Feature/library manga actions ([#506](https://github.com/Suwayomi/Suwayomi-WebUI/pull/506) by @schroda)
- ([r1314](https://github.com/Suwayomi/Suwayomi-WebUI/commit/980da657d99dd8d01fed82b5c80be7e3b01d0429)) [Codegen] Check cache before executing query for a single item ([#513](https://github.com/Suwayomi/Suwayomi-WebUI/pull/513) by @schroda)
- ([r1313](https://github.com/Suwayomi/Suwayomi-WebUI/commit/c115fcd5739ed17c3de26f5146c6d6e2a3381cda)) Feature/make selection logic reusable ([#515](https://github.com/Suwayomi/Suwayomi-WebUI/pull/515) by @schroda)
- ([r1312](https://github.com/Suwayomi/Suwayomi-WebUI/commit/c8747d6db4bac16915b41e742f9f78a43919107c)) Use correct key to normalize extensions ([#512](https://github.com/Suwayomi/Suwayomi-WebUI/pull/512) by @schroda)
- ([r1311](https://github.com/Suwayomi/Suwayomi-WebUI/commit/0615ef87ce03e4d9549c88cdedc052d1b164f762)) Add divider between library tabs and mangas ([#514](https://github.com/Suwayomi/Suwayomi-WebUI/pull/514) by @schroda)
- ([r1310](https://github.com/Suwayomi/Suwayomi-WebUI/commit/2390361fb2b63fbe0726c93c4ac3a4afd04e29d2)) [Codegen] Request manga download count with chapter deletion mutation ([#516](https://github.com/Suwayomi/Suwayomi-WebUI/pull/516) by @schroda)
- ([r1309](https://github.com/Suwayomi/Suwayomi-WebUI/commit/168e8f82f6f44c277f79467f2eb92aa3fc21a31b)) Use "Footer" to prevent fab overlapping the last item ([#517](https://github.com/Suwayomi/Suwayomi-WebUI/pull/517) by @schroda)
- ([r1308](https://github.com/Suwayomi/Suwayomi-WebUI/commit/09d1cf91e002db1667881648d216a690a0d4f516)) Prevent navigation state update in case path already changed ([#511](https://github.com/Suwayomi/Suwayomi-WebUI/pull/511) by @schroda)
- ([r1307](https://github.com/Suwayomi/Suwayomi-WebUI/commit/e4beafb11f1ee9dd4f464e7d627e40d58dad2115)) Cancel the navigation state update correctly ([#507](https://github.com/Suwayomi/Suwayomi-WebUI/pull/507) by @schroda)
- ([r1306](https://github.com/Suwayomi/Suwayomi-WebUI/commit/e966b0328bbee4bd91bd6f9ea13881bdfd31cbb3)) Remove unnecessary query refetches with mutations ([#508](https://github.com/Suwayomi/Suwayomi-WebUI/pull/508) by @schroda)
- ([r1305](https://github.com/Suwayomi/Suwayomi-WebUI/commit/3756b65256840e9bef6c277b73ddce977427800e)) Correctly check for dev env ([#509](https://github.com/Suwayomi/Suwayomi-WebUI/pull/509) by @schroda)
- ([r1304](https://github.com/Suwayomi/Suwayomi-WebUI/commit/f4e84412389af1332bc374856d1b44904d7b6270)) Prevent ApolloError handling the manga category mutation result ([#510](https://github.com/Suwayomi/Suwayomi-WebUI/pull/510) by @schroda)
- ([r1303](https://github.com/Suwayomi/Suwayomi-WebUI/commit/2f8c284c8b09298fb56182e8d7d7438a49180abf)) Add continue read button to library ([#505](https://github.com/Suwayomi/Suwayomi-WebUI/pull/505) by @schroda)
- ([r1302](https://github.com/Suwayomi/Suwayomi-WebUI/commit/663e20fff9d81f381ce7b56a89b3b484a460de5c)) Visualize read chapters in the update list ([#504](https://github.com/Suwayomi/Suwayomi-WebUI/pull/504) by @schroda)
- ([r1301](https://github.com/Suwayomi/Suwayomi-WebUI/commit/953ebbe5a639e737776ae3e4fc29581ce493f616)) Add button to mark all chapters as read ([#503](https://github.com/Suwayomi/Suwayomi-WebUI/pull/503) by @schroda)
- ([r1300](https://github.com/Suwayomi/Suwayomi-WebUI/commit/5010b706fdce5746bf20b47c7bd5e77c88b5b15e)) Add button to download all chapters ([#503](https://github.com/Suwayomi/Suwayomi-WebUI/pull/503) by @schroda)
- ([r1299](https://github.com/Suwayomi/Suwayomi-WebUI/commit/90cec353586f557337f2450f5067ab15a94a2275)) Add button to quickly select all chapters ([#503](https://github.com/Suwayomi/Suwayomi-WebUI/pull/503) by @schroda)
- ([r1298](https://github.com/Suwayomi/Suwayomi-WebUI/commit/226f2e170f0c82dad81eedc22e00a8d166dc8906)) Handle line breaks in the manga description ([#502](https://github.com/Suwayomi/Suwayomi-WebUI/pull/502) by @schroda)
- ([r1297](https://github.com/Suwayomi/Suwayomi-WebUI/commit/1300c10d83c62a05d36320e143f284c4a1263a25)) Remove unnecessary library refetch ([#499](https://github.com/Suwayomi/Suwayomi-WebUI/pull/499) by @schroda)
- ([r1296](https://github.com/Suwayomi/Suwayomi-WebUI/commit/12b6700110abab6205349960fb3a8f6428a3ba6f)) [VersionMapping] Require server version "r1438" for preview ([#498](https://github.com/Suwayomi/Suwayomi-WebUI/pull/498) by @schroda)
- ([r1295](https://github.com/Suwayomi/Suwayomi-WebUI/commit/f542c17fd682681bb5146ee5ef0e8629a83fa820)) Update download subscription to server changes ([#498](https://github.com/Suwayomi/Suwayomi-WebUI/pull/498) by @schroda)
- ([r1294](https://github.com/Suwayomi/Suwayomi-WebUI/commit/62568ce76b82333132ee7ca567dabe86dbcba1f8)) Translations update from Hosted Weblate ([#424](https://github.com/Suwayomi/Suwayomi-WebUI/pull/424) by @weblate, @alexandrejournet, @ibaraki-douji, @nitezs, @misaka10843, @Becods)
- ([r1293](https://github.com/Suwayomi/Suwayomi-WebUI/commit/214043fe9d77726641e7224705aaa7cace428c43)) Fix/script changelog creation ([#496](https://github.com/Suwayomi/Suwayomi-WebUI/pull/496) by @schroda)
- ([r1292](https://github.com/Suwayomi/Suwayomi-WebUI/commit/1dc60af67c1df2b49b864a8ea6c93b6ad48150ba)) Add logic to reorder downloads ([#495](https://github.com/Suwayomi/Suwayomi-WebUI/pull/495) by @schroda)
- ([r1291](https://github.com/Suwayomi/Suwayomi-WebUI/commit/b5f86ae6097d18048c5b4ef8fd5622460a32c31b)) Feature/virtualize download queue ([#494](https://github.com/Suwayomi/Suwayomi-WebUI/pull/494) by @schroda)
- ([r1290](https://github.com/Suwayomi/Suwayomi-WebUI/commit/abee8c7c55c7d0ebc4b39685f4a41912de9a9f71)) Use virtuoso grid state to restore the previous scroll position ([#492](https://github.com/Suwayomi/Suwayomi-WebUI/pull/492) by @schroda)
- ([r1289](https://github.com/Suwayomi/Suwayomi-WebUI/commit/b6b902797e2b9b82d2fd5d45c003dee887dd96a9)) Scroll to top when changing page ([#493](https://github.com/Suwayomi/Suwayomi-WebUI/pull/493) by @schroda)
- ([r1288](https://github.com/Suwayomi/Suwayomi-WebUI/commit/c51897ed54f729470f3661e2f847aad998360368)) Feature/download queue clear queue ([#490](https://github.com/Suwayomi/Suwayomi-WebUI/pull/490) by @schroda)
- ([r1287](https://github.com/Suwayomi/Suwayomi-WebUI/commit/7d574a29f6d01e9ce437c10480baf09104aac19e)) Correctly calculate the remaining time till the next update check ([#491](https://github.com/Suwayomi/Suwayomi-WebUI/pull/491) by @schroda)
- ([r1286](https://github.com/Suwayomi/Suwayomi-WebUI/commit/9cb72243d6e1bdf464cf2f4e2c11d829622f50c6)) Automatically check for server updates ([#489](https://github.com/Suwayomi/Suwayomi-WebUI/pull/489) by @schroda)
- ([r1285](https://github.com/Suwayomi/Suwayomi-WebUI/commit/78310496aa7483f65787b4022662bf662579d653)) Feature/about screen add option to check for and trigger updates ([#485](https://github.com/Suwayomi/Suwayomi-WebUI/pull/485) by @schroda)
- ([r1284](https://github.com/Suwayomi/Suwayomi-WebUI/commit/2caf88ce51d071a8fff0c53d302b0309dff1abff)) Remove incorrect "ListItemSecondaryAction" usage ([#486](https://github.com/Suwayomi/Suwayomi-WebUI/pull/486) by @schroda)
- ([r1283](https://github.com/Suwayomi/Suwayomi-WebUI/commit/fbf627b3aaea47ea88047b6c03804726f2983ec1)) Add option to clear the server cache ([#487](https://github.com/Suwayomi/Suwayomi-WebUI/pull/487) by @schroda)
- ([r1282](https://github.com/Suwayomi/Suwayomi-WebUI/commit/db5d3ff7c0062257f7096df906bdcee84a98c17e)) Remove "directLink" prop ([#488](https://github.com/Suwayomi/Suwayomi-WebUI/pull/488) by @schroda)
- ([r1281](https://github.com/Suwayomi/Suwayomi-WebUI/commit/ad0f0726517197000e2fcd90d81254a384897e0b)) Feature/automatic chapter deletion more options ([#484](https://github.com/Suwayomi/Suwayomi-WebUI/pull/484) by @schroda)
- ([r1280](https://github.com/Suwayomi/Suwayomi-WebUI/commit/ee03c56684aafb60e82b835c9d11f7a9671daaf8)) Fix/mark previous as read action includes the selected chapter ([#483](https://github.com/Suwayomi/Suwayomi-WebUI/pull/483) by @schroda)
- ([r1279](https://github.com/Suwayomi/Suwayomi-WebUI/commit/b2e6c040f154e7ae79bc41cf0e8b3e7d11113ae6)) [i18n] Format text to local lowercase ([#481](https://github.com/Suwayomi/Suwayomi-WebUI/pull/481) by @schroda)
- ([r1278](https://github.com/Suwayomi/Suwayomi-WebUI/commit/259d1d87df38b617a58ae2e0a19df30d4dc85cd7)) Show info about hosted WebUI in "About" ([#482](https://github.com/Suwayomi/Suwayomi-WebUI/pull/482) by @schroda)
- ([r1277](https://github.com/Suwayomi/Suwayomi-WebUI/commit/ee9811bf53d00a8117b760a392d769870f3efb38)) Correctly detect keyboard input "Enter" ([#479](https://github.com/Suwayomi/Suwayomi-WebUI/pull/479) by @schroda)
- ([r1276](https://github.com/Suwayomi/Suwayomi-WebUI/commit/c6cc7c14dd42eaa6b95339f8d798f3fe028b1df1)) Update the "lastRunningState" in case update was triggered outside of app ([#478](https://github.com/Suwayomi/Suwayomi-WebUI/pull/478) by @schroda)
- ([r1275](https://github.com/Suwayomi/Suwayomi-WebUI/commit/47b077cbf5f3d193f0f11b501f8244395c2dbf4e)) Feature/update dependencies ([#477](https://github.com/Suwayomi/Suwayomi-WebUI/pull/477) by @schroda)
- ([r1274](https://github.com/Suwayomi/Suwayomi-WebUI/commit/5aa7fc0f9fcf381739266715f37a1bdb22627a1f)) Remove icon for library search filter setting ([#476](https://github.com/Suwayomi/Suwayomi-WebUI/pull/476) by @schroda)
- ([r1273](https://github.com/Suwayomi/Suwayomi-WebUI/commit/2006ca910954f6b78d0f1cc4ad18a40dd75b7af1)) Feature/handle disabled download ahead limit by default ([#475](https://github.com/Suwayomi/Suwayomi-WebUI/pull/475) by @schroda)
- ([r1272](https://github.com/Suwayomi/Suwayomi-WebUI/commit/6b2245d730eececc6e462b1b771cd676a45e1eaa)) Move the last update timestamp to the body ([#472](https://github.com/Suwayomi/Suwayomi-WebUI/pull/472) by @schroda)
- ([r1271](https://github.com/Suwayomi/Suwayomi-WebUI/commit/7c69a4a5a1b2d49d23a00b17c6624f49222664fb)) Feature/global update last timestamp use stale data while fetching ([#473](https://github.com/Suwayomi/Suwayomi-WebUI/pull/473) by @schroda)
- ([r1270](https://github.com/Suwayomi/Suwayomi-WebUI/commit/a4bd44de9ed4197366cbcf2360293ea41a01523f)) Correctly merge chapter requests ([#474](https://github.com/Suwayomi/Suwayomi-WebUI/pull/474) by @schroda)
- ([r1269](https://github.com/Suwayomi/Suwayomi-WebUI/commit/aa01d6712b4ac4a66b3a134d77f0c1252a50dd16)) Update extensions list after extension update ([#471](https://github.com/Suwayomi/Suwayomi-WebUI/pull/471) by @schroda)
- ([r1268](https://github.com/Suwayomi/Suwayomi-WebUI/commit/7adc28a35c074fb134c2d760f91601c2fc5546a6)) Feature/gql improve queries mutations subscriptions ([#470](https://github.com/Suwayomi/Suwayomi-WebUI/pull/470) by @schroda)
- ([r1267](https://github.com/Suwayomi/Suwayomi-WebUI/commit/3b147b1b46a7b279ee961861fb1874867667602c)) Fix/update gql after server changes ([#469](https://github.com/Suwayomi/Suwayomi-WebUI/pull/469) by @schroda)
- ([r1266](https://github.com/Suwayomi/Suwayomi-WebUI/commit/3766540ce59d19918cfa2f7b8e1454782087d71f)) Add WebUI settings ([#460](https://github.com/Suwayomi/Suwayomi-WebUI/pull/460) by @schroda)
- ([r1265](https://github.com/Suwayomi/Suwayomi-WebUI/commit/cdf922945764eb26d7cdba5007f5fb8d1d44bbbd)) Feature/global update show last update time ([#468](https://github.com/Suwayomi/Suwayomi-WebUI/pull/468) by @schroda)
- ([r1264](https://github.com/Suwayomi/Suwayomi-WebUI/commit/99ba45ecebb3f1c8bc2735cf8499bc73f38432f4)) Use mui tooltip for manga titles ([#467](https://github.com/Suwayomi/Suwayomi-WebUI/pull/467) by @schroda)
- ([r1263](https://github.com/Suwayomi/Suwayomi-WebUI/commit/dbb4bf70af21ea74a4a92e54bdf2dc4a0495f2a9)) Use correct local source header in settings ([#466](https://github.com/Suwayomi/Suwayomi-WebUI/pull/466) by @schroda)
- ([r1262](https://github.com/Suwayomi/Suwayomi-WebUI/commit/d12ac27b617d67c41e7ed5d3ba936ce4fca47761)) Feature/download ahead while reading ([#464](https://github.com/Suwayomi/Suwayomi-WebUI/pull/464) by @schroda)
- ([r1261](https://github.com/Suwayomi/Suwayomi-WebUI/commit/e26907e2cea21ba113333e34c4433568aa0a8ecf)) Prevent TypeError when loading next chapter after last page ([#463](https://github.com/Suwayomi/Suwayomi-WebUI/pull/463) by @schroda)
- ([r1260](https://github.com/Suwayomi/Suwayomi-WebUI/commit/7db5435967a3d41abc976605a501cc688be1d873)) Remove "useCache" query from image requests ([#465](https://github.com/Suwayomi/Suwayomi-WebUI/pull/465) by @schroda)
- ([r1259](https://github.com/Suwayomi/Suwayomi-WebUI/commit/197ee5c94bb4153f8b4db2678a7f38c3a48c4cef)) Persist server settings when disabling them ([#462](https://github.com/Suwayomi/Suwayomi-WebUI/pull/462) by @schroda)
- ([r1258](https://github.com/Suwayomi/Suwayomi-WebUI/commit/1fd9b4e74459a41ed8502700bf717262763670f6)) Disable disallowed settings ([#461](https://github.com/Suwayomi/Suwayomi-WebUI/pull/461) by @schroda)
- ([r1257](https://github.com/Suwayomi/Suwayomi-WebUI/commit/eda682d9abe2bd74c7fcfeaf622880c99e4d5a94)) Remove deprecated cache setting ([#459](https://github.com/Suwayomi/Suwayomi-WebUI/pull/459) by @schroda)
- ([r1256](https://github.com/Suwayomi/Suwayomi-WebUI/commit/cac60fed2c60c2ec90ba7dc2240897993df0a475)) Feature/server settings ([#458](https://github.com/Suwayomi/Suwayomi-WebUI/pull/458) by @schroda)
- ([r1255](https://github.com/Suwayomi/Suwayomi-WebUI/commit/482db4626a7c13af4f769daa82807673a8bc4301)) Prevent infinite re-renders in extensions ([#457](https://github.com/Suwayomi/Suwayomi-WebUI/pull/457) by @schroda)
- ([r1254](https://github.com/Suwayomi/Suwayomi-WebUI/commit/8d4687428fac1f078a1c2acab1abe4c0b13054a5)) Fix/app search ([#456](https://github.com/Suwayomi/Suwayomi-WebUI/pull/456) by @schroda)
- ([r1253](https://github.com/Suwayomi/Suwayomi-WebUI/commit/35c34b6d1b7609acb59cce94a46ce2ca4dab7536)) Feature/search bar improvements ([#455](https://github.com/Suwayomi/Suwayomi-WebUI/pull/455) by @schroda)
- ([r1252](https://github.com/Suwayomi/Suwayomi-WebUI/commit/0c7498dba61da0ff3faec41124f236834d90ba5d)) Prevent pages from getting selected while dragging ([#454](https://github.com/Suwayomi/Suwayomi-WebUI/pull/454) by @schroda)
- ([r1251](https://github.com/Suwayomi/Suwayomi-WebUI/commit/dabe88385d0ff1017eea6e3df9d620cc6550e33f)) Feature/reader vertical pager keyboard bindings scrolling ([#452](https://github.com/Suwayomi/Suwayomi-WebUI/pull/452) by @schroda)
- ([r1250](https://github.com/Suwayomi/Suwayomi-WebUI/commit/9b96560a17b4af243d9990c107c7de765a3a7a48)) Feature/settings backup ([#453](https://github.com/Suwayomi/Suwayomi-WebUI/pull/453) by @schroda)
- ([r1249](https://github.com/Suwayomi/Suwayomi-WebUI/commit/b080286b81760eeb953f893b03e2b2df9cc0d84e)) Reduce chapter updates in the reader (by @schroda)
- ([r1248](https://github.com/Suwayomi/Suwayomi-WebUI/commit/a0a52b11a1260efc4a1532b34291e22f6bef7933)) Fix/pagination of sources which require pages to be fetched in order ([#451](https://github.com/Suwayomi/Suwayomi-WebUI/pull/451) by @schroda)
- ([r1247](https://github.com/Suwayomi/Suwayomi-WebUI/commit/345fbcb5c7322bd55b630489047085872840479b)) Fix/apollo client spamming infinite requets on failure ([#450](https://github.com/Suwayomi/Suwayomi-WebUI/pull/450) by @schroda)
- ([r1246](https://github.com/Suwayomi/Suwayomi-WebUI/commit/291e1899f98a96a8c40ee079521fa425c752c7ec)) Remove re-fetching of manga query on chapter update ([#449](https://github.com/Suwayomi/Suwayomi-WebUI/pull/449) by @schroda)
- ([r1245](https://github.com/Suwayomi/Suwayomi-WebUI/commit/c5f943d571649462167905b8957933f6def01a71)) Get latest manga data from the apollo cache ([#447](https://github.com/Suwayomi/Suwayomi-WebUI/pull/447) by @schroda)
- ([r1244](https://github.com/Suwayomi/Suwayomi-WebUI/commit/593acc7f89e5a2125a13b884d93358bcef141ee0)) Refresh extension list after updating ([#446](https://github.com/Suwayomi/Suwayomi-WebUI/pull/446) by @schroda)
- ([r1243](https://github.com/Suwayomi/Suwayomi-WebUI/commit/9284e46c45a8a97237c09cb887266eabd4e050dd)) Prevent SelectionFAB from being behind the "ChapterCard" checkbox ([#445](https://github.com/Suwayomi/Suwayomi-WebUI/pull/445) by @schroda)
- ([r1242](https://github.com/Suwayomi/Suwayomi-WebUI/commit/4ab61723e7e3e6ebdc30970572ed81149002b8b9)) Add missing tooltips ([#444](https://github.com/Suwayomi/Suwayomi-WebUI/pull/444) by @schroda)
- ([r1241](https://github.com/Suwayomi/Suwayomi-WebUI/commit/d9d92a75fa2bb3f8d45d34b4ae44da67296cdf7a)) Handle source not supporting browse "latest" ([#443](https://github.com/Suwayomi/Suwayomi-WebUI/pull/443) by @schroda)
- ([r1240](https://github.com/Suwayomi/Suwayomi-WebUI/commit/134e47763faae9e62db4d4e3a8387a74e32e5568)) Feature/update dependencies ([#442](https://github.com/Suwayomi/Suwayomi-WebUI/pull/442) by @schroda)
- ([r1239](https://github.com/Suwayomi/Suwayomi-WebUI/commit/7cb062d4534f568c96bdadbbf9066e6c9923d08d)) Feature/extensions always use fetch mutation to get list ([#440](https://github.com/Suwayomi/Suwayomi-WebUI/pull/440) by @schroda)
- ([r1238](https://github.com/Suwayomi/Suwayomi-WebUI/commit/2c35808ee4490acc8bc2e272e29d54443f0b6fe2)) Fetch chapter pages everytime unless chapter is downloaded ([#439](https://github.com/Suwayomi/Suwayomi-WebUI/pull/439) by @schroda)
- ([r1237](https://github.com/Suwayomi/Suwayomi-WebUI/commit/a6cc757d5044603e021a90ca89dfd0fa742c53e0)) Feature/global update settings update manga metadata ([#441](https://github.com/Suwayomi/Suwayomi-WebUI/pull/441) by @schroda)
- ([r1236](https://github.com/Suwayomi/Suwayomi-WebUI/commit/14e7af9a4aff324f40b56ac864da6577d0af52f3)) Feature/modify download settings ([#429](https://github.com/Suwayomi/Suwayomi-WebUI/pull/429) by @schroda)
- ([r1235](https://github.com/Suwayomi/Suwayomi-WebUI/commit/1acda66b1661d608a2b1deaaaa1c14ece344282d)) Feature/update backup restore to server changes ([#438](https://github.com/Suwayomi/Suwayomi-WebUI/pull/438) by @schroda)
- ([r1234](https://github.com/Suwayomi/Suwayomi-WebUI/commit/cc59f88a1d87bf2d234a874ad75d1588c845368a)) Correct library error translations ([#437](https://github.com/Suwayomi/Suwayomi-WebUI/pull/437) by @schroda)
- ([r1233](https://github.com/Suwayomi/Suwayomi-WebUI/commit/a638333684641dd755b0ed52371ffe176174edde)) Handle source browse when first page is also the last page ([#436](https://github.com/Suwayomi/Suwayomi-WebUI/pull/436) by @schroda)
- ([r1232](https://github.com/Suwayomi/Suwayomi-WebUI/commit/529fcf2d258c5fda99c777575c900336b2d9c357)) Mark first chapter as read for "mark previous as read" ([#435](https://github.com/Suwayomi/Suwayomi-WebUI/pull/435) by @schroda)
- ([r1231](https://github.com/Suwayomi/Suwayomi-WebUI/commit/4c6e9a8aa128f0a338ac48edc5456a018b5618cf)) Do not add mangas to the default category ([#433](https://github.com/Suwayomi/Suwayomi-WebUI/pull/433) by @schroda)
- ([r1230](https://github.com/Suwayomi/Suwayomi-WebUI/commit/678df88068f34a7219b61cf7a4a4746ce4dbf106)) Feature/modify global update settings ([#432](https://github.com/Suwayomi/Suwayomi-WebUI/pull/432) by @schroda)
- ([r1229](https://github.com/Suwayomi/Suwayomi-WebUI/commit/4c6d50740500d2823d7990c0192f9aebefea2575)) Feature/show backup restore progress ([#431](https://github.com/Suwayomi/Suwayomi-WebUI/pull/431) by @schroda)
- ([r1228](https://github.com/Suwayomi/Suwayomi-WebUI/commit/30fd8b0fca65416db9ee4aa439a227d3d675bb16)) Feature/support new tachiyomi backup file extension ([#430](https://github.com/Suwayomi/Suwayomi-WebUI/pull/430) by @schroda)
- ([r1227](https://github.com/Suwayomi/Suwayomi-WebUI/commit/5a5b12a9e10fb7d29622b6333013671efbf53011)) [ESLint] Prefer named exports ([#427](https://github.com/Suwayomi/Suwayomi-WebUI/pull/427) by @schroda)
- ([r1226](https://github.com/Suwayomi/Suwayomi-WebUI/commit/68e7b4b16d8cf44d4d50bb77ddb9764c38b1e78d)) Feature/library global update exclude manga with state ([#281](https://github.com/Suwayomi/Suwayomi-WebUI/pull/281) by @schroda)
- ([r1225](https://github.com/Suwayomi/Suwayomi-WebUI/commit/c8f02b3b8c70592cea6e9588118a304477e7e40c)) [ESLint] Add "no-unused-imports" plugin ([#426](https://github.com/Suwayomi/Suwayomi-WebUI/pull/426) by @schroda)
- ([r1224](https://github.com/Suwayomi/Suwayomi-WebUI/commit/76d1ba835c2e6d976dbcee86aaa584d273115016)) Merge pull request #395 from schroda/feature/use_graphql ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1223](https://github.com/Suwayomi/Suwayomi-WebUI/commit/09c7e804ba2a6f76615ebd3136ab4b490c38e7cc)) Refresh library mangas after update ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1222](https://github.com/Suwayomi/Suwayomi-WebUI/commit/b1aaeb4d5d5ce3f54eb64616563b6a81fcbe84a1)) Show loading text for include/exclude categories setting ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1221](https://github.com/Suwayomi/Suwayomi-WebUI/commit/2ad9ccd9bd4d78a9e5aa7ffc592e748c5f55bfd4)) Use gql for loading default category mangas ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1220](https://github.com/Suwayomi/Suwayomi-WebUI/commit/b6382bb33af231ac37810ea338d44e0f79429d2c)) Load only category mangas that are in the library ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1219](https://github.com/Suwayomi/Suwayomi-WebUI/commit/2297f36efb9aed7cf234cec1ed79d730b0f81102)) Add manga to default categories when adding to library ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1218](https://github.com/Suwayomi/Suwayomi-WebUI/commit/e2fed69c903ac2ec2a1d4fd01261afc20e5d3020)) Optimistically refresh categories on reordering ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1217](https://github.com/Suwayomi/Suwayomi-WebUI/commit/b7e1afd4f445c1c72325f5c93740107a6e4a79ca)) Update refetching queries and evicting cache data ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1216](https://github.com/Suwayomi/Suwayomi-WebUI/commit/5263a1102ccfb07268ebd91037a5b677e832130a)) Rename "doRequestNew" to "doRequest" ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1215](https://github.com/Suwayomi/Suwayomi-WebUI/commit/b3a432ed2e459cad395a9bbef7f1963a0c168af3)) Update typings ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1214](https://github.com/Suwayomi/Suwayomi-WebUI/commit/16ebfcb51f50c20fcfc4b336bf9f1f4ce0966897)) Correctly update extensions language selection ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1213](https://github.com/Suwayomi/Suwayomi-WebUI/commit/fbcc1fb91a6c166e6c9e24d9015bc4ad82250ee3)) Add optional options arg to all requests ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1212](https://github.com/Suwayomi/Suwayomi-WebUI/commit/d3e15e9d3b36b94072b43b9374d14d9753ac0e76)) Use gql for "subscriptions" ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1211](https://github.com/Suwayomi/Suwayomi-WebUI/commit/b79fbcf71dfe9984ad27ff8ed14df84aaafcf041)) Setup graphql subscriptions ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1210](https://github.com/Suwayomi/Suwayomi-WebUI/commit/6a1c302e451e10c9cb67d5b895de8e6c32b8849f)) Remove SWR ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1209](https://github.com/Suwayomi/Suwayomi-WebUI/commit/09f48c2c1734da28ef849c084a41f171d1057077)) Use gql for "backups" ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1208](https://github.com/Suwayomi/Suwayomi-WebUI/commit/e2f34f1f479c067eefa60f9e210734be4040382b)) Use gql for "fetchMore" workaround ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1207](https://github.com/Suwayomi/Suwayomi-WebUI/commit/aad87463f34d8fcbf547d67be5e2d9968156f559)) [Codegen] Use gql for "loading chapters" ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1206](https://github.com/Suwayomi/Suwayomi-WebUI/commit/78049282a770f912d4f28515937a3b7d0cd62658)) Use gql for "sources" - preferences ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1205](https://github.com/Suwayomi/Suwayomi-WebUI/commit/dcead5a801cec5e7cba92ee5effb458a2940a6ba)) Preserve selected filters on browser back navigation ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1204](https://github.com/Suwayomi/Suwayomi-WebUI/commit/12dd973179e8599fd34782b7f958c09d9609343d)) Use gql for "mangas" VI - source mangas filter ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1203](https://github.com/Suwayomi/Suwayomi-WebUI/commit/3297c7d96c62de34335264925c1d8d80fe376de1)) Use gql for "mangas" V - update manga categories ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1202](https://github.com/Suwayomi/Suwayomi-WebUI/commit/87aef85683a680969fc0999a431c09fd21707e06)) Use gql for "mangas" IV - source mangas popular/latest ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1201](https://github.com/Suwayomi/Suwayomi-WebUI/commit/0a73496cbabfd5acb0fe08375948b5a368ab3c6a)) Use gql for "mangas" III - global search ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1200](https://github.com/Suwayomi/Suwayomi-WebUI/commit/af9d49e5e99fa52a6534307f46ec5a630619c9d7)) Use gql for "mangas" II - category mangas ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1199](https://github.com/Suwayomi/Suwayomi-WebUI/commit/89dc053f00f20832c09271b8ed3239078e03df16)) Use gql for "mangas" I - get manga ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1198](https://github.com/Suwayomi/Suwayomi-WebUI/commit/a5b4b95bce8861751a74bf3c121e32f9c19bff46)) Use gql for "updater" ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1197](https://github.com/Suwayomi/Suwayomi-WebUI/commit/222f8f5c9b2af91373fdfbf688dafa4d34eb96a0)) Use gql for "downloader" ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1196](https://github.com/Suwayomi/Suwayomi-WebUI/commit/ca18e750bf83e883714a39e1327d25b6349e5a6f)) Use gql for "categories" ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1195](https://github.com/Suwayomi/Suwayomi-WebUI/commit/f0d55c01c861821e20671155acd1966c4e98b671)) Use gql for "updating chapters" ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1194](https://github.com/Suwayomi/Suwayomi-WebUI/commit/c75e184c5741799c20a76df185762534a3743d06)) Use gql for "updating mangas" ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1193](https://github.com/Suwayomi/Suwayomi-WebUI/commit/e1f338d0132e2970d9e4467b94a133fae2aebf72)) Use gql for "sources" ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1192](https://github.com/Suwayomi/Suwayomi-WebUI/commit/4a80e18cd0df8e9fc231af46afc8c2d6ddb1d5f6)) Use gql for "extensions" ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1191](https://github.com/Suwayomi/Suwayomi-WebUI/commit/81cfeedbba59037998e938ab031f6055f5cbaca6)) Use gql for "checkForUpdate" ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1190](https://github.com/Suwayomi/Suwayomi-WebUI/commit/59468bc5685d872600299f5d2f3997230fe57f2c)) Use gql for "about" ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1189](https://github.com/Suwayomi/Suwayomi-WebUI/commit/80cb06dfd5ecba58873ef7d029af67571585e188)) Use gql for "global metadata" ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1188](https://github.com/Suwayomi/Suwayomi-WebUI/commit/b9480736c31e742fbef78182419efcf2e203085d)) Log apollo errors ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1187](https://github.com/Suwayomi/Suwayomi-WebUI/commit/181eb6c811d2a149b7680935a6968abd73264c68)) Add graphql logic to RequestManager ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1186](https://github.com/Suwayomi/Suwayomi-WebUI/commit/0ee47c872b781e8f80c385f668bb982fd3244e85)) Introduce "BaseClient" ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1185](https://github.com/Suwayomi/Suwayomi-WebUI/commit/a0b686497f7b408ede50354f62541272caf0835b)) Move "RestClient" in sub folder ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1184](https://github.com/Suwayomi/Suwayomi-WebUI/commit/bffc1669e211fb9b7a082fa5661bb40bdc59a53b)) Move "RequestManager" in sub folder ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1183](https://github.com/Suwayomi/Suwayomi-WebUI/commit/1c794c24b5e9cc02d9998945091e9cf1783c84a6)) Setup intellij "GraphQL" plugin ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1182](https://github.com/Suwayomi/Suwayomi-WebUI/commit/87ddf2f8443140882f4e9d9334652ab6571c58ec)) [Codegen] Generate files ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1181](https://github.com/Suwayomi/Suwayomi-WebUI/commit/0087d3a87b13fffd9ad50e8d66fa98f38ade38d9)) [Tool][Codegen] Add script to post format the generated graphql file ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1180](https://github.com/Suwayomi/Suwayomi-WebUI/commit/10a57d3388660d479773bdd2d007e923a986d916)) Change "moduleResolution" to "node" ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1179](https://github.com/Suwayomi/Suwayomi-WebUI/commit/1b4497db46f9fa6756dc7c93311cc4e74c54670a)) Setup "graphql-codgen" ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1178](https://github.com/Suwayomi/Suwayomi-WebUI/commit/55c1c51edd03f3e7559b32face8dd3e78301cd92)) Create queries, mutations and subscriptions ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1177](https://github.com/Suwayomi/Suwayomi-WebUI/commit/ae5d2525f18912a91d0bf3fa9dfe0ab68740fe92)) Add "apollo-client" dependencies ([#395](https://github.com/Suwayomi/Suwayomi-WebUI/pull/395) by @schroda)
- ([r1176](https://github.com/Suwayomi/Suwayomi-WebUI/commit/5cf07acc1cc87834f6d091e71432c919f4c28e25)) Update BUILDING.md ([#420](https://github.com/Suwayomi/Suwayomi-WebUI/pull/420) by @skrewde)
- ([r1175](https://github.com/Suwayomi/Suwayomi-WebUI/commit/3155c1d602e1ec715f956fc0af2cd1c3361c0f8a)) Add option to offset first page in double page reader ([#418](https://github.com/Suwayomi/Suwayomi-WebUI/pull/418) by @rickymcmuffin)
- ([r1174](https://github.com/Suwayomi/Suwayomi-WebUI/commit/9daf71d85cbe79726af2b87422ee67360876724f)) Translations update from Hosted Weblate ([#411](https://github.com/Suwayomi/Suwayomi-WebUI/pull/411) by @weblate)
- ([r1173](https://github.com/Suwayomi/Suwayomi-WebUI/commit/4e134768ef028798efd3fc8b9ff7666c2e81daa2)) Feature/update dependencies ([#419](https://github.com/Suwayomi/Suwayomi-WebUI/pull/419) by @schroda)
- ([r1172](https://github.com/Suwayomi/Suwayomi-WebUI/commit/686f9d605f332b3bd1ecbe5a253f58f8c23fa434)) Improvements on double page ([#417](https://github.com/Suwayomi/Suwayomi-WebUI/pull/417) by @rickymcmuffin)
- ([r1171](https://github.com/Suwayomi/Suwayomi-WebUI/commit/6afd12b0bbf2938826a0f796888a42a1c8b35d3e)) Update required server version for preview to r1353 ([#415](https://github.com/Suwayomi/Suwayomi-WebUI/pull/415) by @schroda)
- ([r1170](https://github.com/Suwayomi/Suwayomi-WebUI/commit/a8f25f58bf2bcee878465545d3bd89cff21405cc)) Update dependencies ([#414](https://github.com/Suwayomi/Suwayomi-WebUI/pull/414) by @schroda)
- ([r1169](https://github.com/Suwayomi/Suwayomi-WebUI/commit/c23799f0e0714b54966cf14ead8567ba32429406)) Update "UpdateStatus" type to server changes ([#413](https://github.com/Suwayomi/Suwayomi-WebUI/pull/413) by @schroda)
- ([r1168](https://github.com/Suwayomi/Suwayomi-WebUI/commit/bde16d04112d3666a9f8f29979a99aa33bc08070)) Feature/update dependencies ([#410](https://github.com/Suwayomi/Suwayomi-WebUI/pull/410) by @schroda)
- ([r1167](https://github.com/Suwayomi/Suwayomi-WebUI/commit/38b69297f49b8d315fad4c27458d5d8117644da1)) Add new languages to resources ([#409](https://github.com/Suwayomi/Suwayomi-WebUI/pull/409) by @schroda)
- ([r1166](https://github.com/Suwayomi/Suwayomi-WebUI/commit/5d032d3748d0654bc1f9d05820a2908a954ea712)) Translations update from Hosted Weblate ([#403](https://github.com/Suwayomi/Suwayomi-WebUI/pull/403) by @weblate, @xconkhi9x)
- ([r1165](https://github.com/Suwayomi/Suwayomi-WebUI/commit/dd0ab4cb8604ceff9a52d94c4659743ec6b01d8a)) Show "inLibraryIndicator" in "VerticalGrid" ([#408](https://github.com/Suwayomi/Suwayomi-WebUI/pull/408) by @schroda)
- ([r1164](https://github.com/Suwayomi/Suwayomi-WebUI/commit/2f795b9d38d9594f655788cc9e3041206a9f1072)) Fix/tools scripts tsconfig and linting ([#406](https://github.com/Suwayomi/Suwayomi-WebUI/pull/406) by @schroda)
- ([r1163](https://github.com/Suwayomi/Suwayomi-WebUI/commit/ebf6c99dee61e3500a4acb7770315bd8b24a1719)) Fix contributing readme ([#405](https://github.com/Suwayomi/Suwayomi-WebUI/pull/405) by @schroda)
- ([r1162](https://github.com/Suwayomi/Suwayomi-WebUI/commit/48fe8d23e9d4b5e59e4ef70f8e082e098f0959da)) Fix/vite tsconfig setup ([#404](https://github.com/Suwayomi/Suwayomi-WebUI/pull/404) by @schroda)
- ([r1161](https://github.com/Suwayomi/Suwayomi-WebUI/commit/637118ad7a2562255bad507c4d78dcaa373d133c)) Add new languages to resources ([#402](https://github.com/Suwayomi/Suwayomi-WebUI/pull/402) by @schroda)
- ([r1160](https://github.com/Suwayomi/Suwayomi-WebUI/commit/e061fc348c13e4aa14c54a4726e2093a3881907a)) Translations update from Hosted Weblate ([#396](https://github.com/Suwayomi/Suwayomi-WebUI/pull/396) by @weblate, @cnmorocho, @Wip-Sama, @Becods)
- ([r1159](https://github.com/Suwayomi/Suwayomi-WebUI/commit/31dca431e4bfae922ffa9820dc87f6a55370be7a)) Feature/use vite with swc ([#400](https://github.com/Suwayomi/Suwayomi-WebUI/pull/400) by @schroda)
- ([r1158](https://github.com/Suwayomi/Suwayomi-WebUI/commit/a3d36bdb1719b34bc9fe3f5e6655b421363821f0)) Feature/introduce script to create changelog ([#401](https://github.com/Suwayomi/Suwayomi-WebUI/pull/401) by @schroda)
- ([r1157](https://github.com/Suwayomi/Suwayomi-WebUI/commit/a487e47260317bc5bf69238c114e23d59efd93bb)) Feature/update dependencies ([#399](https://github.com/Suwayomi/Suwayomi-WebUI/pull/399) by @schroda)
- ([r1156](https://github.com/Suwayomi/Suwayomi-WebUI/commit/f42aead32b7815c9bc9d0f01b89435030b60a1fa)) Add ui version to server version mapping file ([#398](https://github.com/Suwayomi/Suwayomi-WebUI/pull/398) by @schroda)
- ([r1155](https://github.com/Suwayomi/Suwayomi-WebUI/commit/e4745ee8123e2fc197768f9a11a2e7d0fda30105)) [ESLint] Fix issues ([#397](https://github.com/Suwayomi/Suwayomi-WebUI/pull/397) by @schroda)
- ([r1154](https://github.com/Suwayomi/Suwayomi-WebUI/commit/04f7831dc49aa44d055fcc5112358f6bef7df821)) Use proper button radius ([#393](https://github.com/Suwayomi/Suwayomi-WebUI/pull/393) by @schroda)
- ([r1153](https://github.com/Suwayomi/Suwayomi-WebUI/commit/83c68513576d60e58f033e08d25771b7feadc227)) Update "react-i18next" to v13.x ([#392](https://github.com/Suwayomi/Suwayomi-WebUI/pull/392) by @schroda)
- ([r1152](https://github.com/Suwayomi/Suwayomi-WebUI/commit/5279339b86f2fc0d1946f0ef53d0842a922117fd)) Feature/update i18next to v23.x ([#391](https://github.com/Suwayomi/Suwayomi-WebUI/pull/391) by @schroda)
- ([r1151](https://github.com/Suwayomi/Suwayomi-WebUI/commit/46ccf20168b9aeb595b0303876fb01e22de90bec)) Update dependencies with non-breaking changes ([#390](https://github.com/Suwayomi/Suwayomi-WebUI/pull/390) by @schroda)
- ([r1150](https://github.com/Suwayomi/Suwayomi-WebUI/commit/09b10cd5abba260741084fad71afdfe976d3e372)) Fix/back button not working without browser history ([#389](https://github.com/Suwayomi/Suwayomi-WebUI/pull/389) by @schroda)
- ([r1149](https://github.com/Suwayomi/Suwayomi-WebUI/commit/1d76e990ae58b27739a3802e42af98c1c6dd4913)) Move "@types/node" to dev-dependencies ([#388](https://github.com/Suwayomi/Suwayomi-WebUI/pull/388) by @schroda)
- ([r1148](https://github.com/Suwayomi/Suwayomi-WebUI/commit/7857645800d6973f52ccfa105957d3900156f14d)) Enable changing include/exclude state of "default" category ([#387](https://github.com/Suwayomi/Suwayomi-WebUI/pull/387) by @schroda)
- ([r1147](https://github.com/Suwayomi/Suwayomi-WebUI/commit/67b4bbbdcf2cc8d133e6f365f6e9cbdb49a8d6cf)) Do not use and mutate global array ([#386](https://github.com/Suwayomi/Suwayomi-WebUI/pull/386) by @schroda)
- ([r1146](https://github.com/Suwayomi/Suwayomi-WebUI/commit/5c0dcf1f6ccd69504d3e8617d6e6eebc976612f9)) Rename function ([#386](https://github.com/Suwayomi/Suwayomi-WebUI/pull/386) by @schroda)
- ([r1145](https://github.com/Suwayomi/Suwayomi-WebUI/commit/084308a8331402e9b1af517ccd263712b74649fb)) Fix typo ([#385](https://github.com/Suwayomi/Suwayomi-WebUI/pull/385) by @schroda)
- ([r1144](https://github.com/Suwayomi/Suwayomi-WebUI/commit/95ca2fabaec4b15255f071ea5b47f984a0d9df28)) Group obsolete extensions ([#385](https://github.com/Suwayomi/Suwayomi-WebUI/pull/385) by @schroda)
- ([r1143](https://github.com/Suwayomi/Suwayomi-WebUI/commit/2bbfb5272fa8eb019f01e5e2300af05ad733b265)) Add new languages to resources ([#384](https://github.com/Suwayomi/Suwayomi-WebUI/pull/384) by @schroda)
- ([r1142](https://github.com/Suwayomi/Suwayomi-WebUI/commit/750d2f246462bb276bdcf6f28822bb2c4dab6771)) Translated using Weblate (Ukrainian) ([#292](https://github.com/Suwayomi/Suwayomi-WebUI/pull/292) by @weblate, @Kefir2105, @RafieHardinur, @SuperMario229, @misaka10843, @schroda, @Becods)
- ([r1141](https://github.com/Suwayomi/Suwayomi-WebUI/commit/5d9bc5474c3f0c7d282bb75ab1dbb2ef38952a09)) Prevent white screen in Updates page ([#383](https://github.com/Suwayomi/Suwayomi-WebUI/pull/383) by @Becods)
- ([r1140](https://github.com/Suwayomi/Suwayomi-WebUI/commit/ed9c51cd37afbb9f84682ff48378f722b847cc15)) Reset scroll position when changing the search term ([#382](https://github.com/Suwayomi/Suwayomi-WebUI/pull/382) by @schroda)
- ([r1139](https://github.com/Suwayomi/Suwayomi-WebUI/commit/322cf8c91588d28c3c7f4a1b52e7144b0e94bd8a)) Move Library "search settings" to LibrarySettings ([#381](https://github.com/Suwayomi/Suwayomi-WebUI/pull/381) by @schroda)
- ([r1138](https://github.com/Suwayomi/Suwayomi-WebUI/commit/f5a8c8d35c113aca2643f3e6e1b54611bb1a8db7)) Update reset scroll position flag after doing the reset ([#380](https://github.com/Suwayomi/Suwayomi-WebUI/pull/380) by @schroda)
- ([r1137](https://github.com/Suwayomi/Suwayomi-WebUI/commit/79b5e696b65b1281496a0ee184171054ad47175f)) Fix/setting buttons unclickable area ([#379](https://github.com/Suwayomi/Suwayomi-WebUI/pull/379) by @schroda)
- ([r1136](https://github.com/Suwayomi/Suwayomi-WebUI/commit/0f029cb625bd69e0806aefcc8ad76c7d84147969)) Fix/library manga grid infinite item size on category switch ([#378](https://github.com/Suwayomi/Suwayomi-WebUI/pull/378) by @schroda)
- ([r1135](https://github.com/Suwayomi/Suwayomi-WebUI/commit/cdacc4b2a96668dd6d954aed367fbf1fcf486680)) Always use available width for grid items ([#375](https://github.com/Suwayomi/Suwayomi-WebUI/pull/375) by @schroda)
- ([r1134](https://github.com/Suwayomi/Suwayomi-WebUI/commit/ad3864161691f21751f5f97e116728d5fa3ebf4b)) Only show scrollbar when necessary ([#377](https://github.com/Suwayomi/Suwayomi-WebUI/pull/377) by @schroda)
- ([r1133](https://github.com/Suwayomi/Suwayomi-WebUI/commit/48d559ec190fc9496c40139187431b7d754dcb4f)) Fix/manga grid infinite item width ([#376](https://github.com/Suwayomi/Suwayomi-WebUI/pull/376) by @schroda)
- ([r1132](https://github.com/Suwayomi/Suwayomi-WebUI/commit/cc423932b05e9375c724ed3aad05affe3db09025)) Properly resolve alias paths in vite ([#374](https://github.com/Suwayomi/Suwayomi-WebUI/pull/374) by @schroda)
- ([r1131](https://github.com/Suwayomi/Suwayomi-WebUI/commit/4dcf6a3ad90dddbecef98f0695ac5a0a3b447df7)) Make library tabs menu position fixed ([#369](https://github.com/Suwayomi/Suwayomi-WebUI/pull/369) by @schroda)
- ([r1130](https://github.com/Suwayomi/Suwayomi-WebUI/commit/19d27fdf2621c7271294486d835124a274bb3d1f)) Feature/virtualize manga grid ([#363](https://github.com/Suwayomi/Suwayomi-WebUI/pull/363) by @schroda)
- ([r1129](https://github.com/Suwayomi/Suwayomi-WebUI/commit/3ad33b0a15d0f5e5c34a83f0db2a1f553d76f5b4)) Reset scroll position when changing searchTerm ([#373](https://github.com/Suwayomi/Suwayomi-WebUI/pull/373) by @schroda)
- ([r1128](https://github.com/Suwayomi/Suwayomi-WebUI/commit/938b5166d9df2f431589d77b08770ad17368390a)) Fix Library tab change animation ([#372](https://github.com/Suwayomi/Suwayomi-WebUI/pull/372) by @schroda)
- ([r1127](https://github.com/Suwayomi/Suwayomi-WebUI/commit/05fa6d8fa952443a013f45c264e33d378efa165d)) Never pass "searchTerm" for "filter" source content type request ([#371](https://github.com/Suwayomi/Suwayomi-WebUI/pull/371) by @schroda)
- ([r1126](https://github.com/Suwayomi/Suwayomi-WebUI/commit/0495a932deda341a6035147a93d2ba1043cc65b8)) Use the "disableCache" flag for the "filters" source content type request ([#370](https://github.com/Suwayomi/Suwayomi-WebUI/pull/370) by @schroda)
- ([r1125](https://github.com/Suwayomi/Suwayomi-WebUI/commit/64eb420ba6d88f85704b3a479b4b309c59dab550)) Use same endpoint for search in SearchAll and SourceMangas ([#368](https://github.com/Suwayomi/Suwayomi-WebUI/pull/368) by @schroda)
- ([r1124](https://github.com/Suwayomi/Suwayomi-WebUI/commit/97c77027f22033ffbcef67a5941c007ea74de381)) Scroll to top when changing source manga content type ([#365](https://github.com/Suwayomi/Suwayomi-WebUI/pull/365) by @schroda)
- ([r1123](https://github.com/Suwayomi/Suwayomi-WebUI/commit/5187d4c8428434c1fc3e5036e1c5e7821856a04d)) Fix "hasNextPage" calculation for Library grid ([#366](https://github.com/Suwayomi/Suwayomi-WebUI/pull/366) by @schroda)
- ([r1122](https://github.com/Suwayomi/Suwayomi-WebUI/commit/cc4bf7bb325f986f39c68fe9fd610dd5051df592)) Fix initial infinite swr request for pages > 1 ([#367](https://github.com/Suwayomi/Suwayomi-WebUI/pull/367) by @schroda)
- ([r1121](https://github.com/Suwayomi/Suwayomi-WebUI/commit/dcd5302a2650b17581dd53df6ab873a2f83cc0c3)) Fix/source mangas white screen when directly open page via url ([#362](https://github.com/Suwayomi/Suwayomi-WebUI/pull/362) by @schroda)
- ([r1120](https://github.com/Suwayomi/Suwayomi-WebUI/commit/26b48f15c5887ac72b585f9fd4dcfe11d1f7b59a)) Fix/library settings global update categories empty dialog after updating ([#361](https://github.com/Suwayomi/Suwayomi-WebUI/pull/361) by @schroda)
- ([r1119](https://github.com/Suwayomi/Suwayomi-WebUI/commit/63c1ac95eb25d7b80e5c7328159d1f896ab42146)) Feature/remove use back to util ([#360](https://github.com/Suwayomi/Suwayomi-WebUI/pull/360) by @schroda)
- ([r1118](https://github.com/Suwayomi/Suwayomi-WebUI/commit/87de016d6f50bf4a819bb539ab06583b66fd48d9)) Prevent chapter revalidation on focus event in the Reader ([#359](https://github.com/Suwayomi/Suwayomi-WebUI/pull/359) by @schroda)
- ([r1117](https://github.com/Suwayomi/Suwayomi-WebUI/commit/bbdbccf235bd54af35b6b0de1de228ef986202ea)) Prevent chapter revalidation on focus event in the Reader (#359) (by @schroda)
- ([r1116](https://github.com/Suwayomi/Suwayomi-WebUI/commit/7f83bb9a0a9413d5b469025d82baf7198e36451f)) Do not use stale chapter data for the reader ([#358](https://github.com/Suwayomi/Suwayomi-WebUI/pull/358) by @schroda)
- ([r1115](https://github.com/Suwayomi/Suwayomi-WebUI/commit/67e554ede6ec3691245ab236bc044466d18f47f6)) Prevent updating "lastPageRead" of chapter to the initial chapters "lastPageRead" ([#357](https://github.com/Suwayomi/Suwayomi-WebUI/pull/357) by @schroda)
- ([r1114](https://github.com/Suwayomi/Suwayomi-WebUI/commit/21174dc04ab392cca5ae00f45f2b75a45f37840f)) Add the option to ignore SWR stale data ([#356](https://github.com/Suwayomi/Suwayomi-WebUI/pull/356) by @schroda)
- ([r1113](https://github.com/Suwayomi/Suwayomi-WebUI/commit/f979d207e87da5dd33ba2a0a4e9e93a190f0f5bb)) Open reader via the correct url when using resume FAB ([#355](https://github.com/Suwayomi/Suwayomi-WebUI/pull/355) by @schroda)
- ([r1112](https://github.com/Suwayomi/Suwayomi-WebUI/commit/cce6ec0113f0d88f02fb748b7c297438f4ce12e5)) Preserve "SourceMangas" location state ([#354](https://github.com/Suwayomi/Suwayomi-WebUI/pull/354) by @schroda)
- ([r1111](https://github.com/Suwayomi/Suwayomi-WebUI/commit/0446a01e85e80cb1ebd3d29d88b20a78dffec748)) Remove unused dependency "web-vitals" ([#353](https://github.com/Suwayomi/Suwayomi-WebUI/pull/353) by @schroda)
- ([r1110](https://github.com/Suwayomi/Suwayomi-WebUI/commit/4b83ccc88902135f1801858c4d189f6ff228a1a7)) Feature/use alias for imports ([#352](https://github.com/Suwayomi/Suwayomi-WebUI/pull/352) by @schroda)
- ([r1109](https://github.com/Suwayomi/Suwayomi-WebUI/commit/1ec6d77598788890beea16245d59b5b65410440e)) Fix/library title size info initial render flickering ([#350](https://github.com/Suwayomi/Suwayomi-WebUI/pull/350) by @schroda)
- ([r1108](https://github.com/Suwayomi/Suwayomi-WebUI/commit/00e403ce09f1514db6064732571cab596e154c43)) Use correct endpoint for deleting downloaded chapter ([#351](https://github.com/Suwayomi/Suwayomi-WebUI/pull/351) by @schroda)
- ([r1107](https://github.com/Suwayomi/Suwayomi-WebUI/commit/bc99728dcd9e7b36c8d3495d80bcbd6b3326f072)) Feature/migrate to vite ([#349](https://github.com/Suwayomi/Suwayomi-WebUI/pull/349) by @schroda)
- ([r1106](https://github.com/Suwayomi/Suwayomi-WebUI/commit/fa614a19d257fd9c993ccedebb7da9f2443f1510)) Update dependency "typescript" to v5.x ([#348](https://github.com/Suwayomi/Suwayomi-WebUI/pull/348) by @schroda)
- ([r1105](https://github.com/Suwayomi/Suwayomi-WebUI/commit/57b1c0f680eb33d3849278beb4e14b7428e58e96)) Update dependency "eslint" to v8.42.0 ([#347](https://github.com/Suwayomi/Suwayomi-WebUI/pull/347) by @schroda)
- ([r1104](https://github.com/Suwayomi/Suwayomi-WebUI/commit/61975b5c4935b6c919f4560a82ba95fdd7d3e1cb)) Feature/update dependency react to v18.x ([#346](https://github.com/Suwayomi/Suwayomi-WebUI/pull/346) by @schroda)
- ([r1103](https://github.com/Suwayomi/Suwayomi-WebUI/commit/62bf6d66dccfe3b26caef0018d6d965488c63657)) Update dependency "react-virtuoso" to v4.x ([#345](https://github.com/Suwayomi/Suwayomi-WebUI/pull/345) by @schroda)
- ([r1102](https://github.com/Suwayomi/Suwayomi-WebUI/commit/f2ac73891ca3c524a568035d0d6d581f88c7c3b1)) Update dependency "file-selector" to v0.6.0 ([#344](https://github.com/Suwayomi/Suwayomi-WebUI/pull/344) by @schroda)
- ([r1101](https://github.com/Suwayomi/Suwayomi-WebUI/commit/c6f7cd7731e5e8b0a3fc7b5916d0f2de760da51b)) Feature/update dependency web vitals to v3.x ([#343](https://github.com/Suwayomi/Suwayomi-WebUI/pull/343) by @schroda)
- ([r1100](https://github.com/Suwayomi/Suwayomi-WebUI/commit/548b746ee42e3f4eff6370d62d909514a5538961)) Update dependency "@fontsource/roboto" to v5.x ([#342](https://github.com/Suwayomi/Suwayomi-WebUI/pull/342) by @schroda)
- ([r1099](https://github.com/Suwayomi/Suwayomi-WebUI/commit/7f4ec7a30c600415fd61acd25f6aea52f68935d9)) Update dependency "@mui/icons-material" to v5.11.16 ([#341](https://github.com/Suwayomi/Suwayomi-WebUI/pull/341) by @schroda)
- ([r1098](https://github.com/Suwayomi/Suwayomi-WebUI/commit/4e8813b526fab197a61e22d9a855514f1f9bd203)) Feature/update dependency react router dom to v6.x ([#340](https://github.com/Suwayomi/Suwayomi-WebUI/pull/340) by @schroda)
- ([r1097](https://github.com/Suwayomi/Suwayomi-WebUI/commit/fd5a1e240a51bf19483546551e6a5509b9ca7d0c)) Remove unused dependency "query-string" ([#339](https://github.com/Suwayomi/Suwayomi-WebUI/pull/339) by @schroda)
- ([r1096](https://github.com/Suwayomi/Suwayomi-WebUI/commit/6b05083d950263897ffbaf72b62de5abf795a2c8)) Feature/update dependency use query params to v2.x ([#338](https://github.com/Suwayomi/Suwayomi-WebUI/pull/338) by @schroda)
- ([r1095](https://github.com/Suwayomi/Suwayomi-WebUI/commit/ce02802888d20d968a6abdfbc18562faa59e0902)) Update dependency "@emotion" to v11.11.0 ([#337](https://github.com/Suwayomi/Suwayomi-WebUI/pull/337) by @schroda)
- ([r1094](https://github.com/Suwayomi/Suwayomi-WebUI/commit/c63b4acdf826c4e8a586e48f2e7c005a1275c2cd)) Update dependency "i18n" to v22.5.0 ([#336](https://github.com/Suwayomi/Suwayomi-WebUI/pull/336) by @schroda)
- ([r1093](https://github.com/Suwayomi/Suwayomi-WebUI/commit/31f2e0f839740dcfae3fc0c475f24b3c98128950)) Update dependency "react-beautiful-dnd" to v13.1.1 ([#335](https://github.com/Suwayomi/Suwayomi-WebUI/pull/335) by @schroda)
- ([r1092](https://github.com/Suwayomi/Suwayomi-WebUI/commit/8655fe1f6dfc7466811db36bcd345842264060a6)) Update dependency "@typescript-eslint" to v5.59.8 ([#334](https://github.com/Suwayomi/Suwayomi-WebUI/pull/334) by @schroda)
- ([r1091](https://github.com/Suwayomi/Suwayomi-WebUI/commit/647260f18c708ff2edd7006e2aecd10658835aff)) Update dependency "prettier" to v2.8.8 ([#333](https://github.com/Suwayomi/Suwayomi-WebUI/pull/333) by @schroda)
- ([r1090](https://github.com/Suwayomi/Suwayomi-WebUI/commit/1ce27ea939db46ad0b53e60901b9aa02ea628011)) Feature/update dependency mui to v5.x ([#332](https://github.com/Suwayomi/Suwayomi-WebUI/pull/332) by @schroda)
- ([r1089](https://github.com/Suwayomi/Suwayomi-WebUI/commit/6911f60eb4fa43b83e48420a4108780072c2d096)) Feature/remove dependency mui system ([#331](https://github.com/Suwayomi/Suwayomi-WebUI/pull/331) by @schroda)
- ([r1088](https://github.com/Suwayomi/Suwayomi-WebUI/commit/2a6950b015f75b070a89132d54adb0e999662101)) Feature/remove dependency mui styles ([#330](https://github.com/Suwayomi/Suwayomi-WebUI/pull/330) by @schroda)
- ([r1087](https://github.com/Suwayomi/Suwayomi-WebUI/commit/3a82fd0abb63b8408435963369a72bb3c7d0815b)) Remove unused dependency "react-lazyload" ([#329](https://github.com/Suwayomi/Suwayomi-WebUI/pull/329) by @schroda)
- ([r1086](https://github.com/Suwayomi/Suwayomi-WebUI/commit/9e8c4254bd4f77feb6e6aea3bb4197fa98c223a0)) Remove unused dependency "p-queue" ([#328](https://github.com/Suwayomi/Suwayomi-WebUI/pull/328) by @schroda)
- ([r1085](https://github.com/Suwayomi/Suwayomi-WebUI/commit/9ae7b2e5922a4fe5a548208e729849df8baa59a3)) Remove console log ([#327](https://github.com/Suwayomi/Suwayomi-WebUI/pull/327) by @schroda)
- ([r1084](https://github.com/Suwayomi/Suwayomi-WebUI/commit/0fb204f1419d4d903c5ac7710e6a4ee3c89b260d)) Feature/request manager remove get client usage ([#325](https://github.com/Suwayomi/Suwayomi-WebUI/pull/325) by @schroda)
- ([r1083](https://github.com/Suwayomi/Suwayomi-WebUI/commit/207a87f3e98875aeef0aad6963c8ccb86ad6a789)) Fix import backup file request ([#326](https://github.com/Suwayomi/Suwayomi-WebUI/pull/326) by @schroda)
- ([r1082](https://github.com/Suwayomi/Suwayomi-WebUI/commit/eaa83927898cf3249d443f42ea4621ef89695cf9)) Fix axios requests ([#324](https://github.com/Suwayomi/Suwayomi-WebUI/pull/324) by @schroda)
- ([r1081](https://github.com/Suwayomi/Suwayomi-WebUI/commit/d60ed0c0558c4d9895ab681143f374959709c42f)) Add fit page to window reader setting ([#323](https://github.com/Suwayomi/Suwayomi-WebUI/pull/323) by @Alexandre-P-J)
- ([r1080](https://github.com/Suwayomi/Suwayomi-WebUI/commit/46a7ff6e3bc28fd4e459920a9640ab04a851ce4d)) Feature/refactor source mangas screen ([#314](https://github.com/Suwayomi/Suwayomi-WebUI/pull/314) by @schroda)
- ([r1079](https://github.com/Suwayomi/Suwayomi-WebUI/commit/1480507c353009d2d7238c97f6eaf09fcfddb3ab)) Fix/source options filters state ([#320](https://github.com/Suwayomi/Suwayomi-WebUI/pull/320) by @schroda)
- ([r1078](https://github.com/Suwayomi/Suwayomi-WebUI/commit/f852255d8260e5f19ac5ba81328371f025c30a7e)) Add additional info about SWR infinite load to response ([#321](https://github.com/Suwayomi/Suwayomi-WebUI/pull/321) by @schroda)
- ([r1077](https://github.com/Suwayomi/Suwayomi-WebUI/commit/606ee9de2d8b99e86264cf95d549ec2244908ebf)) RequestManager make requests abortable - Fix missed usages ([#318](https://github.com/Suwayomi/Suwayomi-WebUI/pull/318) by @schroda)
- ([r1076](https://github.com/Suwayomi/Suwayomi-WebUI/commit/c32cb535920da8ac3653890a76a413e225fc8f29)) Feature/improve refactored global search performance ([#317](https://github.com/Suwayomi/Suwayomi-WebUI/pull/317) by @schroda)
- ([r1075](https://github.com/Suwayomi/Suwayomi-WebUI/commit/0d36d2dcfb41709c3515abbb0f4cf3b6d49115d4)) Feature/request manager make requests abortable ([#316](https://github.com/Suwayomi/Suwayomi-WebUI/pull/316) by @schroda)
- ([r1074](https://github.com/Suwayomi/Suwayomi-WebUI/commit/95ceac335b6749530b2d895f40be715ef278e738)) Update to axios v1.x ([#315](https://github.com/Suwayomi/Suwayomi-WebUI/pull/315) by @schroda)
- ([r1073](https://github.com/Suwayomi/Suwayomi-WebUI/commit/84433ffff826e1faf2a110c4b7c7183a1816179c)) Support SWR infinite requests via POST ([#313](https://github.com/Suwayomi/Suwayomi-WebUI/pull/313) by @schroda)
- ([r1072](https://github.com/Suwayomi/Suwayomi-WebUI/commit/6fd0dd0daddd6f3aa141a35a1d27e796df23f440)) Fix "setSourceFilters" request ([#312](https://github.com/Suwayomi/Suwayomi-WebUI/pull/312) by @schroda)
- ([r1071](https://github.com/Suwayomi/Suwayomi-WebUI/commit/a95937f2536e25ce5b3988477306f31300956b3f)) Fix/global search not showing request error ([#310](https://github.com/Suwayomi/Suwayomi-WebUI/pull/310) by @schroda)
- ([r1070](https://github.com/Suwayomi/Suwayomi-WebUI/commit/474e568a05a1f58846323e94f9d821dc440ed36e)) Refactor SearchAll screen ([#308](https://github.com/Suwayomi/Suwayomi-WebUI/pull/308) by @schroda)
- ([r1069](https://github.com/Suwayomi/Suwayomi-WebUI/commit/836b4ea4d21f4b6976ee2678863b3e683326bcb9)) Set the manga ref for the "list style" ([#311](https://github.com/Suwayomi/Suwayomi-WebUI/pull/311) by @schroda)
- ([r1068](https://github.com/Suwayomi/Suwayomi-WebUI/commit/f908195b0982d12c498b019cb956c91ded07dc2b)) Feature/cleanup search all ([#307](https://github.com/Suwayomi/Suwayomi-WebUI/pull/307) by @schroda)
- ([r1067](https://github.com/Suwayomi/Suwayomi-WebUI/commit/0cd5720f54d06d70e15a57afa1ee1f28d52ae5da)) Fix/request manager infinite swr requests ([#306](https://github.com/Suwayomi/Suwayomi-WebUI/pull/306) by @schroda)
- ([r1066](https://github.com/Suwayomi/Suwayomi-WebUI/commit/feac34ba83028b59798d69f55f4cd03b7fc13d16)) Trigger global search request ([#305](https://github.com/Suwayomi/Suwayomi-WebUI/pull/305) by @schroda)
- ([r1065](https://github.com/Suwayomi/Suwayomi-WebUI/commit/aa801a57ee61fb52a544ed77fdfddfe4c9843ffe)) Feature/updates screen use infinite swr hook ([#303](https://github.com/Suwayomi/Suwayomi-WebUI/pull/303) by @schroda)
- ([r1064](https://github.com/Suwayomi/Suwayomi-WebUI/commit/a457d80c446960df5644513fdf4d3a11974243c7)) Feature/streamline backend requests ([#297](https://github.com/Suwayomi/Suwayomi-WebUI/pull/297) by @schroda)
- ([r1063](https://github.com/Suwayomi/Suwayomi-WebUI/commit/f86c7ea08ae0c6a40e628f0d92f8d7f31537c668)) Prevent showing "empty library" message on first load ([#302](https://github.com/Suwayomi/Suwayomi-WebUI/pull/302) by @schroda)
- ([r1062](https://github.com/Suwayomi/Suwayomi-WebUI/commit/58eb2fead3e8bed2b42843293882b5e9d1563ae3)) Feature/enforce license notice in each file via eslint rule ([#304](https://github.com/Suwayomi/Suwayomi-WebUI/pull/304) by @schroda)
- ([r1061](https://github.com/Suwayomi/Suwayomi-WebUI/commit/13dbb8faf4f3353cc342c91cbebb557d83ebf2ab)) Prevent add category FAB from overlaying last category ([#301](https://github.com/Suwayomi/Suwayomi-WebUI/pull/301) by @schroda)
- ([r1060](https://github.com/Suwayomi/Suwayomi-WebUI/commit/14ac872876922128de3e141d1f3c05b5d11da7ad)) Prevent manga page FAB from changing position when selecting chapters ([#300](https://github.com/Suwayomi/Suwayomi-WebUI/pull/300) by @schroda)
- ([r1059](https://github.com/Suwayomi/Suwayomi-WebUI/commit/0a0d902bd28d5eb6b99ef56c53c6abd69151dc37)) Fix/manga screen prevent fab from overlaying last chapter in list ([#298](https://github.com/Suwayomi/Suwayomi-WebUI/pull/298) by @schroda)
- ([r1058](https://github.com/Suwayomi/Suwayomi-WebUI/commit/5aaf0854fea41c4a18e66f53140946fa3e70694c)) Fix/download queue staying stopped when removing download ([#299](https://github.com/Suwayomi/Suwayomi-WebUI/pull/299) by @schroda)
- ([r1057](https://github.com/Suwayomi/Suwayomi-WebUI/commit/8dae72604f8417e4afddc634acfddb4a0f8a8197)) Update to SWR version 2.x ([#296](https://github.com/Suwayomi/Suwayomi-WebUI/pull/296) by @schroda)
- ([r1056](https://github.com/Suwayomi/Suwayomi-WebUI/commit/4aff22079a136c2dc48a34fdfa7e34b17ddfea9b)) Settings language add description ([#294](https://github.com/Suwayomi/Suwayomi-WebUI/pull/294) by @schroda)
- ([r1055](https://github.com/Suwayomi/Suwayomi-WebUI/commit/c8b0c64a8d53808e104672f2b86538bc5ce46e0b)) Add new languages to resources ([#293](https://github.com/Suwayomi/Suwayomi-WebUI/pull/293) by @schroda)
- ([r1054](https://github.com/Suwayomi/Suwayomi-WebUI/commit/b6c87dd630554325e80e2369f10c1b2e7171d18d)) Translations update from Hosted Weblate ([#276](https://github.com/Suwayomi/Suwayomi-WebUI/pull/276) by @weblate, @AriaMoradi, @NathanBnm, @misaka10843, @FumoVite, @JoHena, @bandysharif, @DevCoz)
- ([r1053](https://github.com/Suwayomi/Suwayomi-WebUI/commit/b4b3dc54a7f09ca95e29aab5a277032afc625f7f)) App strings reworked ([#277](https://github.com/Suwayomi/Suwayomi-WebUI/pull/277) by @comradekingu, @schroda)
- ([r1052](https://github.com/Suwayomi/Suwayomi-WebUI/commit/bd91510227d36d4a15a1feddd681b0105bc37ca6)) Remove eslint rule deactivations ([#290](https://github.com/Suwayomi/Suwayomi-WebUI/pull/290) by @schroda)
- ([r1051](https://github.com/Suwayomi/Suwayomi-WebUI/commit/d92dc83ddaca8a7ac45b781761ea7c0ce0ded123)) Display strings in uppercase ([#291](https://github.com/Suwayomi/Suwayomi-WebUI/pull/291) by @schroda)
- ([r1050](https://github.com/Suwayomi/Suwayomi-WebUI/commit/2e05e7d35c0c25c0a1d134f146ebf8ec645a6ed0)) fix/manga_screen_missing_source_toast ([#288](https://github.com/Suwayomi/Suwayomi-WebUI/pull/288) by @schroda)
- ([r1049](https://github.com/Suwayomi/Suwayomi-WebUI/commit/548b22d21151390fbfb599a977d7d2cbb4a732f6)) fix/library_settings_screen_title_update_on_language_change ([#287](https://github.com/Suwayomi/Suwayomi-WebUI/pull/287) by @schroda)
- ([r1048](https://github.com/Suwayomi/Suwayomi-WebUI/commit/b906509f9f7cc9eec8ce5ed7b474a2aae8a5d718)) Add missing licence text ([#286](https://github.com/Suwayomi/Suwayomi-WebUI/pull/286) by @schroda)
- ([r1047](https://github.com/Suwayomi/Suwayomi-WebUI/commit/f5a961e82fb6ddb7b79b3789eaa9102b7af83dd7)) Fix/manga white screen missing extension ([#285](https://github.com/Suwayomi/Suwayomi-WebUI/pull/285) by @schroda)
- ([r1046](https://github.com/Suwayomi/Suwayomi-WebUI/commit/9a27335760a049e53acb9347d78690e7967ccdc5)) Add option to include and exclude categories from the global update ([#265](https://github.com/Suwayomi/Suwayomi-WebUI/pull/265) by @schroda)
- ([r1045](https://github.com/Suwayomi/Suwayomi-WebUI/commit/79b2305e540fa85346d47b598d05bd12004b3742)) Feature/library show number of mangas in category ([#269](https://github.com/Suwayomi/Suwayomi-WebUI/pull/269) by @schroda)
- ([r1044](https://github.com/Suwayomi/Suwayomi-WebUI/commit/4157611d83bd6143a204f4226c719d5b46280dc5)) Feature/improve typing of metadata related logic ([#268](https://github.com/Suwayomi/Suwayomi-WebUI/pull/268) by @schroda)
- ([r1043](https://github.com/Suwayomi/Suwayomi-WebUI/commit/0a56a2f6d48b13bd414b5e4a5327f41b7a0a11f8)) Extensions cleanup ([#257](https://github.com/Suwayomi/Suwayomi-WebUI/pull/257) by @schroda)
- ([r1042](https://github.com/Suwayomi/Suwayomi-WebUI/commit/c81189a9ed35c1144466aa15fea0fcd14f5f464a)) Fix/chapter mark as unread not resetting last page read ([#282](https://github.com/Suwayomi/Suwayomi-WebUI/pull/282) by @schroda)
- ([r1041](https://github.com/Suwayomi/Suwayomi-WebUI/commit/22b3437dcdbad22bffe49955187dfda2e606e874)) Keep "add category" fab position fixed ([#283](https://github.com/Suwayomi/Suwayomi-WebUI/pull/283) by @schroda)
- ([r1040](https://github.com/Suwayomi/Suwayomi-WebUI/commit/d51150b7848cf7a6596bbba7c015328a578dfd16)) Feature/reader skip duplicate chapters ([#262](https://github.com/Suwayomi/Suwayomi-WebUI/pull/262) by @schroda)
- ([r1039](https://github.com/Suwayomi/Suwayomi-WebUI/commit/b1dc13cd30bfdc374123019a9fc51c07e5824633)) Update browser and nav bar title on language change ([#275](https://github.com/Suwayomi/Suwayomi-WebUI/pull/275) by @schroda)
- ([r1038](https://github.com/Suwayomi/Suwayomi-WebUI/commit/92506ccf78ecdfe11fbc71b95ca99267b6c0b621)) Revert "Translated using Weblate (Portuguese)" (by @AriaMoradi)
- ([r1037](https://github.com/Suwayomi/Suwayomi-WebUI/commit/31d3656697c6719de62b0bc424c6f57bdcadbbd3)) Revert "Translated using Weblate (German)" (by @AriaMoradi)
- ([r1036](https://github.com/Suwayomi/Suwayomi-WebUI/commit/14bdb6d9ac6c6766cb02a9eac8ff4aa75a46752e)) Revert "Translated using Weblate (Arabic)" (by @AriaMoradi)
- ([r1035](https://github.com/Suwayomi/Suwayomi-WebUI/commit/2e14b71d18e10f09bc4f9748b7e809eddeaa93a4)) Revert "Translated using Weblate (Spanish)" (by @AriaMoradi)
- ([r1034](https://github.com/Suwayomi/Suwayomi-WebUI/commit/ff79d9e964a8b7e236d65956926f00404baf3bb1)) Revert "Translated using Weblate (French)" (by @AriaMoradi)
- ([r1033](https://github.com/Suwayomi/Suwayomi-WebUI/commit/2050875d6ffc5f8f74be565bf7812d4a79009069)) Merge pull request #274 from weblate/weblate-suwayomi-tachidesk-webui ([#274](https://github.com/Suwayomi/Suwayomi-WebUI/pull/274) by @AriaMoradi)
- ([r1032](https://github.com/Suwayomi/Suwayomi-WebUI/commit/d04d33c658ef885a5b050ed17ab192bc96948cba)) Translated using Weblate (French) ([#274](https://github.com/Suwayomi/Suwayomi-WebUI/pull/274) by @weblate)
- ([r1031](https://github.com/Suwayomi/Suwayomi-WebUI/commit/f9e5e6cf7a04767705095903b31cc46df0cd8c93)) Translated using Weblate (Spanish) ([#274](https://github.com/Suwayomi/Suwayomi-WebUI/pull/274) by @weblate)
- ([r1030](https://github.com/Suwayomi/Suwayomi-WebUI/commit/c34e55ce80a8e98be047cbeae31df6036d79784f)) Translated using Weblate (Arabic) ([#274](https://github.com/Suwayomi/Suwayomi-WebUI/pull/274) by @weblate)
- ([r1029](https://github.com/Suwayomi/Suwayomi-WebUI/commit/fe5edb1c4b2a1af27a185021ff2d7f09abcd144f)) Translated using Weblate (German) ([#274](https://github.com/Suwayomi/Suwayomi-WebUI/pull/274) by @weblate)
- ([r1028](https://github.com/Suwayomi/Suwayomi-WebUI/commit/14d23c3d29050a843a8a2304bd4030c37d269d69)) Translated using Weblate (Portuguese) ([#274](https://github.com/Suwayomi/Suwayomi-WebUI/pull/274) by @weblate)
- ([r1027](https://github.com/Suwayomi/Suwayomi-WebUI/commit/43f367a5460a819b685c7429675d4d5eecd748a5)) Merge remote-tracking branch 'origin/master' ([#274](https://github.com/Suwayomi/Suwayomi-WebUI/pull/274) by @weblate)
- ([r1026](https://github.com/Suwayomi/Suwayomi-WebUI/commit/07c0e83f8fbf7a01f46dc19a9858ee78e867fa49)) fix translation files ([#273](https://github.com/Suwayomi/Suwayomi-WebUI/pull/273) by @AriaMoradi)
- ([r1025](https://github.com/Suwayomi/Suwayomi-WebUI/commit/6f75837b81e078577c49ad67f31cb5501aa8b9ef)) Translations update from Hosted Weblate ([#272](https://github.com/Suwayomi/Suwayomi-WebUI/pull/272) by @weblate, @AriaMoradi)
- ([r1024](https://github.com/Suwayomi/Suwayomi-WebUI/commit/8497a0b12e4ce0d5c7f3d435f4ce3654fcd38a41)) add trnalation policy (by @AriaMoradi)
- ([r1023](https://github.com/Suwayomi/Suwayomi-WebUI/commit/aedbccf83e3f6e9e00fe0070b0ff964d483a3132)) Translated using Weblate (German) ([#272](https://github.com/Suwayomi/Suwayomi-WebUI/pull/272) by @AriaMoradi)
- ([r1022](https://github.com/Suwayomi/Suwayomi-WebUI/commit/8fcbe29031bd4bd57ca965b4b3ce5723038623f3)) Added translation using Weblate (German) ([#272](https://github.com/Suwayomi/Suwayomi-WebUI/pull/272) by @AriaMoradi)
- ([r1021](https://github.com/Suwayomi/Suwayomi-WebUI/commit/ffe0d5160df0293454ef98c683165c07ba7875e2)) Translations update from Hosted Weblate ([#270](https://github.com/Suwayomi/Suwayomi-WebUI/pull/270) by @weblate, @comradekingu, @AriaMoradi, @Zereef)
- ([r1020](https://github.com/Suwayomi/Suwayomi-WebUI/commit/fdb44bb46bca1b5f9c4462754716ef61e05f5c66)) Added translation using Weblate (Portuguese) (by @Zereef)
- ([r1019](https://github.com/Suwayomi/Suwayomi-WebUI/commit/42b80b439b242ac9269bc58d117b348b9e5f009e)) Translated using Weblate (German) (by @J. Lavoie)
- ([r1018](https://github.com/Suwayomi/Suwayomi-WebUI/commit/c11a057a420822206bc5c99398bfd0c4b95ae8a8)) Added translation using Weblate (French) (by @J. Lavoie)
- ([r1017](https://github.com/Suwayomi/Suwayomi-WebUI/commit/f71a29fa93d0155f97157d0407d18597ca2828b4)) Translated using Weblate (Arabic) (by @Shippo)
- ([r1016](https://github.com/Suwayomi/Suwayomi-WebUI/commit/dce6f5259ffba2fd73b52a2d32d8c00f38599334)) Added translation using Weblate (Spanish) (by @PedroJLR)
- ([r1015](https://github.com/Suwayomi/Suwayomi-WebUI/commit/ce06220b9c2c78863196d5c396c76a979856e11f)) Translated using Weblate (German) (by @AriaMoradi)
- ([r1014](https://github.com/Suwayomi/Suwayomi-WebUI/commit/75e021588127f03270acd53ed5754c66222b89a7)) Added translation using Weblate (Arabic) (by @Shippo)
- ([r1013](https://github.com/Suwayomi/Suwayomi-WebUI/commit/70511aa1c8fc6f650293f4cfcb88e6c9785fba68)) Added translation using Weblate (German) (by @AriaMoradi)
- ([r1012](https://github.com/Suwayomi/Suwayomi-WebUI/commit/52b08cfeb11be6e3301cdfc4808722b8cf6234d0)) Deleted translation using Weblate (Norwegian Bokmål) (by @AriaMoradi)
- ([r1011](https://github.com/Suwayomi/Suwayomi-WebUI/commit/226a0cc249e6e0bacb28120d6885738f3bf5a5d1)) Translated using Weblate (Norwegian Bokmål) (by @comradekingu)
- ([r1010](https://github.com/Suwayomi/Suwayomi-WebUI/commit/9fef2bf488ef7466046d384fdf8e6504c603e68a)) Translated using Weblate (French) (by @AriaMoradi)
- ([r1009](https://github.com/Suwayomi/Suwayomi-WebUI/commit/0126cd266b8c55893469c34f14dad102961f8742)) Added translation using Weblate (Norwegian Bokmål) (by @comradekingu)
- ([r1008](https://github.com/Suwayomi/Suwayomi-WebUI/commit/16c0f854fe7fd5911f4f14c4ea9cebe29ba59e9c)) Fix discord and github links ([#267](https://github.com/Suwayomi/Suwayomi-WebUI/pull/267) by @JoHena)
- ([r1007](https://github.com/Suwayomi/Suwayomi-WebUI/commit/39780df0e0504601d0ba1250ddea7ee6155a895b)) add language selection to settings ([#260](https://github.com/Suwayomi/Suwayomi-WebUI/pull/260) by @schroda)
- ([r1006](https://github.com/Suwayomi/Suwayomi-WebUI/commit/d58bd6cd92a74faeaa13a7713efb11e838d73fc2)) add translation keys ([#246](https://github.com/Suwayomi/Suwayomi-WebUI/pull/246) by @schroda)
- ([r1005](https://github.com/Suwayomi/Suwayomi-WebUI/commit/8c129f2e08e8c807b57c7eef802556faa2edcf1b)) Disable "SSR" option in "useMediaQuery" ([#263](https://github.com/Suwayomi/Suwayomi-WebUI/pull/263) by @schroda)
- ([r1004](https://github.com/Suwayomi/Suwayomi-WebUI/commit/8cd1afd2c725e015f8480f51eb0a2965585053d2)) Ignore filters only while searching ([#256](https://github.com/Suwayomi/Suwayomi-WebUI/pull/256) by @schroda)
- ([r1003](https://github.com/Suwayomi/Suwayomi-WebUI/commit/57c8ee2cdc1f6e52a26ed436d0ac438fa390b218)) Add build step to pr workflow ([#259](https://github.com/Suwayomi/Suwayomi-WebUI/pull/259) by @schroda)
- ([r1002](https://github.com/Suwayomi/Suwayomi-WebUI/commit/398626250e71b85029288167ab47f25f297c0914)) Remove console log ([#258](https://github.com/Suwayomi/Suwayomi-WebUI/pull/258) by @schroda)
- ([r1001](https://github.com/Suwayomi/Suwayomi-WebUI/commit/23001a42989f0231e946bd6251b45c6eeda1818e)) update react scripts dependency ([#255](https://github.com/Suwayomi/Suwayomi-WebUI/pull/255) by @schroda)
- ([r1000](https://github.com/Suwayomi/Suwayomi-WebUI/commit/91e7bd2b27beae0c83b8158757600cf396694381)) Added sort by last read ([#254](https://github.com/Suwayomi/Suwayomi-WebUI/pull/254) by @akabhirav)
- ([r999](https://github.com/Suwayomi/Suwayomi-WebUI/commit/e32078917d82991ecdb70f4a9c23e941ff147a23)) Replace Sort by ID with Date Added ([#253](https://github.com/Suwayomi/Suwayomi-WebUI/pull/253) by @akabhirav)
- ([r998](https://github.com/Suwayomi/Suwayomi-WebUI/commit/a67370be62ee4b47c0ad78f338998c82bbf11218)) Introduce override filters while searching setting ([#242](https://github.com/Suwayomi/Suwayomi-WebUI/pull/242) by @akabhirav)
- ([r997](https://github.com/Suwayomi/Suwayomi-WebUI/commit/dcc18bba0083b684190992e252ed1c6e4dc4203d)) extension card cleanup ([#252](https://github.com/Suwayomi/Suwayomi-WebUI/pull/252) by @schroda)
- ([r996](https://github.com/Suwayomi/Suwayomi-WebUI/commit/96254365182cf02425c17649dece4bb7554f9985)) get first unread chapter from original chapter list ([#250](https://github.com/Suwayomi/Suwayomi-WebUI/pull/250) by @schroda)
- ([r995](https://github.com/Suwayomi/Suwayomi-WebUI/commit/c6257acdf11370109a7fca93be5efc536103fa3d)) Show empty library in case search doesn't match anything ([#251](https://github.com/Suwayomi/Suwayomi-WebUI/pull/251) by @schroda)
- ([r994](https://github.com/Suwayomi/Suwayomi-WebUI/commit/96fd1cf73ba0ad18158e9ff99f9dca9944ea1ff4)) remove manually created typing d ts file ([#249](https://github.com/Suwayomi/Suwayomi-WebUI/pull/249) by @schroda)
- ([r993](https://github.com/Suwayomi/Suwayomi-WebUI/commit/1914a61eddc1033eabd2844fd80b8909cf30b6fe)) Add GitHub Action to run tsc on pull request events ([#232](https://github.com/Suwayomi/Suwayomi-WebUI/pull/232) by @schroda)
- ([r992](https://github.com/Suwayomi/Suwayomi-WebUI/commit/56fe90d0499fa674b6833d3ccd99a8b17bd7a0f0)) Translations update from Hosted Weblate ([#241](https://github.com/Suwayomi/Suwayomi-WebUI/pull/241) by @weblate, @comradekingu, @AriaMoradi)
- ([r991](https://github.com/Suwayomi/Suwayomi-WebUI/commit/cd1d24ada7a81f5df43235c310eb90d10eb44103)) Add logic to migrate metadata values ([#227](https://github.com/Suwayomi/Suwayomi-WebUI/pull/227) by @schroda)
- ([r990](https://github.com/Suwayomi/Suwayomi-WebUI/commit/5473d14eaeb8d21aea36f90f2f28833c0cd2817e)) Adds search by genre to WebUI ([#238](https://github.com/Suwayomi/Suwayomi-WebUI/pull/238) by @akabhirav)
- ([r989](https://github.com/Suwayomi/Suwayomi-WebUI/commit/94e45c21333be735cd3e2d76815db4b1962958c2)) add translation keys (by @AriaMoradi)
- ([r988](https://github.com/Suwayomi/Suwayomi-WebUI/commit/688358f67391cadbbb9246f2cf3dc2cfffe9f21d)) add translation keys (by @AriaMoradi)
- ([r987](https://github.com/Suwayomi/Suwayomi-WebUI/commit/76d44bd657a11357d0617f317628ab5dbaf0d0fa)) clean up translations (by @AriaMoradi)
- ([r986](https://github.com/Suwayomi/Suwayomi-WebUI/commit/6f3bc1bc4edac94a4828ca58b2388e51382870b2)) add translation notice (by @AriaMoradi)
- ([r985](https://github.com/Suwayomi/Suwayomi-WebUI/commit/ce839145993ab21d4b42e2245232774205a9d3d2)) add translation files (by @AriaMoradi)
- ([r984](https://github.com/Suwayomi/Suwayomi-WebUI/commit/1c7c3e566c780ae457d376b525ffb8613a903110)) add i18n (#239) (by @AriaMoradi)


# Server: v0.7.0 + WebUI: r983
## TL;DR
- CBZ downloads support
- Webview implementation based on Microsoft playwright, disabled for this release
- Fixed compatibility with some chinese extensions
- Support for Tachiyomi extensions lib 1.4
- WebUI changes:
    - Uhh, idk, find out yourself...

## Suwayomi-Server Changelog
- (r1159) v0.6.6 (by @AriaMoradi)
- (r1160) add Chagelog TL;DR (by @AriaMoradi)
- (r1161) fix Changelog typos (by @AriaMoradi)
- (r1162) WebView based cloudflare interceptor ([#456](https://github.com/Suwayomi/Suwayomi-Server/pull/456) by @AriaMoradi)
- (r1163) update issue mod (by @AriaMoradi)
- (r1164) better description (by @AriaMoradi)
- (r1165) fix regex (by @AriaMoradi)
- (r1166) get default User Agent from WebView ([#457](https://github.com/Suwayomi/Suwayomi-Server/pull/457) by @AriaMoradi)
- (r1167) implementation of android.graphics.BitmapFactory ([#460](https://github.com/Suwayomi/Suwayomi-Server/pull/460) by @animeavi)
- (r1168) Basic android.graphics Rect and Canvas implementation ([#461](https://github.com/Suwayomi/Suwayomi-Server/pull/461) by @animeavi)
- (r1169) Get Playwright working ([#462](https://github.com/Suwayomi/Suwayomi-Server/pull/462) by @Syer10)
- (r1170) disable deb release (by @AriaMoradi)
- (r1171) Fix debian release ([#463](https://github.com/Suwayomi/Suwayomi-Server/pull/463) by @mahor1221)
- (r1172) Add better manga thumbnail handling ([#465](https://github.com/Suwayomi/Suwayomi-Server/pull/465) by @Syer10)
- (r1173) Use extension list fallback if extensions fail to fetch ([#469](https://github.com/Suwayomi/Suwayomi-Server/pull/469) by @Syer10)
- (r1174) fix when playwright fails on providing a UA (by @AriaMoradi)
- (r1175) Update CategoryMetaTable.kt (by @AriaMoradi)
- (r1176) fix CategoryMetaTable reference to CategoryTable ([#473](https://github.com/Suwayomi/Suwayomi-Server/pull/473) by @AriaMoradi)
- (r1177) remove possibly misleading sentence (by @AriaMoradi)
- (r1178) Clarify and Update (by @AriaMoradi)
- (r1179) Clarify and Update (by @AriaMoradi)
- (r1180) link to Tachiyomi section (by @AriaMoradi)
- (r1181) fix typo (by @AriaMoradi)
- (r1182) Improve Gradle Configuration ([#478](https://github.com/Suwayomi/Suwayomi-Server/pull/478) by @Syer10)
- (r1183) Improve Playwright handling ([#479](https://github.com/Suwayomi/Suwayomi-Server/pull/479) by @Syer10)
- (r1184) fix ambiguous reference issue on JDK 13+ (by @AriaMoradi)
- (r1185) update gradle version (by @AriaMoradi)
- (r1186) upgrade dorkbox stuff (by @AriaMoradi)
- (r1187) Fixe Dex2Jar and dorkbox dependency issues ([#487](https://github.com/Suwayomi/Suwayomi-Server/pull/487) by @akabhirav)
- (r1188) Fix logging and update system try ([#488](https://github.com/Suwayomi/Suwayomi-Server/pull/488) by @Syer10)
- (r1189) add support for Extensions Lib 1.4 ([#496](https://github.com/Suwayomi/Suwayomi-Server/pull/496) by @Syer10)
- (r1190) disable playwright for v0.6.7 (by @AriaMoradi)
- (r1191) Decouple Cache and Download behaviour ([#493](https://github.com/Suwayomi/Suwayomi-Server/pull/493) by @akabhirav)
- (r1192) rethink image cache ([#498](https://github.com/Suwayomi/Suwayomi-Server/pull/498) by @AriaMoradi)
- (r1193) fix Page index issues for some providers ([#491](https://github.com/Suwayomi/Suwayomi-Server/pull/491) by @akabhirav)
- (r1194) Download as CBZ ([#490](https://github.com/Suwayomi/Suwayomi-Server/pull/490) by @akabhirav)
- (r1195) re-order config options (by @AriaMoradi)
- (r1196) stop using depricated API (by @AriaMoradi)

## Suwayomi-WebUI Changelog
- (r964) Created a GridLayout enum and updated all locations to use it. ([#208](https://github.com/Suwayomi/Suwayomi-WebUI/pull/208) by @infix)
- (r965) fix library update progress rendering ([#210](https://github.com/Suwayomi/Suwayomi-WebUI/pull/210) by @schroda)
- (r966) Save reader settings per manga in Meta ([#216](https://github.com/Suwayomi/Suwayomi-WebUI/pull/216) by @schroda)
- (r967) make default reader settings changeable ([#217](https://github.com/Suwayomi/Suwayomi-WebUI/pull/217) by @schroda)
- (r968) [#211] Refresh Library after a update ([#212](https://github.com/Suwayomi/Suwayomi-WebUI/pull/212) by @schroda)
- (r969) add logic for metadata migration ([#218](https://github.com/Suwayomi/Suwayomi-WebUI/pull/218) by @schroda)
- (r970) set browser tab title ([#220](https://github.com/Suwayomi/Suwayomi-WebUI/pull/220) by @schroda)
- (r971) Add tooltip containing full manga title to title of manga ([#221](https://github.com/Suwayomi/Suwayomi-WebUI/pull/221) by @schroda)
- (r972) show more detailed upload dates for today and yesterday ([#222](https://github.com/Suwayomi/Suwayomi-WebUI/pull/222) by @schroda)
- (r973) add GitHub action on pushing to run lint ([#224](https://github.com/Suwayomi/Suwayomi-WebUI/pull/224) by @schroda)
- (r974) Ignore filters while searching ([#226](https://github.com/Suwayomi/Suwayomi-WebUI/pull/226) by @schroda)
- (r975) force absolute import path ([#223](https://github.com/Suwayomi/Suwayomi-WebUI/pull/223) by @schroda)
- (r976) add prettier for auto formatting ([#231](https://github.com/Suwayomi/Suwayomi-WebUI/pull/231) by @schroda)
- (r977) Fix import path ([#228](https://github.com/Suwayomi/Suwayomi-WebUI/pull/228) by @schroda)
- (r978) increase prettier line length to 120 ([#233](https://github.com/Suwayomi/Suwayomi-WebUI/pull/233) by @schroda)
- (r979) Add chapter page dropdown ([#230](https://github.com/Suwayomi/Suwayomi-WebUI/pull/230) by @schroda)
- (r980) add chapter dropdown to reader nav bar  ([#229](https://github.com/Suwayomi/Suwayomi-WebUI/pull/229) by @schroda)
- (r981) Fix lint error ([#235](https://github.com/Suwayomi/Suwayomi-WebUI/pull/235) by @schroda)
- (r982) Fix reader nav bar scroll to page ([#236](https://github.com/Suwayomi/Suwayomi-WebUI/pull/236) by @schroda)
- (r964) Created a GridLayout enum and updated all locations to use it. ([#208](https://github.com/Suwayomi/Suwayomi-WebUI/pull/208) by @infix)



# Server: v0.6.6 + WebUI: r963
## TL;DR
- Batch actions for chapters
- Improved the downloader
- WebUI changes:
    - Support for chapter actions
    - a lot of code cleanup
    - some bugfixes

## Suwayomi-Server Changelog
- (r1114) fix broken links (by @AriaMoradi)
- (r1115) fix more broken stuff (by @AriaMoradi)
- (r1116) fix more broken stuff (by @AriaMoradi)
- (r1117) fix more broken stuff (by @AriaMoradi)
- (r1118) Update winget.yml ([#393](https://github.com/Suwayomi/Suwayomi-Server/pull/393) by @vedantmgoyal2009)
- (r1119) fix jre path([#396](https://github.com/Suwayomi/Suwayomi-Server/pull/396) by @vedantmgoyal2009)
- (r1120) Fix deb package ([#397](https://github.com/Suwayomi/Suwayomi-Server/pull/397) by @mahor1221)
- (r1121) bump version (by @AriaMoradi)
- (r1122) Update Changelog (by @AriaMoradi)
- (r1123) Add libc++-dev ([#405](https://github.com/Suwayomi/Suwayomi-Server/pull/405) by @mahor1221)
- (r1124) Revert back to correct way of handling jre_dir ([#408](https://github.com/Suwayomi/Suwayomi-Server/pull/408) by @mahor1221)
- (r1125) Update winget.yml ([#410](https://github.com/Suwayomi/Suwayomi-Server/pull/410) by @vedantmgoyal2009)
- (r1126) Remove support for Sorayomi web interface ([#414](https://github.com/Suwayomi/Suwayomi-Server/pull/414) by @marcoebbinghaus)
- (r1127) Fix downloader memory leak ([#418](https://github.com/Suwayomi/Suwayomi-Server/pull/418) by @Syer10)
- (r1128) Documentation cleanup ([#417](https://github.com/Suwayomi/Suwayomi-Server/pull/417) by @Syer10)
- (r1129) Updater cleanup and improvements ([#416](https://github.com/Suwayomi/Suwayomi-Server/pull/416) by @Syer10)
- (r1130) replace quickjs with Mozilla Rhino ([#415](https://github.com/Suwayomi/Suwayomi-Server/pull/415) by @xhzhe)
- (r1131) ktlint (by @AriaMoradi)
- (r1132) move Tachiyomi's BuildConfig to kotlin dir (by @AriaMoradi)
- (r1133) remove BuildConfig as extensions now use AppInfo (by @AriaMoradi)
- (r1134) include list of mangas missing source in restore report ([#421](https://github.com/Suwayomi/Suwayomi-Server/pull/421) by @AriaMoradi)
- (r1135) Update dependencies ([#422](https://github.com/Suwayomi/Suwayomi-Server/pull/422) by @Syer10)
- (r1136) Lint ([#423](https://github.com/Suwayomi/Suwayomi-Server/pull/423) by @Syer10)
- (r1137) Fix: Error handling for popular/latest api if pageNum was supplied as zero ([#424](https://github.com/Suwayomi/Suwayomi-Server/pull/424) by @meta-boy)
- (r1138) Add cache control header to manga page response ([#430](https://github.com/Suwayomi/Suwayomi-Server/pull/430) by @martinek)
- (r1139) add MangaTable.lastFetchedAt and ChapterTable.chaptersLastFetchedAt ([#431](https://github.com/Suwayomi/Suwayomi-Server/pull/431) by @martinek)
- (r1140) Pre-load meta entries for all chapters for optimization ([#432](https://github.com/Suwayomi/Suwayomi-Server/pull/432) by @martinek)
- (r1141) POST variant for `/{sourceId}/search` endpoint ([#434](https://github.com/Suwayomi/Suwayomi-Server/pull/434) by @martinek)
- (r1142) Add request body to documentation ([#435](https://github.com/Suwayomi/Suwayomi-Server/pull/435) by @Syer10)
- (r1143) add batch download api ([#436](https://github.com/Suwayomi/Suwayomi-Server/pull/436) by @martinek)
- (r1144) Migrate to H2 v2 (by @AriaMoradi)
- (r1145) add category and global meta ([#438](https://github.com/Suwayomi/Suwayomi-Server/pull/438) by @AriaMoradi)
- (r1146) Revert H2 database to v1 (by @AriaMoradi)
- (r1147) refactor deprecated api (by @AriaMoradi)
- (r1148) Downloader Rewrite ([#437](https://github.com/Suwayomi/Suwayomi-Server/pull/437) by @Syer10)
- (r1149) Set source preference doc fix ([#441](https://github.com/Suwayomi/Suwayomi-Server/pull/441) by @Syer10)
- (r1150) Add batch chapter update endpoint ([#442](https://github.com/Suwayomi/Suwayomi-Server/pull/442) by @martinek)
- (r1151) changes needed for tachiyomi tracker (by @AriaMoradi)
- (r1152) Future proofing (by @AriaMoradi)
- (r1153) Fix settings/check-update endpoint ([#445](https://github.com/Suwayomi/Suwayomi-Server/pull/445) by @martinek)
- (r1154) Fix docs for /server/check-updates ([#447](https://github.com/Suwayomi/Suwayomi-Server/pull/447) by @martinek)
- (r1155) Batch editing and deleting any chapter ([#449](https://github.com/Suwayomi/Suwayomi-Server/pull/449) by @martinek)
- (r1156) make chapters endpoint more unifrom (by @AriaMoradi)
- (r1157) Add batch endpoint for removing downloads from download queue ([#452](https://github.com/Suwayomi/Suwayomi-Server/pull/452) by @martinek)
- (r1158) Download queue missing update fix ([#450](https://github.com/Suwayomi/Suwayomi-Server/pull/450) by @martinek)

## Suwayomi-WebUI Changelog
- (r947) Feature/swr for library screens ([#186](https://github.com/Suwayomi/Suwayomi-WebUI/pull/186) by @martinek)
- (r948) Feature/swr for simple queries ([#187](https://github.com/Suwayomi/Suwayomi-WebUI/pull/187) by @martinek)
- (r949) Check download queue for changes and reload chapters if any chapter download changes state. ([#189](https://github.com/Suwayomi/Suwayomi-WebUI/pull/189) by @martinek)
- (r950) Update typescript dependency ([#190](https://github.com/Suwayomi/Suwayomi-WebUI/pull/190) by @martinek)
- (r951) update browserlist (by @AriaMoradi)
- (r952) Feature/batch chapter download ([#191](https://github.com/Suwayomi/Suwayomi-WebUI/pull/191) by @martinek)
- (r953) Memoize empty view face so it does not change on rerender ([#193](https://github.com/Suwayomi/Suwayomi-WebUI/pull/193) by @martinek)
- (r954) Feature/batch chapter actions ([#194](https://github.com/Suwayomi/Suwayomi-WebUI/pull/194) by @martinek)
- (r955) Fix navbar back button behavior ([#195](https://github.com/Suwayomi/Suwayomi-WebUI/pull/195) by @martinek)
- (r956) Options panels refactoring ([#196](https://github.com/Suwayomi/Suwayomi-WebUI/pull/196) by @martinek)
- (r957) Refactor and fix sorting in library ([#197](https://github.com/Suwayomi/Suwayomi-WebUI/pull/197) by @martinek)
- (r958) Scroll window to top when PagedPager changes page ([#198](https://github.com/Suwayomi/Suwayomi-WebUI/pull/198) by @martinek)
- (r959) Verticall scroll navigation and fix ([#200](https://github.com/Suwayomi/Suwayomi-WebUI/pull/200) by @martinek)
- (r960) Hide overflowing text in reader title if text can't be wrapped ([#199](https://github.com/Suwayomi/Suwayomi-WebUI/pull/199) by @martinek)
- (r961) Add safezone to scroll end detection to prevent edge cases when scrolling to the end would not detect end ([#201](https://github.com/Suwayomi/Suwayomi-WebUI/pull/201) by @martinek)
- (r962) Refactor/download queue and cleanup visuals overall ([#202](https://github.com/Suwayomi/Suwayomi-WebUI/pull/202) by @martinek)
- (r963) Fix "back" pagination on double page layout in reader for spread pages ([#203](https://github.com/Suwayomi/Suwayomi-WebUI/pull/203) by @martinek)



# Server: v0.6.5 + WebUI: r946
## TL;DR
- Fixed Windows bundler

## Suwayomi-Server Changelog
- (r1113) v0.6.4 (by @AriaMoradi)
- (r1114) fix broken links (by @AriaMoradi)
- (r1115) fix more broken stuff (by @AriaMoradi)
- (r1116) fix more broken stuff (by @AriaMoradi)
- (r1117) fix more broken stuff (by @AriaMoradi)
- (r1118) Update winget.yml ([#393](https://github.com/Suwayomi/Suwayomi-Server/pull/393) by @vedantmgoyal2009)
- (r1119) fix jre path([#396](https://github.com/Suwayomi/Suwayomi-Server/pull/396) by @voltrare)
- (r1120) Fix deb package ([#397](https://github.com/Suwayomi/Suwayomi-Server/pull/397) by @mahor1221)
- (r1121) bump version (by @AriaMoradi)

## Suwayomi-WebUI Changelog
- None



# Server: v0.6.4 + WebUI: r946
## TL;DR
- No new major features
- Bug fixes and changes for packaging
- Documentation changes

## Suwayomi-Server Changelog
- (r1087) v0.6.3 (by @AriaMoradi)
- (r1088) Save categories when manga is unfavorited ([#335](https://github.com/Suwayomi/Suwayomi-Server/pull/335) by @Syer10)
- (r1089) handle solid RAR archives ([#339](https://github.com/Suwayomi/Suwayomi-Server/pull/339)) cfso100@gmail.com
- (r1090) add support for changing downloads dir ([#343](https://github.com/Suwayomi/Suwayomi-Server/pull/343) by @AriaMoradi)
- (r1091) fix Applications dir dependency ([#344](https://github.com/Suwayomi/Suwayomi-Server/pull/344) by @AriaMoradi)
- (r1092) add support for alternative web interfaces ([#342](https://github.com/Suwayomi/Suwayomi-Server/pull/342) by @AriaMoradi)
- (r1093) Add displayValues json field for select filter ([#347](https://github.com/Suwayomi/Suwayomi-Server/pull/347) by @Syer10)
- (r1094) document manga endpoints ([#348](https://github.com/Suwayomi/Suwayomi-Server/pull/348) by @Syer10)
- (r1095) add ChapterCount to manga object in categoryMangas endpoint ([#349](https://github.com/Suwayomi/Suwayomi-Server/pull/349) by @abhijeetChawla)
- (r1096) document all endpoints ([#350](https://github.com/Suwayomi/Suwayomi-Server/pull/350) by @Syer10)
- (r1097) fix copymanga ([#354](https://github.com/Suwayomi/Suwayomi-Server/pull/354) by @AriaMoradi)
- (r1098) fix formatting by kotlinter (by @AriaMoradi)
- (r1099) bump WebUI (by @AriaMoradi)
- (r1100) fix WebUI release name (by @AriaMoradi)
- (r1101) Fix documentation errors ([#358](https://github.com/Suwayomi/Suwayomi-Server/pull/358) by @Syer10)
- (r1102) Docs improvements ([#359](https://github.com/Suwayomi/Suwayomi-Server/pull/359) by @Syer10)
- (r1103) Add linux-all.tar.gz & systemd service ([#366](https://github.com/Suwayomi/Suwayomi-Server/pull/366) by @mahor1221)
- (r1104) Publish to Windows Package Managar (WinGet) ([#369](https://github.com/Suwayomi/Suwayomi-Server/pull/369) by @vedantmgoyal2009)
- (r1105) Refactor scripts ([#370](https://github.com/Suwayomi/Suwayomi-Server/pull/370) by @mahor1221)
- (r1106) Run workflow jobs toghether ([#371](https://github.com/Suwayomi/Suwayomi-Server/pull/371) by @mahor1221)
- (r1107) Update gradle action ([#372](https://github.com/Suwayomi/Suwayomi-Server/pull/372) by @mahor1221)
- (r1108) Improve DocumentationDsl, bugfix default values and add queryParams ([#378](https://github.com/Suwayomi/Suwayomi-Server/pull/378) by @Syer10)
- (r1109) Tidy up bundler script ([#380](https://github.com/Suwayomi/Suwayomi-Server/pull/380) by @mahor1221)
- (r1110) Replace linux-all with linux-assets ([#381](https://github.com/Suwayomi/Suwayomi-Server/pull/381) by @mahor1221)
- (r1111) Rename every instance of Suwayomi jar to Suwayomi-Server.jar ([#384](https://github.com/Suwayomi/Suwayomi-Server/pull/384) by @AriaMoradi)
- (r1112) Fix mistakes from #384 ([#385](https://github.com/Suwayomi/Suwayomi-Server/pull/385) by @AriaMoradi)

## Suwayomi-WebUI Changelog
- (r943) fix default width ([#171](https://github.com/Suwayomi/Suwayomi-WebUI/pull/171) by @Robonau)
- (r944) added an update checker button for library ([#172](https://github.com/Suwayomi/Suwayomi-WebUI/pull/172) by @infix)
- (r945) fix download queue delete button ([#176](https://github.com/Suwayomi/Suwayomi-WebUI/pull/176) by @Kreach37)
- (r946) fix mangadex filters ([#177](https://github.com/Suwayomi/Suwayomi-WebUI/pull/177) by @Robonau)



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

## Suwayomi-Server Changelog
- (r1074) v0.6.2 (by @AriaMoradi)
- (r1075) support array filter changes ([#304](https://github.com/Suwayomi/Suwayomi-Server/pull/304) by @AriaMoradi)
- (r1076) fix filterlist bugs ([#306](https://github.com/Suwayomi/Suwayomi-Server/pull/306) by @AriaMoradi)
- (r1077) Update README.md ([#305](https://github.com/Suwayomi/Suwayomi-Server/pull/305) by @mahor1221)
- (r1078) fix meta update changing all keys ([#314](https://github.com/Suwayomi/Suwayomi-Server/pull/314) by @AriaMoradi)
- (r1079) add support for tachiyomi extensions Lib 1.3 ([#316](https://github.com/Suwayomi/Suwayomi-Server/pull/316) by @AriaMoradi)
- (r1080) Fix sources list of one source throws an exception ([#308](https://github.com/Suwayomi/Suwayomi-Server/pull/308) by @Syer10)
- (r1081) Improve source handling, fix errors with uninitialized mangas in broken sources ([#319](https://github.com/Suwayomi/Suwayomi-Server/pull/319) by @Syer10)
- (r1082) Add thumbnail support for stub sources ([#320](https://github.com/Suwayomi/Suwayomi-Server/pull/320) by @Syer10)
- (r1083) update description for Tachidesk-Sorayomi ([#326](https://github.com/Suwayomi/Suwayomi-Server/pull/326) by @DattatreyaReddy)
- (r1084) Add last bit of code needed for Extensions Lib 1.3 ([#330](https://github.com/Suwayomi/Suwayomi-Server/pull/330) by @Syer10)
- (r1085) Add QuickJS, replaces Duktape for Extensions Lib 1.3 ([#331](https://github.com/Suwayomi/Suwayomi-Server/pull/331) by @Syer10)
- (r1086) fix auth not actually blocking requests ([#333](https://github.com/Suwayomi/Suwayomi-Server/pull/333) by @AriaMoradi)

## Suwayomi-WebUI Changelog
- (r930) Source filter scroll fix (array of filters on submit [#149](https://github.com/Suwayomi/Suwayomi-WebUI/pull/149) by @Robonau)
- (r931) fix manga badges setting menu that turns the update/download badges on and off ([#150](https://github.com/Suwayomi/Suwayomi-WebUI/pull/150) by @Robonau)
- (r932) move sorts to copy tachiyomi ([#151](https://github.com/Suwayomi/Suwayomi-WebUI/pull/151) by @Robonau)
- (r933) add comfortable grid option ([#152](https://github.com/Suwayomi/Suwayomi-WebUI/pull/152) by @Robonau)
- (r934) source layouts ([#153](https://github.com/Suwayomi/Suwayomi-WebUI/pull/153) by @Robonau)
- (r935) List layout ([#154](https://github.com/Suwayomi/Suwayomi-WebUI/pull/154) by @Robonau)
- (r936) in library badge to manga in sources ([#156](https://github.com/Suwayomi/Suwayomi-WebUI/pull/156) by @Robonau)
- (r937) mass search ([#157](https://github.com/Suwayomi/Suwayomi-WebUI/pull/157) by @Robonau)
- (r938) 18+ tag on source/extension cards ([#160](https://github.com/Suwayomi/Suwayomi-WebUI/pull/160) by @Robonau)
- (r939) fix search source click ([#164](https://github.com/Suwayomi/Suwayomi-WebUI/pull/164) by @Robonau)
- (r940) items per row setting ([#165](https://github.com/Suwayomi/Suwayomi-WebUI/pull/165) by @Robonau)
- (r941) fix the grid width thing ([#169](https://github.com/Suwayomi/Suwayomi-WebUI/pull/169) by @Robonau)
- (r942) unified library options ([#168](https://github.com/Suwayomi/Suwayomi-WebUI/pull/168) by @infix)

# Server: v0.6.2 + WebUI: r929
## TL;DR
- Changes in WebUI
    - Moved search to Browse
    - Support for Source Filters
    - Better visuals for Download Queue
    - A live version of WebUI is now available [at this link](https://tachidesk-webui-preview.github.io/).

## Suwayomi-Server Changelog
- (r1073) Refactor debian-packager.sh, rename launcher scripts ([#303](https://github.com/Suwayomi/Suwayomi-Server/pull/303) by @mahor1221)

## Suwayomi-WebUI Changelog
- (r912) show locale date, less confusing ([#131](https://github.com/Suwayomi/Suwayomi-WebUI/pull/131) by @AriaMoradi)
- (r913) fix links to work on a bare host ([#132](https://github.com/Suwayomi/Suwayomi-WebUI/pull/132) by @AriaMoradi)
- (r914) fix direct links ([#133](https://github.com/Suwayomi/Suwayomi-WebUI/pull/133) by @AriaMoradi)
- (r915) deploy to github pages (by @AriaMoradi)
- (r916) fix typo (by @AriaMoradi)
- (r917) better naming (by @AriaMoradi)
- (r918) update notice about github pages (by @AriaMoradi)
- (r919) move text (by @AriaMoradi)
- (r920) make all links work by catching 404 (by @AriaMoradi)
- (r921) fix scrolling 8px ([#135](https://github.com/Suwayomi/Suwayomi-WebUI/pull/135) by @Robonau)
- (r922) sorting ([#136](https://github.com/Suwayomi/Suwayomi-WebUI/pull/136) by @Robonau)
- (r923) Close button fix ([#141](https://github.com/Suwayomi/Suwayomi-WebUI/pull/141)) z14942744@gmail.com
- (r924) add NavBarContextProvider ([#128](https://github.com/Suwayomi/Suwayomi-WebUI/pull/128) by @abhijeetChawla)
- (r925) Resolved Merged Conflicts ([#127](https://github.com/Suwayomi/Suwayomi-WebUI/pull/127) by @abhijeetChawla)
- (r926) more Download Queue info ([#138](https://github.com/Suwayomi/Suwayomi-WebUI/pull/138) by @Robonau)
- (r927) Source filters, move search to SourceMangas ([#142](https://github.com/Suwayomi/Suwayomi-WebUI/pull/142) by @Robonau)
- (r928) Source genre sorts design ([#147](https://github.com/Suwayomi/Suwayomi-WebUI/pull/147) by @Robonau)
- (r929) Update LibraryOptions.tsx ([#146](https://github.com/Suwayomi/Suwayomi-WebUI/pull/146) by @Robonau)



# Server: v0.6.1 + WebUI: r911
## TL;DR
- msi and deb packages thanks to @mahor1221
- [Tachidesk-Flutter](https://github.com/Suwayomi/Tachidesk-Flutter) exists now!

## Suwayomi-Server Changelog
- (r1047) update (by @AriaMoradi)
- (r1048) bump version (by @AriaMoradi)
- (r1049) Update README.md (by @AriaMoradi)
- (r1050) Update README.md (by @AriaMoradi)
- (r1051) refactor getChapter ([#268](https://github.com/Suwayomi/Suwayomi-Server/pull/268) by @AriaMoradi)
- (r1052) Improve documentation with Http codes ([#261](https://github.com/Suwayomi/Suwayomi-Server/pull/261) by @Syer10)
- (r1053) Add Route to stop and reset the updater ([#260](https://github.com/Suwayomi/Suwayomi-Server/pull/260) by @ntbm)
- (r1054) ignore non image files ([#269](https://github.com/Suwayomi/Suwayomi-Server/pull/269) by @AriaMoradi)
- (r1055) fix compile erorr (by @AriaMoradi)
- (r1056) update dex2jar (by @AriaMoradi)
- (r1057) Update Gradle and Dependencies ([#281](https://github.com/Suwayomi/Suwayomi-Server/pull/281) by @Syer10)
- (r1058) Handlers must return a result ([#282](https://github.com/Suwayomi/Suwayomi-Server/pull/282) by @Syer10)
- (r1059) Allow app compilation on Java 18+ ([#286](https://github.com/Suwayomi/Suwayomi-Server/pull/286) by @Syer10)
- (r1060) Automated MSI package building ([#277](https://github.com/Suwayomi/Suwayomi-Server/pull/277) by @mahor1221)
- (r1061) Automated debian package building ([#287](https://github.com/Suwayomi/Suwayomi-Server/pull/287) by @mahor1221)
- (r1062) fix Debian package errors ([#288](https://github.com/Suwayomi/Suwayomi-Server/pull/288) by @mahor1221)
- (r1063) Fix build_push.yml Hopefully ([#289](https://github.com/Suwayomi/Suwayomi-Server/pull/289) by @mahor1221)
- (r1064) Improve windows-bundler.sh ([#290](https://github.com/Suwayomi/Suwayomi-Server/pull/290) by @mahor1221)
- (r1065) add Tachidesk-Flutter to readme ([#292](https://github.com/Suwayomi/Suwayomi-Server/pull/292)) @DattatreyaReddy)
- (r1066) no online fetch on backup ([#293](https://github.com/Suwayomi/Suwayomi-Server/pull/293) by @AriaMoradi)
- (r1067) auto-remove duplicate chapters ([#294](https://github.com/Suwayomi/Suwayomi-Server/pull/294) by @AriaMoradi)
- (r1068) remove gson ([#295](https://github.com/Suwayomi/Suwayomi-Server/pull/295) by @AriaMoradi)

## Suwayomi-WebUI Changelog
- (r894) migrate ReaderNavbar to Mui 5 ([#84](https://github.com/Suwayomi/Suwayomi-WebUI/pull/84) by @AriaMoradi)
- (r895) migrate SpinnerImage to Mui 5 ([#97](https://github.com/Suwayomi/Suwayomi-WebUI/pull/97) by @AriaMoradi)
- (r896) migrate VerticalPager to Mui 5 ([#94](https://github.com/Suwayomi/Suwayomi-WebUI/pull/94) by @AriaMoradi)
- (r897) migrate PagedPager to Mui 5 ([#93](https://github.com/Suwayomi/Suwayomi-WebUI/pull/93) by @AriaMoradi)
- (r898) MangaCard imges don't stretch now ([#110](https://github.com/Suwayomi/Suwayomi-WebUI/pull/110) by @abhijeetChawla)
- (r899) show correct title ([#111](https://github.com/Suwayomi/Suwayomi-WebUI/pull/111) by @AriaMoradi)
- (r900) migrate DoublePage to Mui 5 ([#88](https://github.com/Suwayomi/Suwayomi-WebUI/pull/88) by @AriaMoradi)
- (r901) migrate DoublePagedPager to Mui 5 ([#91](https://github.com/Suwayomi/Suwayomi-WebUI/pull/91) by @AriaMoradi)
- (r902) migrate Reader to Mui 5 ([#100](https://github.com/Suwayomi/Suwayomi-WebUI/pull/100) by @AriaMoradi)
- (r903) migrate HorizantalPager to Mui 5 ([#92](https://github.com/Suwayomi/Suwayomi-WebUI/pull/92) by @AriaMoradi)
- (r904) migrate PageNumber to Mui 5 ([#90](https://github.com/Suwayomi/Suwayomi-WebUI/pull/90) by @AriaMoradi)
- (r905) Chapter filter is woking ([#114](https://github.com/Suwayomi/Suwayomi-WebUI/pull/114) by @abhijeetChawla)
- (r906) added extension search ([#115](https://github.com/Suwayomi/Suwayomi-WebUI/pull/115) by @abhijeetChawla)
- (r907) cleanup ([#117](https://github.com/Suwayomi/Suwayomi-WebUI/pull/117) by @AriaMoradi)
- (r908) handle search shortcuts ([#116](https://github.com/Suwayomi/Suwayomi-WebUI/pull/116) by @AriaMoradi)
- (r909) Refactor for Removing unnecesary UseEffect ([#118](https://github.com/Suwayomi/Suwayomi-WebUI/pull/118) by @abhijeetChawla)
- (r910) refactor ChapterList ([#125](https://github.com/Suwayomi/Suwayomi-WebUI/pull/125) by @abhijeetChawla)
- (r911) refactor ChapterOptions ([#126](https://github.com/Suwayomi/Suwayomi-WebUI/pull/126) by @abhijeetChawla)



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

## Suwayomi-Server Changelog
- (r996) cleanup (by @AriaMoradi)
- (r999) better cleaning algorithm (by @AriaMoradi)
- (r1007) remove anime support (by @AriaMoradi)
- (r1009) Fix tests ([#226](https://github.com/Suwayomi/Suwayomi-Server/pull/226) by @ntbm)
- (r1010) Expose unread and download count of Manga in category api ([#227](https://github.com/Suwayomi/Suwayomi-Server/pull/227) by @ntbm)
- (r1011) add Cache Header to Thumbnail Response for improved library performance ([#228](https://github.com/Suwayomi/Suwayomi-Server/pull/228) by @ntbm)
- (r1013) Fix unread and download counts casing ([#230](https://github.com/Suwayomi/Suwayomi-Server/pull/230) by @Syer10)
- (r1014) Fix broken test ([#231](https://github.com/Suwayomi/Suwayomi-Server/pull/231) by @ntbm)
- (r1016) Fix category reorder Endpoint. Added Test for Category Reorder ([#232](https://github.com/Suwayomi/Suwayomi-Server/pull/232) by @ntbm)
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
- (r1037) Add a Kotlin DSL for endpoint documentation ([#249](https://github.com/Suwayomi/Suwayomi-Server/pull/249) by @Syer10)
- (r1038) update (by @AriaMoradi)
- (r1039) update (by @AriaMoradi)
- (r1040) cleanup directory names ([#251](https://github.com/Suwayomi/Suwayomi-Server/pull/251) by @AriaMoradi)
- (r1041) Fix first page not being detected correctly ([#253](https://github.com/Suwayomi/Suwayomi-Server/pull/253) by @AriaMoradi)
- (r1042) Update README.md (by @AriaMoradi)
- (r1043) Update README.md (by @AriaMoradi)
- (r1044) migrate application directories ([#255](https://github.com/Suwayomi/Suwayomi-Server/pull/255) by @AriaMoradi)
- (r1045) add support for MultiSelectListPreference ([#258](https://github.com/Suwayomi/Suwayomi-Server/pull/258) by @AriaMoradi)
- (r1046) empty searchTerm support ([#259](https://github.com/Suwayomi/Suwayomi-Server/pull/259) by @AriaMoradi)


## Suwayomi-WebUI 
- (r821) add Permanent sidebar for desktop widths([#46](https://github.com/Suwayomi/Suwayomi-WebUI/pull/46) by @abhijeetChawla)
- (r822) Fix Local Source being missing (by @AriaMoradi)
- (r823) fix the ugliness of bare messages (by @AriaMoradi)
- (r824) add pull request template (by @AriaMoradi)
- (r825) add Unread badges ([#48](https://github.com/Suwayomi/Suwayomi-WebUI/pull/48) by @ntbm)
- (r826) Back button implementation ([#47](https://github.com/Suwayomi/Suwayomi-WebUI/pull/47) by @abhijeetChawla)
- (r827) remove redundant '/manga' prefix from paths (by @AriaMoradi)
- (r828) refactor (by @AriaMoradi)
- (r829) put Sources and Extensions in the same screen (by @AriaMoradi)
- (r830) Set Fallback Image for broken Thumbnails ([#50](https://github.com/Suwayomi/Suwayomi-WebUI/pull/50) by @ntbm)
- (r833) Apply Api changes for unread badges ([#52](https://github.com/Suwayomi/Suwayomi-WebUI/pull/52) by @ntbm)
- (r834) add EmptyView to DownloadQueue, refactro strings ([#53](https://github.com/Suwayomi/Suwayomi-WebUI/pull/53) by @abhijeetChawla)
- (r835) Bottom navbar for mobile ([#51](https://github.com/Suwayomi/Suwayomi-WebUI/pull/51) by @abhijeetChawla)
- (r836) Implement Unread Filter for Library ([#54](https://github.com/Suwayomi/Suwayomi-WebUI/pull/54) by @ntbm)
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
- (r849) add Search to Library ([#55](https://github.com/Suwayomi/Suwayomi-WebUI/pull/55) by @ntbm)
- (r850) add aspect ratio to the manga card. ([#56](https://github.com/Suwayomi/Suwayomi-WebUI/pull/56) by @abhijeetChawla)
- (r851) better wording (by @AriaMoradi)
- (r852) reorder nav buttons (by @AriaMoradi)
- (r853) nicer gradient (by @AriaMoradi)
- (r854) refactor MangaCard (by @AriaMoradi)
- (r855) closes #58 (by @AriaMoradi
- (r856) Add Resume Reading FAB Manga screen ([#59](https://github.com/Suwayomi/Suwayomi-WebUI/pull/59) by @abhijeetChawla)
- (r857) add filter and badge for `downloadCount` ([#62](https://github.com/Suwayomi/Suwayomi-WebUI/pull/62) by @abhijeetChawla)
- (r858) add issue template (by @AriaMoradi)
- (r859) Change color of navbar in light mode ([#65](https://github.com/Suwayomi/Suwayomi-WebUI/pull/65) by @abhijeetChawla)
- (r860) fix manga FAB margins ([#66](https://github.com/Suwayomi/Suwayomi-WebUI/pull/66) by @AriaMoradi)
- (r861) remove extra scrollbar on mobile ([#67](https://github.com/Suwayomi/Suwayomi-WebUI/pull/67) by @AriaMoradi)
- (r862) Fix Bad messages in Library Appbar search ([#70](https://github.com/Suwayomi/Suwayomi-WebUI/pull/70) by @ntbm)
- (r863) ban the style prop (by @AriaMoradi)
- (r864) Updates pagination update ([#68](https://github.com/Suwayomi/Suwayomi-WebUI/pull/68) by @AriaMoradi)
- (r865) make the whole chapter card into a button ([#73](https://github.com/Suwayomi/Suwayomi-WebUI/pull/73) by @AriaMoradi)
- (r866) fix chapter actions not working if manga is not fetched online ([#74](https://github.com/Suwayomi/Suwayomi-WebUI/pull/74) by @AriaMoradi)
- (r867) migrate some components to Mui5 new styling system ([#72](https://github.com/Suwayomi/Suwayomi-WebUI/pull/72) by @abhijeetChawla)
- (r868) load first page on read manga ([#76](https://github.com/Suwayomi/Suwayomi-WebUI/pull/76) by @AriaMoradi)
- (r869) Revert "migrate some components to Mui5 new styling system ([#72](https://github.com/Suwayomi/Suwayomi-WebUI/pull/72))" (by @AriaMoradi)
- (r870) migrate Backup to Mui 5 ([#106](https://github.com/Suwayomi/Suwayomi-WebUI/pull/106) by @AriaMoradi)
- (r871) migrate EmptyView to Mui 5 ([#95](https://github.com/Suwayomi/Suwayomi-WebUI/pull/95) by @AriaMoradi)
- (r872) migrate CategorySelect to Mui 5 ([#85](https://github.com/Suwayomi/Suwayomi-WebUI/pull/85) by @AriaMoradi)
- (r873) migrate LibraryOptions to Mui 5 ([#83](https://github.com/Suwayomi/Suwayomi-WebUI/pull/83) by @AriaMoradi)
- (r874) migrate ChapterCard.tsx to Mui 5 ([#80](https://github.com/Suwayomi/Suwayomi-WebUI/pull/80) by @AriaMoradi)
- (r875) migrate App.tsx to Mui 5 ([#79](https://github.com/Suwayomi/Suwayomi-WebUI/pull/79) by @AriaMoradi)
- (r876) migrate SourceConfigure to Mui 5 ([#103](https://github.com/Suwayomi/Suwayomi-WebUI/pull/103) by @AriaMoradi)
- (r877) migrate Settings to Mui 5 ([#102](https://github.com/Suwayomi/Suwayomi-WebUI/pull/102) by @AriaMoradi)
- (r878) migrate Updates to Mui 5 ([#104](https://github.com/Suwayomi/Suwayomi-WebUI/pull/104) by @AriaMoradi)
- (r879) Save tabs number in Url to persist tab when go to other paths ([#78](https://github.com/Suwayomi/Suwayomi-WebUI/pull/78) by @abhijeetChawla)
- (r880) migrate LangSelect to Mui 5 ([#86](https://github.com/Suwayomi/Suwayomi-WebUI/pull/86) by @AriaMoradi)
- (r881) migrate ExtensionCard.tsx to Mui 5 ([#81](https://github.com/Suwayomi/Suwayomi-WebUI/pull/81) by @AriaMoradi)
- (r882) migrate SingleSearch to Mui 5 ([#101](https://github.com/Suwayomi/Suwayomi-WebUI/pull/101) by @AriaMoradi)
- (r883) migrate LoadingPlaceholder to Mui 5 ([#96](https://github.com/Suwayomi/Suwayomi-WebUI/pull/96) by @AriaMoradi)
- (r884) migrate About to Mui 5 ([#105](https://github.com/Suwayomi/Suwayomi-WebUI/pull/105) by @AriaMoradi)
- (r885) migrate SourceCard to Mui 5 ([#82](https://github.com/Suwayomi/Suwayomi-WebUI/pull/82) by @AriaMoradi)
- (r886) migrate Manga to Mui 5 ([#99](https://github.com/Suwayomi/Suwayomi-WebUI/pull/99) by @AriaMoradi)
- (r887) migrate Browse to Mui 5 ([#98](https://github.com/Suwayomi/Suwayomi-WebUI/pull/98) by @AriaMoradi)
- (r888) migrate DesktopSideBar to Mui 5 ([#87](https://github.com/Suwayomi/Suwayomi-WebUI/pull/87) by @AriaMoradi)
- (r889) cleanup library  ([#107](https://github.com/Suwayomi/Suwayomi-WebUI/pull/107) by @AriaMoradi)
- (r890) support for new searchTerm (by @AriaMoradi)
- (r891) Revert "support for new searchTerm" (by @AriaMoradi)
- (r892) add support for emptySearch ([#109](https://github.com/Suwayomi/Suwayomi-WebUI/pull/109) by @AriaMoradi)
- (r893) add support for MultiSelectListPreference ([#108](https://github.com/Suwayomi/Suwayomi-WebUI/pull/108) by @AriaMoradi)



# Server: v0.5.4 + WebUI: r820
## TL;DR
- Fixed ReadComicOnline, Toonily and possibly other sources not working
- Backup and Restore now includes Updates tab data
- Removed Anime support from WebUI, Anime support will also be removed from Suwayomi-Server in a future update

## Suwayomi-Server Changelog
- (r973) convert android.jar lib to a maven repo
- (r978) mimic Tachiyomi's behaviour more closely, fixes ReadComicOnline (EN)
- (r980) fix export chapter ordering, include new props in backup
- (r982) remove isNsfw annotation detection
- (r984) use correct time conversion units when doing backups
- (r989) Support using a CatalogueSource instead of only HttpSources ([#219](https://github.com/Suwayomi/Suwayomi-Server/pull/219) by @Syer10)
- (r991) Use a custom task to run electron ([#220](https://github.com/Suwayomi/Suwayomi-Server/pull/220) by @Syer10)

## Suwayomi-WebUI Changelog
- (r810) fix wrong strings in set Server Address dialog, fixes [#39](https://github.com/Suwayomi/Suwayomi-WebUI/issues/39)
- (r811) fix chapterFetch loop
- (r812) fix overlapping requests
- (r813) fix typo
- (r814) Better portrait support ([#41](https://github.com/Suwayomi/Suwayomi-WebUI/issues/41) by @minhe7735)
- (r815) fixes Reader navbar colors when in light mode ([#43](https://github.com/Suwayomi/Suwayomi-WebUI/issues/43) by @abhijeetChawla)
- (r816) default languages cleanup, force Local source enabled
- (r817) force Local source at LangSelect
- (r818) rename ExtensionLangSelect: generic name for generic use
- (r819) don't show anime anymore
- (r820) Remove Anime support



# Server: v0.5.3 + WebUI: r809
## TL;DR
- added support for a equivalent page to Tachiyomi's Updates tab
- fix launchers not working on macOS M1/arm64

## Suwayomi-Server Changelog
- (r956) fix macOS-arm64 bundle launchers not working
- (r957) Workaround StdLib issue and add KtLint to all modules ([#206](https://github.com/Suwayomi/Suwayomi-Server/pull/206) by @Syer10)
- (r960-r963) Add recently updated chapters(Updates) endpoint


## Suwayomi-WebUI Changelog
- (r808) fix chapter list not calling onlineFetch=true
- (r809) add support for Updates



# Server: v0.5.2 + WebUI: r807
## TL;DR
- Fixed Local source not working on Windows
- Fixed Chapter numbers being shown incorrectly

## Suwayomi-Server
### Public API
#### Non-breaking changes
- N/A

#### Breaking changes
- N/A

#### Bug fixes
- (r948) Fix ManaToki (KO) and NewToki (KO) (issue [#202](https://github.com/Suwayomi/Suwayomi-Server/issue/202))
- (r949) Local source: fix windows paths

### Private API
- (r941) Update BytecodeEditor to use Java NIO Paths ([#200](https://github.com/Suwayomi/Suwayomi-Server/pull/200) by @Syer10)
- (r942) Gradle Updates ([#199](https://github.com/Suwayomi/Suwayomi-Server/pull/199) by @Syer10)


## Suwayomi-WebUI
#### Visible changes
- (r804) update text positioning on Reader and Player ([#35](https://github.com/Suwayomi/Suwayomi-WebUI/pull/35) by @voltrare)
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
- Added BasicAuth support, now you can protect your Suwayomi instance if you are running it on a public server
- Added ability to turn off cache for image requests

## Suwayomi-Server
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



## Suwayomi-WebUI
#### Visible changes
- (r790) nice looking progress percentage
- (r791) show a Delete button for downloaded chapters
- (r792) Update hover effect using more of Material-UI color pallete ([#29](https://github.com/Suwayomi/Suwayomi-WebUI/pull/29) by @voltrare)
- (r793) Optimize images ([#32](https://github.com/Suwayomi/Suwayomi-WebUI/pull/32) by @phanirithvij)
- (r794) try fix #30 ([#31](https://github.com/Suwayomi/Suwayomi-WebUI/pull/31) by @phanirithvij)
- (r795) fix viewing page number when the string is long
- (r796) show proper display name for source
- (r797) fail gracefully when a thumbnail has errors
- (r798) fix when a source fails to load mangas
- (r800) add Local source ([#31](https://github.com/Suwayomi/Suwayomi-WebUI/pull/31))
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

## Suwayomi-Server
### Public API
#### Non-breaking changes
- (r888) add installing APK from external sources endpoint

#### Breaking changes
- (r877 [#188](https://github.com/Suwayomi/Suwayomi-Server/pull/188) by @Syer10) `MangaDataClass.genre` changed type to `List<String>`

#### Bug fixes
- (r899-r901) fix when an external apk is installed and it doesn't have the default tachiyomi-extensions name
- (r905) fix a bug where if two sources return the same URL, a false duplicate might be detected

### Private API
- (r887) the `run` task won't call `downloadWebUI` now
- (r902) cleanup print/ln instances
- (r906) better handling of uninstalling Extensions

## Suwayomi-WebUI
#### Visible changes
- (r770) add support for the new genre type
- (r771) set the default value of `showNsfw` to `true` so we won't have visual artifacts with a clean install
- (r774 [#21](https://github.com/Suwayomi/Suwayomi-WebUI/pull/21) by @voltrare) `ReaderNavbar.jsx`: Swap close and retract Navbar buttons
- (r775 [#23](https://github.com/Suwayomi/Suwayomi-WebUI/pull/23) by @voltrare) `yarn.lock`: Fixes version inconsistency after commit 9b866811b
- (r776 [#23](https://github.com/Suwayomi/Suwayomi-WebUI/pull/23) by @voltrare) add margin between Source and Extension cards, make the Search button look nicer
- (r777) add support for installing external APK files
- (r778) fix the makeToaster?
- (r779) Action button for installing external extension
- (r780 Suwayomi/Suwayomi-WebUI#25) add on hover, active effect to Chapter/Episode card
- (r782-r785) updating material-ui to v5 changed the theme
- (r785-r788) better `SourceCard` looks on mobile, move `SourceDataClass.isConfigurable` gear button to `SourceMangas`
- (r789) implement source configuration

#### Bug fixes
- N/A

#### Internal changes
- (r782-r785) update dependencies, migrate material-ui from v4 to v5



# Server: v0.4.9 + WebUI: r769
## Suwayomi-Server
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


## Suwayomi-WebUI
#### Visible changes
- (r767-r769) Support for hiding NSFW content in settings screen, extensions screen, sources screen

#### Bug fixes
- N/A

#### Internal changes
- (r767) Remove some duplicate dependency declaration from `package.json`

#### Non-code changes
- (r42-r45) Change `README.md`: some links and stuff 
- (r45-r765) Add all of the commit history from when WebUI was separated from Server, jumping from r45 to r765 (r45 is exactly the same as r765)
- (r766) Steal `.gitattributes` from Suwayomi-Server
- (r767) Dependency cleanup in `package.json`




# Server: v0.4.8 + WebUI: r41
## Suwayomi-Server
### Public API
#### Non-breaking changes
- Added support for serializing Search Filters
- `SourceDataClass` now has a `isNsfw` key

#### Breaking changes
- N/A

#### Bug fixes
- Fixed a bug where backup restore reversed chapter order
- Open Site feature now works properly (https://github.com/Suwayomi/Suwayomi-WebUI/issues/19)

### Private API
- Added `CloudflareInterceptor` from TachiWeb-Server
- Restoring backup for mangas in library(merging manga data) is now supported

## Suwayomi-WebUI
#### Visible changes
- Better looking manga card titles
- Better reader title, next, prev buttons

#### Bug fixes
- Open Site feature now works properly (https://github.com/Suwayomi/Suwayomi-WebUI/issues/19)
- Re-ordering categories now works

#### Internal changes
- N/A
