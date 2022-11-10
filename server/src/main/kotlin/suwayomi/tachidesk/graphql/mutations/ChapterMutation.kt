package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.server.extensions.getValuesFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.types.ChapterType
import suwayomi.tachidesk.manga.model.table.ChapterMetaTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import java.time.Instant
import java.util.concurrent.CompletableFuture

class ChapterMutation {
    data class MetaTypeInput(
        val key: String,
        val value: String?
    )

    data class ChapterAttributesInput(
        val isBookmarked: Boolean? = null,
        val isRead: Boolean? = null,
        val lastPageRead: Int? = null,
        val meta: List<MetaTypeInput>? = null
    )

    data class UpdateChapterInput(
        val ids: List<Int>,
        val attributes: ChapterAttributesInput
    )

    fun updateChapters(dataFetchingEnvironment: DataFetchingEnvironment, input: UpdateChapterInput): CompletableFuture<List<ChapterType>> {
        val (ids, attributes) = input

        transaction {
            if (attributes.isRead != null || attributes.isBookmarked != null || attributes.lastPageRead != null) {
                val now = Instant.now().epochSecond
                ChapterTable.update({ ChapterTable.id inList ids }) { update ->
                    attributes.isRead?.also {
                        update[isRead] = it
                    }
                    attributes.isBookmarked?.also {
                        update[isBookmarked] = it
                    }
                    attributes.lastPageRead?.also {
                        update[lastPageRead] = it
                        update[lastReadAt] = now
                    }
                }
            }

            if (attributes.meta != null) {
                attributes.meta.forEach { metaItem ->
                    // Delete any existing values
                    // Even when updating, it is easier to just delete all and create new
                    ChapterMetaTable.deleteWhere {
                        (key eq metaItem.key) and (ref inList ids)
                    }
                    if (metaItem.value != null) {
                        ChapterMetaTable.batchInsert(ids) { chapterId ->
                            this[ChapterMetaTable.ref] = chapterId
                            this[ChapterMetaTable.key] = metaItem.key
                            this[ChapterMetaTable.value] = metaItem.value
                        }
                    }
                }
            }
        }

        return dataFetchingEnvironment.getValuesFromDataLoader<Int, ChapterType>("ChapterDataLoader", ids)
    }
}
