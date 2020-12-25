package ir.armor.tachidesk.util

import ir.armor.tachidesk.Config
import ir.armor.tachidesk.database.makeDataBaseTables
import java.io.File

fun applicationSetup() {
    // make dirs we need
    File(Config.dataRoot).mkdirs()
    File(Config.extensionsRoot).mkdirs()


    makeDataBaseTables()
}