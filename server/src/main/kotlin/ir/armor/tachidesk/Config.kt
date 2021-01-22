package ir.armor.tachidesk

import net.harawata.appdirs.AppDirsFactory

object Config {
    val dataRoot = AppDirsFactory.getInstance().getUserDataDir("Tachidesk", null, null)
    val extensionsRoot = "$dataRoot/extensions"
}
