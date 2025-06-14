package suwayomi.tachidesk.graphql.types

import suwayomi.tachidesk.server.serverConfig

data class AboutWebUI(
    val channel: WebUIChannel,
    val tag: String,
)

data class WebUIUpdateCheck(
    val channel: WebUIChannel,
    val tag: String,
    val updateAvailable: Boolean,
)

data class WebUIUpdateInfo(
    val channel: WebUIChannel,
    val tag: String,
)

enum class UpdateState {
    IDLE,
    DOWNLOADING,
    FINISHED,
    ERROR,
}

data class WebUIUpdateStatus(
    val info: WebUIUpdateInfo,
    val state: UpdateState,
    val progress: Int,
)

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
        "https://raw.githubusercontent.com/Suwayomi/Suwayomi-VUI/main/versionToServerVersionMapping.json",
        "https://api.github.com/repos/Suwayomi/Suwayomi-VUI/releases/latest",
        "Suwayomi-VUI-Web",
    ),

    CUSTOM(
        "Custom",
        "repoURL",
        "versionMappingUrl",
        "latestReleaseInfoURL",
        "baseFileName",
    ),
    ;

    companion object {
        val default: WebUIFlavor = WEBUI

        fun from(value: String): WebUIFlavor = entries.find { it.uiName == value } ?: default

        val current: WebUIFlavor
            get() = serverConfig.webUIFlavor.value
    }
}
