package suwayomi.tachidesk.graphql.types

import suwayomi.tachidesk.server.serverConfig

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
    // Suwayomi-Enhanced ships a single WebUI flavor — our own fork.
    // Releases on github.com/sebastianov92/Suwayomi-Enhanced-WebUI must
    // tag a build with the standard Suwayomi-WebUI-rXXX.zip filename.
    WEBUI(
        "WebUI",
        "https://github.com/sebastianov92/Suwayomi-Enhanced-WebUI",
        "https://raw.githubusercontent.com/sebastianov92/Suwayomi-Enhanced-WebUI/main/versionToServerVersionMapping.json",
        "https://api.github.com/repos/sebastianov92/Suwayomi-Enhanced-WebUI/releases/latest",
        "Suwayomi-WebUI",
    ),

    // CUSTOM kept as a fallback for power users who want to point at a
    // local build, but it's hidden from the WebUI settings dropdown.
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
