package suwayomi.tachidesk.event

import suwayomi.tachidesk.event.enums.EventType
import java.util.UUID

data class Event<T>(
    val id: UUID? = UUID.randomUUID(),
    val type: EventType,
    val entity: T
)
