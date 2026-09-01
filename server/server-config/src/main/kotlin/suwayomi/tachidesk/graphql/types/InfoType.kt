package suwayomi.tachidesk.graphql.types

import suwayomi.tachidesk.server.util.PlatformInfo

data class JvmInfo(
    val javaVersion: String,
    val vmName: String,
    val vmVersion: String,
    val vmVendor: String,
)

data class OSInfo(
    val name: String,
    val version: String,
    val build: String? = null,
)

data class PlatformInfo(
    val os: OSInfo,
    val arch: String,
    val headless: Boolean,
    val jvm: JvmInfo,
) {
    constructor(platformInfo: PlatformInfo) : this(
        os = platformInfo.os.details,
        arch = platformInfo.arch.name,
        headless = platformInfo.headless,
        jvm = platformInfo.jvm,
    )
}
