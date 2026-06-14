package suwayomi.tachidesk.graphql.types

import suwayomi.tachidesk.server.serverConfig

enum class WebUIInterface {
    BROWSER,
    ELECTRON,
}

enum class WebUIFlavor(
    val uiName: String,
    val repoUrl: String,
    val latestReleaseInfoUrl: String,
    val baseFileName: String,
) {
    WEBUI(
        "WebUI",
        "https://github.com/vtorres-t/Suwayomi-WebUI",
        "https://api.github.com/repos/vtorres-t/Suwayomi-WebUI/releases/latest",
        "Suwayomi-WebUI",
    ),

    VUI(
        "VUI",
        "https://github.com/vtorres-t/Suwayomi-VUI",
        "https://api.github.com/repos/vtorres-t/Suwayomi-VUI/releases/latest",
        "Suwayomi-VUI-Web",
    ),

    CUSTOM(
        "Custom",
        "repoUrl",
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
