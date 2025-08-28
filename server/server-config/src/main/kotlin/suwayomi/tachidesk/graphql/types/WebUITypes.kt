package suwayomi.tachidesk.graphql.types

enum class WebUIInterface {
    BROWSER,
    ELECTRON,
}

enum class WebUIChannel {
    BUNDLED, // the default webUI version bundled with the server release
    STABLE,
    PREVIEW,
    ;

    companion object {
        fun from(channel: String): WebUIChannel = entries.find { it.name.lowercase() == channel.lowercase() } ?: STABLE
    }
}

enum class WebUIFlavor(
    val uiName: String,
    val repoUrl: String,
    val versionMappingUrl: String,
    val latestReleaseInfoUrl: String,
    val baseFileName: String,
) {
    WEBUI(
        "WebUI",
        "https://github.com/Suwayomi/Suwayomi-WebUI-preview",
        "https://raw.githubusercontent.com/Suwayomi/Suwayomi-WebUI/master/versionToServerVersionMapping.json",
        "https://api.github.com/repos/Suwayomi/Suwayomi-WebUI-preview/releases/latest",
        "Suwayomi-WebUI",
    ),
    VUI(
        "VUI",
        "https://github.com/Suwayomi/Suwayomi-VUI",
        "https://raw.githubusercontent.com/Suwayomi/Suwayomi-VUI/master/versionToServerVersionMapping.json",
        "https://api.github.com/repos/Suwayomi/Suwayomi-VUI/releases/latest",
        "Suwayomi-VUI",
    ),
    CUSTOM("Custom", "", "", "", ""),
    ;

    companion object {
        fun from(flavor: String): WebUIFlavor = entries.find { it.name.lowercase() == flavor.lowercase() } ?: WEBUI
    }
}
