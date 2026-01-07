#!/bin/sh

# Immediately bail out if any command fails:
set -e

echo "Suwayomi data location inside the container: /home/suwayomi/.local/share/Tachidesk"

# make sure the server.conf file exists
/home/suwayomi/create_server_conf.sh

# set default values for environment variables:
export TZ="${TZ:-Etc/UTC}"

# Set default values for settings
sed -i -r "s/server.initialOpenInBrowserEnabled = ([0-9]+|[a-zA-Z]+)( #)?/server.initialOpenInBrowserEnabled = false #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.systemTrayEnabled = ([0-9]+|[a-zA-Z]+)( #)?/server.systemTrayEnabled = false #/" /home/suwayomi/.local/share/Tachidesk/server.conf

# !!! IMPORTANT: make sure to add new env variables to the container.yml workflow step testing the container with providing environment variables

# Overwrite configuration values with environment variables
# the "( #)?" at the end of the regex prevents the settings comment from getting removed
# some settings might not have a comment, however, "sed" does not support non matching groups in a regex, thus, an empty
# comment will just be created for these settings

# Server ip and port bindings
sed -i -r "s/server.ip = \"(.*?)\"( #)?/server.ip = \"${BIND_IP:-\1}\" #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.port = ([0-9]+|[a-zA-Z]+)( #)?/server.port = ${BIND_PORT:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf

# Socks5 proxy
sed -i -r "s/server.socksProxyEnabled = ([0-9]+|[a-zA-Z]+)( #)?/server.socksProxyEnabled = ${SOCKS_PROXY_ENABLED:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.socksProxyVersion = ([0-9]+|[a-zA-Z]+)( #)?/server.socksProxyVersion = ${SOCKS_PROXY_VERSION:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.socksProxyHost = \"(.*?)\"( #)?/server.socksProxyHost = \"${SOCKS_PROXY_HOST:-\1}\" #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.socksProxyPort = \"(.*?)\"( #)?/server.socksProxyPort = \"${SOCKS_PROXY_PORT:-\1}\" #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.socksProxyUsername = \"(.*?)\"( #)?/server.socksProxyUsername = \"${SOCKS_PROXY_USERNAME:-\1}\" #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.socksProxyPassword = \"(.*?)\"( #)?/server.socksProxyPassword = \"${SOCKS_PROXY_PASSWORD:-\1}\" #/" /home/suwayomi/.local/share/Tachidesk/server.conf

# webUI
sed -i -r "s/server.webUIEnabled = ([0-9]+|[a-zA-Z]+)( #)?/server.webUIEnabled = ${WEB_UI_ENABLED:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.webUIFlavor = \"*([a-zA-Z0-9_]+)\"*( #)?/server.webUIFlavor = ${WEB_UI_FLAVOR:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.webUIChannel = \"*([a-zA-Z0-9_]+)\"*( #)?/server.webUIChannel = ${WEB_UI_CHANNEL:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.webUIUpdateCheckInterval = ([0-9]+|[a-zA-Z]+)( #)?/server.webUIUpdateCheckInterval = ${WEB_UI_UPDATE_INTERVAL:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf

# downloader
sed -i -r "s/server.downloadAsCbz = ([0-9]+|[a-zA-Z]+)( #)?/server.downloadAsCbz = ${DOWNLOAD_AS_CBZ:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.autoDownloadNewChapters = ([0-9]+|[a-zA-Z]+)( #)?/server.autoDownloadNewChapters = ${AUTO_DOWNLOAD_CHAPTERS:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.excludeEntryWithUnreadChapters = ([0-9]+|[a-zA-Z]+)( #)?/server.excludeEntryWithUnreadChapters = ${AUTO_DOWNLOAD_EXCLUDE_UNREAD:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.autoDownloadNewChaptersLimit = ([0-9]+|[a-zA-Z]+)( #)?/server.autoDownloadNewChaptersLimit = ${AUTO_DOWNLOAD_NEW_CHAPTERS_LIMIT:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.autoDownloadIgnoreReUploads = ([0-9]+|[a-zA-Z]+)( #)?/server.autoDownloadIgnoreReUploads = ${AUTO_DOWNLOAD_IGNORE_REUPLOADS:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
if [ -n "$DOWNLOAD_CONVERSIONS" ]; then
    perl -0777 -i -pe 's/server\.downloadConversions = ({[^#]*?}}?)/server.downloadConversions = $ENV{DOWNLOAD_CONVERSIONS}/gs' /home/suwayomi/.local/share/Tachidesk/server.conf
fi
if [ -n "$SERVE_CONVERSIONS" ]; then
    perl -0777 -i -pe 's/server\.serveConversions = ({[^#]*?}}?)/server.serveConversions = $ENV{SERVE_CONVERSIONS}/gs' /home/suwayomi/.local/share/Tachidesk/server.conf
fi

# extension repos
if [ -n "$EXTENSION_REPOS" ]; then
    perl -0777 -i -pe 's/server\.extensionRepos = (\[.*?\])/server.extensionRepos = $ENV{EXTENSION_REPOS}/gs' /home/suwayomi/.local/share/Tachidesk/server.conf
fi

# requests
sed -i -r "s/server.maxSourcesInParallel = ([0-9]+|[a-zA-Z]+)( #)?/server.maxSourcesInParallel = ${MAX_SOURCES_IN_PARALLEL:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf

# updater
sed -i -r "s/server.excludeUnreadChapters = ([0-9]+|[a-zA-Z]+)( #)?/server.excludeUnreadChapters = ${UPDATE_EXCLUDE_UNREAD:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.excludeNotStarted = ([0-9]+|[a-zA-Z]+)( #)?/server.excludeNotStarted = ${UPDATE_EXCLUDE_STARTED:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.excludeCompleted = ([0-9]+|[a-zA-Z]+)( #)?/server.excludeCompleted = ${UPDATE_EXCLUDE_COMPLETED:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.globalUpdateInterval = ([0-9\.]+|[a-zA-Z]+)( #)?/server.globalUpdateInterval = ${UPDATE_INTERVAL:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.updateMangas = ([0-9]+|[a-zA-Z]+)( #)?/server.updateMangas = ${UPDATE_MANGA_INFO:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf

# Authentication
AUTH_MODE_VAL="${AUTH_MODE:-$( [ "$BASIC_AUTH_ENABLED" = "true" ] && echo 'basic_auth' || echo "" )}"
AUTH_USERNAME_VAL="${AUTH_USERNAME:-$BASIC_AUTH_USERNAME}"
AUTH_PASSWORD_VAL="${AUTH_PASSWORD:-$BASIC_AUTH_PASSWORD}"
sed -i -r "s/server.authMode = \"*([a-zA-Z0-9_]+)\"*( #)?/server.authMode = ${AUTH_MODE_VAL:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.authUsername = \"(.*?)\"( #)?/server.authUsername = \"${AUTH_USERNAME_VAL:-\1}\" #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.authPassword = \"(.*?)\"( #)?/server.authPassword = \"${AUTH_PASSWORD_VAL:-\1}\" #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.jwtAudience = \"(.*?)\"( #)?/server.jwtAudience = \"${JWT_AUDIENCE:-\1}\" #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.jwtTokenExpiry = \"(.*?)\"( #)?/server.jwtTokenExpiry = \"${JWT_TOKEN_EXPIRY:-\1}\" #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.jwtRefreshExpiry = \"(.*?)\"( #)?/server.jwtRefreshExpiry = \"${JWT_REFRESH_EXPIRY:-\1}\" #/" /home/suwayomi/.local/share/Tachidesk/server.conf

sed -i -r "s/server.basicAuthEnabled = ([0-9]+|[a-zA-Z]+)( #)?/server.basicAuthEnabled = ${BASIC_AUTH_ENABLED:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.basicAuthUsername = \"(.*?)\"( #)?/server.basicAuthUsername = \"${BASIC_AUTH_USERNAME:-\1}\" #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.basicAuthPassword = \"(.*?)\"( #)?/server.basicAuthPassword = \"${BASIC_AUTH_PASSWORD:-\1}\" #/" /home/suwayomi/.local/share/Tachidesk/server.conf

# misc
sed -i -r "s/server.debugLogsEnabled = ([0-9]+|[a-zA-Z]+)( #)?/server.debugLogsEnabled = ${DEBUG:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.maxLogFiles = ([0-9]+|[a-zA-Z]+)( #)?/server.maxLogFiles = ${MAX_LOG_FILES:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.maxLogFileSize = \"(.*?)\"( #)?/server.maxLogFileSize = \"${MAX_LOG_FILE_SIZE:-\1}\" #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.maxLogFolderSize = \"(.*?)\"( #)?/server.maxLogFolderSize = \"${MAX_LOG_FOLDER_SIZE:-\1}\" #/" /home/suwayomi/.local/share/Tachidesk/server.conf

# backup
sed -i -r "s/server.backupTime = \"(.*?)\"( #)?/server.backupTime = \"${BACKUP_TIME:-\1}\" #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.backupInterval = ([0-9]+|[a-zA-Z]+)( #)?/server.backupInterval = ${BACKUP_INTERVAL:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.backupTTL = ([0-9]+|[a-zA-Z]+)( #)?/server.backupTTL = ${BACKUP_TTL:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.autoBackupIncludeManga = ([0-9]+|[a-zA-Z]+)( #)?/server.autoBackupIncludeManga = ${AUTO_BACKUP_INCLUDE_MANGA:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.autoBackupIncludeCategories = ([0-9]+|[a-zA-Z]+)( #)?/server.autoBackupIncludeCategories = ${AUTO_BACKUP_INCLUDE_CATEGORIES:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.autoBackupIncludeChapters = ([0-9]+|[a-zA-Z]+)( #)?/server.autoBackupIncludeChapters = ${AUTO_BACKUP_INCLUDE_CHAPTERS:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.autoBackupIncludeTracking = ([0-9]+|[a-zA-Z]+)( #)?/server.autoBackupIncludeTracking = ${AUTO_BACKUP_INCLUDE_TRACKING:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.autoBackupIncludeHistory = ([0-9]+|[a-zA-Z]+)( #)?/server.autoBackupIncludeHistory = ${AUTO_BACKUP_INCLUDE_HISTORY:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.autoBackupIncludeClientData = ([0-9]+|[a-zA-Z]+)( #)?/server.autoBackupIncludeClientData = ${AUTO_BACKUP_INCLUDE_CLIENT_DATA:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.autoBackupIncludeServerSettings = ([0-9]+|[a-zA-Z]+)( #)?/server.autoBackupIncludeServerSettings = ${AUTO_BACKUP_INCLUDE_SERVER_SETTINGS:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf


# cloudflare bypass
sed -i -r "s/server.flareSolverrEnabled = ([0-9]+|[a-zA-Z]+)( #)?/server.flareSolverrEnabled = ${FLARESOLVERR_ENABLED:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s|server.flareSolverrUrl = \"(.*?)\"( #)?|server.flareSolverrUrl = \"${FLARESOLVERR_URL:-\1}\" #|" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.flareSolverrTimeout = ([0-9]+|[a-zA-Z]+)( #)?/server.flareSolverrTimeout = ${FLARESOLVERR_TIMEOUT:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.flareSolverrSessionName = \"(.*?)\"( #)?/server.flareSolverrSessionName = \"${FLARESOLVERR_SESSION_NAME:-\1}\" #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.flareSolverrSessionTtl = ([0-9]+|[a-zA-Z]+)( #)?/server.flareSolverrSessionTtl = ${FLARESOLVERR_SESSION_TTL:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.flareSolverrAsResponseFallback = ([0-9]+|[a-zA-Z]+)( #)?/server.flareSolverrAsResponseFallback = ${FLARESOLVERR_RESPONSE_AS_FALLBACK:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf

# opds
sed -i -r "s/server.opdsUseBinaryFileSizes = ([0-9]+|[a-zA-Z]+)( #)?/server.opdsUseBinaryFileSizes = ${OPDS_USE_BINARY_FILE_SIZES:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.opdsItemsPerPage = ([0-9]+|[a-zA-Z]+)( #)?/server.opdsItemsPerPage = ${OPDS_ITEMS_PER_PAGE:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.opdsEnablePageReadProgress = ([0-9]+|[a-zA-Z]+)( #)?/server.opdsEnablePageReadProgress = ${OPDS_ENABLE_PAGE_READ_PROGRESS:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.opdsMarkAsReadOnDownload = ([0-9]+|[a-zA-Z]+)( #)?/server.opdsMarkAsReadOnDownload = ${OPDS_MARK_AS_READ_ON_DOWNLOAD:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.opdsShowOnlyUnreadChapters = ([0-9]+|[a-zA-Z]+)( #)?/server.opdsShowOnlyUnreadChapters = ${OPDS_SHOW_ONLY_UNREAD_CHAPTERS:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.opdsShowOnlyDownloadedChapters = ([0-9]+|[a-zA-Z]+)( #)?/server.opdsShowOnlyDownloadedChapters = ${OPDS_SHOW_ONLY_DOWNLOADED_CHAPTERS:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.opdsChapterSortOrder = \"*([a-zA-Z0-9_]+)\"*( #)?/server.opdsChapterSortOrder = ${OPDS_CHAPTER_SORT_ORDER:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.opdsCbzMimetype = \"*([a-zA-Z0-9_]+)\"*( #)?/server.opdsCbzMimetype = ${OPDS_CBZ_MIME_TYPE:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf

# koreader
sed -i -r "s/server.koreaderSyncChecksumMethod = \"*([a-zA-Z0-9_]+)\"*( #)?/server.koreaderSyncChecksumMethod = ${KOREADER_SYNC_CHECKSUM_METHOD:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.koreaderSyncPercentageTolerance = ([-0-9\.Ee]+)?( #)/server.koreaderSyncPercentageTolerance = ${KOREADER_SYNC_PERCENTAGE_TOLERANCE:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.koreaderSyncStrategyForward = \"*([a-zA-Z0-9_]+)\"*( #)?/server.koreaderSyncStrategyForward = ${KOREADER_SYNC_STRATEGY_FORWARD:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.koreaderSyncStrategyBackward = \"*([a-zA-Z0-9_]+)\"*( #)?/server.koreaderSyncStrategyBackward = ${KOREADER_SYNC_STRATEGY_BACKWARD:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf

# database
sed -i -r "s/server.databaseType = \"*([a-zA-Z0-9_]+)\"*( #)?/server.databaseType = ${DATABASE_TYPE:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s|server.databaseUrl = \"(.*?)\"( #)?|server.databaseUrl = \"${DATABASE_URL:-\1}\" #|" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.databaseUsername = \"(.*?)\"( #)?/server.databaseUsername = \"${DATABASE_USERNAME:-\1}\" #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.databasePassword = \"(.*?)\"( #)?/server.databasePassword = \"${DATABASE_PASSWORD:-\1}\" #/" /home/suwayomi/.local/share/Tachidesk/server.conf
sed -i -r "s/server.useHikariConnectionPool = ([0-9]+|[a-zA-Z]+)( #)?/server.useHikariConnectionPool = ${USE_HIKARI_CONNECTION_POOL:-\1} #/" /home/suwayomi/.local/share/Tachidesk/server.conf

rm -rf /home/suwayomi/.local/share/Tachidesk/cache/kcef/Singleton*

if command -v Xvfb >/dev/null; then
  command="xvfb-run --auto-servernum java"
  if [ -d /opt/kcef/jcef ]; then
    # if we have KCEF downloaded in the container, attempt to link it into the data directory where Suwayomi expects it
    if [ ! -d /home/suwayomi/.local/share/Tachidesk/bin ]; then
      mkdir -p /home/suwayomi/.local/share/Tachidesk/bin
    fi
    if [ ! -d /home/suwayomi/.local/share/Tachidesk/bin/kcef ] && [ ! -L /home/suwayomi/.local/share/Tachidesk/bin/kcef ]; then
      ln -s /opt/kcef/jcef /home/suwayomi/.local/share/Tachidesk/bin/kcef
    fi
  fi
  if [ -d /home/suwayomi/.local/share/Tachidesk/bin/kcef ] || [ -L /home/suwayomi/.local/share/Tachidesk/bin/kcef ]; then
    # make sure all files are always executable. KCEF (and our downloader) ensure this on creation, but if the flag is lost
    # at some point, CEF will die
    chmod -R a+x /home/suwayomi/.local/share/Tachidesk/bin/kcef 2>/dev/null || true
  fi
  export LD_PRELOAD=/home/suwayomi/.local/share/Tachidesk/bin/kcef/libcef.so
else
  command="java"
  echo "Suwayomi built without KCEF support, not starting Xvfb"
fi
if [ -f /opt/catch_abort.so ]; then
  export LD_PRELOAD="/opt/catch_abort.so $LD_PRELOAD"
fi
echo "LD_PRELOAD=$LD_PRELOAD"
exec $command -Duser.home=/home/suwayomi -jar "/home/suwayomi/startup/tachidesk_latest.jar";
