package suwayomi.tachidesk.server.settings

enum class SettingGroup(
    val value: String,
) {
    NETWORK("Network"),
    DATABASE("Database"),
    PROXY("Proxy"),
    WEB_UI("WebUI"),
    DOWNLOADER("Downloader"),
    EXTENSION("Extension/Source"),
    LIBRARY_UPDATES("Library updates"),
    AUTH("Authentication"),
    MISC("Misc"),
    BACKUP("Backup"),
    LOCAL_SOURCE("Local source"),
    CLOUDFLARE("Cloudflare"),
    OPDS("OPDS"),
    KOREADER_SYNC("KOReader sync"),
    ;

    override fun toString(): String = value
}
