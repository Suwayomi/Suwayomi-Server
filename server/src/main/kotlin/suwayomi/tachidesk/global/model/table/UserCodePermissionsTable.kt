package suwayomi.tachidesk.global.model.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object UserCodePermissionsTable : Table() {
    val userCode = reference("user_code_id", UserCodeTable, ReferenceOption.CASCADE).index()
    val permission = varchar("permission", 128)

    init {
        uniqueIndex(userCode, permission)
    }
}
