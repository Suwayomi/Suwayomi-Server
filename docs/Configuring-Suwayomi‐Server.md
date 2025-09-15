Suwayomi-Server configuration file is named `server.conf` and is located inside [the data directory](https://github.com/Suwayomi/Suwayomi-Server/wiki/The-Data-Directory).

The configuration file is written in HOCON. Google is your friend if you want to know more. 

**Note:** new keys might be added in the future. Suwayomi generally attempts to update your conf file automatically, but some keys may need to be updated manually.

## Troubleshooting
### I messed up my configuration file
- The reference configuration file can be found [here](https://github.com/Suwayomi/Suwayomi-Server/blob/master/server/src/main/resources/server-reference.conf) replace your whole configuration or erroneous keys referring to it.
- Suwayomi will create a default configuration file when one doesn't exist, you can delete `server.conf` to get a copy of the reference configuration file after a restart.

### I am running Suwayomi in a headless environment (docker, NAS, VPS, etc.)
- Set `server.systemTrayEnabled` to false, it will prevent Suwayomi to attempt to create a System Tray icon.
- Set `server.initialOpenInBrowserEnabled`to false, it will prevent Suwayomi to attempt to open a browser on startup.

### My Suwayomi data directory/downloads size is getting to big
- Set `server.downloadsPath` to the desired path, if you only need to change where downloads are stored. You have to move/remove the existing downloads manually.
- Set the special `server.rootDir` key if you need Suwayomi to use a custom data directory path, refer to [this section](#overriding-tachidesk-servers-data-directory-path). 

## Configuration Options
### Server ip and port bindings
```conf
server.ip = "0.0.0.0"
server.port = 4567
```
- `server.ip` can be a IP or domain name.

### Socks5 proxy
```
server.socksProxyEnabled = false
server.socksProxyHost = ""
server.socksProxyPort = ""
```
This section directs Suwayomi to connect to the network through a proxy server. 

An example configuration can be:
```
server.socksProxyEnabled = true
server.socksProxyHost = "yourproxyhost.com"
server.socksProxyPort = "8080"
```

### webUI
```
server.webUIEnabled = true
server.initialOpenInBrowserEnabled = true
server.webUIInterface = "browser" # "browser" or "electron"
server.electronPath = ""
server.webUIFlavor = "WebUI" # "WebUI" or "Custom"
```
- `server.webUIEnabled` controls if Suwayomi will serve `Suwayomi-WebUI` and if it downloads/updates it on startup.
- `server.initialOpenInBrowserEnabled` controls if Suwayomi will attempt to open a brwoser/electron window on startup, disabling this on headless servers is recommended.
- `server.webUIInterface` which web interface Suwayomi should launch on startup, options are `"browser"` and `"electron"`
- `server.electronPath` path of the main electron executable, should be in double quotes
- `server.webUIFlavor` set `"WebUI"` to make the server download and update Suwayomi-WebUI automatically or `"Custom"` if you want the server to serve a custom web interface that you manage by yourself.
  - Note: "Custom" would be useful if you want to test preview versions of Suwayomi-WebUI or when you are using or developing other web interfaces like the web version of Suwayomi-Sorayomi.

### Downloader
```
server.downloadAsCbz = true
server.downloadsPath = ""
server.autoDownloadNewChapters = false
server.excludeEntryWithUnreadChapters = true
server.autoDownloadNewChaptersLimit = 0
server.autoDownloadIgnoreReUploads = false
server.downloadConversions = {}
```
- `server.downloadAsCbz = true` configures Suwayomi to automatically compress chapters into CBZ.
- `server.downloadsPath = ""` the path where manga downloads will be stored, if the value is empty, the default directory `downloads` inside [the data directory](https://github.com/Suwayomi/Suwayomi-Server/wiki/The-Data-Directory) will be used. If you are on Windows the slashes `\` needs to be doubled(`\\`) or replaced with `/`
- `server.autoDownloadNewChapters = false` controls if Suwayomi should automatically download new chapters after a library update.
- `server.excludeEntryWithUnreadChapters = true` controls if Suwayomi will download new chapters for titles with unread chapters (requires `server.autoDownloadNewChapters`).
- `server.autoDownloadNewChaptersLimit = 0` sets how many chapters should be downloaded at most, `0` to disable the limit; if the limit is reached, new chapters will not be downloaded (requires `server.autoDownloadNewChapters`).
- `server.autoDownloadIgnoreReUploads = false` controls if Suwayomi will re-download re-uploads on update (requires `server.autoDownloadNewChapters`).
- `server.downloadConversions = {}` configures optional image conversions for all downloads. This is an [JSON object](https://en.wikipedia.org/wiki/JSON#Syntax), with the source image [mime type](https://en.wikipedia.org/wiki/Media_type) as the key and an object with the target mime type and options as value.  
  The following options are both valid:  
  ```
  server.downloadConversions = { "image/webp" : { target : "image/jpeg", compressionLevel = 0.8 }}
  # -- or --
  server.downloadConversions."image/webp" = {
    target = "image/jpeg"   # image type to convert to
    compressionLevel = 0.8  # quality in range [0,1], leave away to use default compression
  }
  ```  
  A source mime type `default` can be used as fallback to convert all images; a target mime type of `none` can be used to disable conversion for a particular format.

### Updater
```
server.excludeUnreadChapters = true
server.excludeNotStarted = true
server.excludeCompleted = true
server.globalUpdateInterval = 12
server.updateMangas = false
```
- `server.excludeUnreadChapters = true` controls if Suwayomi should include titles with unread chapters in the library update.
- `server.excludeNotStarted = true` controls if Suwayomi should include titles which weren't started yet in the library update.
- `server.excludeCompleted = true` controls if Suwayomi should include titles which are marked completed in the library update.
- `server.globalUpdateInterval = 12` sets the time in hours for the automatic library internal, `0` to disable it. Range: 6 <= n < ∞
- `server.updateMangas = false` controls if Suwayomi should also update title metadata along with fetching new chapters in the library update.

### Authentication
```
server.authMode = "none" # none, basic_auth or simple_login
server.authUsername = "user"
server.authPassword = "pass"
```
- `server.authMode = "none"`: Since v2.1.1867, Suwayomi supports two modes of authentication. Enabling authentication is useful when hosting on a public network/the Internet. If you used the original `server.basicAuth*` variables, it will be automatically migrated.  
  `basic_auth` configures Suwayomi for [Basic access authentication](https://en.wikipedia.org/wiki/Basic_access_authentication).  
  `simple_login` works similarly to Basic Authentication, but presents a custom login page. The login is stored via a cookie and needs to be refreshed on every server restart or every 30 minutes.
- `server.authUsername` the username value that you have to provide when authenticating.
- `server.authPassword` the password value that you have to provide when authenticating.

**Note**: Basic access authentication sends username and password in cleartext and is not completely secure over HTTP, it's recommended to pair this feature with a reverse proxy server like nginx and expose the server over HTTPS. Similarly, with `simple_login`, the credentials are sent in cleartext on login, and the cookie is sent with every request. Also your browser caches the credentials/cookie, so you should be careful when accessing the server from non-private devices and use incognito mode.

### misc
```
server.debugLogsEnabled = false
server.systemTrayEnabled = true
server.maxLogFiles = 31
server.maxLogFileSize = "10mb"
server.maxLogFolderSize = "100mb"
server.extensionRepos = []
server.maxSourcesInParallel = 6
```
- `server.debugLogsEnabled` controls whether if Suwayomi-Server should print more information while being run inside a Terminal/CMD/Powershell window. 
- `server.systemTrayEnabled = true` whether if Suwayomi-Server should show a System Tray Icon, disabling this on headless servers is recommended.
- `server.maxLogFiles = 31` sets the maximum number of days to keep files before they get deleted.
- `server.maxLogFileSize = "10mb"` sets the maximum size of a log file - values are formatted like: 1 (bytes), 1KB (kilobytes), 1MB (megabytes), 1GB (gigabytes)
- `server.maxLogFolderSize = "100mb"` sets the maximum size of all saved log files - values are formatted like: 1 (bytes), 1KB (kilobytes), 1MB (megabytes), 1GB (gigabytes)
- `server.extensionRepos` is a list of extension repositories for custom sources. Uses the same format as Mihon; each entry is expected to be a string URL pointing to a JSON file representing the repository.
- `server.maxSourcesInParallel = 6` sets how many sources can do requests (updates, downloads) in parallel. Updates/downloads are grouped by source and all mangas of a source are updated/downloaded synchronously. Range: 1 <= n <= 20.

### Backup
```
server.backupPath = ""
server.backupTime = "00:00"
server.backupInterval = 1
server.backupTTL = 14
```
- `server.backupPath = ""` the path where backups will be stored, if the value is empty, the default directory `backups` inside [the data directory](https://github.com/Suwayomi/Suwayomi-Server/wiki/The-Data-Directory) will be used. If you are on Windows the slashes `\` needs to be doubled(`\\`) or replaced with `/`
- `server.backupTime = "00:00"` sets the time of day at which the automated backup should be triggered.
- `server.backupInterval = 1` sets the interval in which the server will automatically create a backup in days, `0` to disable it.
- `server.backupTTL = 14` sets how long backup files will be kept before they will get deleted in days, `0` to disable it.

### Local Source
```
server.localSourcePath = ""
```
- `server.localSourcePath = ""` the path from where local manga are loaded, if the value is empty, the default directory `local` inside [the data directory](https://github.com/Suwayomi/Suwayomi-Server/wiki/The-Data-Directory) will be used. If you are on Windows the slashes `\` needs to be doubled(`\\`) or replaced with `/`

### Cloudflare bypass
```
server.flareSolverrEnabled = false
server.flareSolverrUrl = "http://localhost:8191"
server.flareSolverrTimeout = 60 # time in seconds
server.flareSolverrSessionName = "suwayomi"
server.flareSolverrSessionTtl = 15 # time in minutes
server.flareSolverrAsResponseFallback = false
```
- `server.flareSolverrEnabled = false` controls if Suwayomi attempts to connect to FlareSolverr if a CloudFlare challenge is detected.
- `server.flareSolverrUrl = "http://localhost:8191"` sets the address where Suwayomi attempts to connect to FlareSolverr. The instance needs to run and be accessible from the server where Suwayomi is running for CloudFlare bypass to work.
- `server.flareSolverrTimeout = 60` sets the timeout to wait for FlareSolverr to solve a given challenge in seconds.
- `server.flareSolverrSessionName = "suwayomi"` sets the session name that FlareSolverr should use. Tells FlareSolverr which set of cookies to use.
- `server.flareSolverrSessionTtl = 15` sets the time for how long sessions in FlareSolverr should live in minutes. After this time, FlareSolverr will forget cookies.
- `server.flareSolverrAsResponseFallback = false` allows Suwayomi to use the contents of the request that FlareSolverr received in case Suwayomi sees a CloudFlare challenge but FlareSolverr does not (which prevents it from solving the challenge).

**Note:** Byparr is a popular alternative to FlareSolverr. It uses the same API schema as FlareSolverr, so you can also configure the address of a Byparr instance in `server.flareSolverrUrl`.

### OPDS
```
server.opdsUseBinaryFileSizes = false
server.opdsItemsPerPage = 50
server.opdsEnablePageReadProgress = true
server.opdsMarkAsReadOnDownload = false
server.opdsShowOnlyUnreadChapters = false
server.opdsShowOnlyDownloadedChapters = false
server.opdsChapterSortOrder = "DESC"
```
- `server.opdsUseBinaryFileSizes = false` controls if Suwayomi should display file sizes in binary units (KiB, MiB, GiB) or decimal (KB, MB, GB) in OPDS listings.
- `server.opdsItemsPerPage = 50` sets the number of items per page in OPDS listings. Range: 10 <= n <= 5000.
- `server.opdsEnablePageReadProgress = true` controls if Suwayomi should include information to track chapter read progress in OPDS chapter page listings. This will cause the reader to mark the pages as read when it downloads the images.
- `server.opdsMarkAsReadOnDownload = false` controls if Suwayomi should mark the chapters as read when it is downloaded through the OPDS listing.
- `server.opdsShowOnlyUnreadChapters = false` controls if OPDS listings should only include unread chapters.
- `server.opdsShowOnlyDownloadedChapters = false` controls if OPDS listings should only include downloaded chapters.
- `server.opdsChapterSortOrder = "DESC"` sets the default chapter sort order in OPDS listings, either `"ASC"` or `"DESC"`

## Overriding configuration options with command-line arguments
You can override the above configuration options with command-line arguments. 
You usually only need to set this when using custom setups like a portable version of Suwayomi or your if your User dir cannot be written to or your system administrator doesn't allow it.

Use the pattern bellow.
```
java -Dsuwayomi.tachidesk.config.<configuration option 1>=<configuration value 1> -Dsuwayomi.tachidesk.config.<configuration option 2>=<configuration value 2> ... -Dsuwayomi.tachidesk.config.<configuration option N>=<configuration value N> -jar <path to server jar>
```
for example to force launching Suwayomi-Server in electron you would have something like:
```
java -Dsuwayomi.tachidesk.config.server.webUIInterface=electron -Dsuwayomi.tachidesk.config.server.electronPath="/path/to/electron" -jar Suwayomi-Server-v0.X.Y-rXXXX.jar
```

**Note:** you can put the command above in a custom launcher script like the ones found [here](https://github.com/Suwayomi/Suwayomi-Server/tree/master/scripts/resources).

## Overriding Suwayomi-Server's Data Directory path
The only possible way to override the default Data Directory path is with command-line arguments(the chicken and egg problem!).
Add the special config option `server.rootDir="<path to data directory>"` to your command-line arguments.

For example:
```
java -Dsuwayomi.tachidesk.config.server.rootDir="/path/to/data/directory" -jar Suwayomi-Server-v0.X.Y-rXXXX.jar
```

