# Server: v0.4.9-r882 + WebUI: r776
## Tachidesk-Server
### Public API
#### Non-breaking changes
- N/A

#### Breaking changes
- (r877 #188 by @Syer10) `MangaDataClass.genre` changed type to `List<String>`

#### Bug fixes
- N/A

### Private API
- N/A


## Tachidesk-WebUI
#### Visible changes
- (r770) add support for the new genre type
- (r771) set the default value of `showNsfw` to `false` so we won't have visual artifacts with a clean install
- (r774 #21 by @voltrare) `ReaderNavbar.jsx`: Swap close and retract Navbar buttons
- (r775 #23 by @voltrare) `yarn.lock`: Fixes version inconsistency after commit 9b866811b
- (r776 #22 by @voltrare) add margin between Source and Extension cards, make the Search button look nicer

#### Bug fixes
- N/A

#### Internal changes
- N/A



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
