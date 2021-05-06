package ir.armor.tachidesk.model.database.migration.lib

abstract class Migration {
    val name: String
    val version: Int

    init {
        val groups = Regex("^M(\\d+)_(.*)$").matchEntire(this::class.simpleName!!)?.groupValues
            ?: throw IllegalArgumentException("Migration class name doesn't match convention")
        version = groups[1].toInt()
        name = groups[2]
    }

    abstract fun run()
}
