package com.nawzblaze.momentwise.data

import com.nawzblaze.momentwise.intervention.GuardSnapshot
import kotlinx.coroutines.flow.Flow

/** The one in-progress guard session, plus dismiss-for-today dates. */
interface GuardSessionRepository {
    val session: Flow<GuardSnapshot>

    suspend fun current(): GuardSnapshot

    suspend fun save(snapshot: GuardSnapshot)

    suspend fun update(transform: (GuardSnapshot) -> GuardSnapshot)
}
