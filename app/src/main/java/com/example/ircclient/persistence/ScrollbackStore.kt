package com.example.ircclient.persistence

import com.example.ircclient.UiEvent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScrollbackStore(
    private val dao: BufferEventDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun append(bufferKey: String, event: UiEvent) {
        appendAll(bufferKey, listOf(event))
    }

    suspend fun appendAll(bufferKey: String, events: List<UiEvent>) {
        if (events.isEmpty()) return
        val entities = events.map { PersistedEventMapper.toEntity(bufferKey, it) }
        withContext(dispatcher) {
            dao.insert(entities)
            pruneInternal(bufferKey)
        }
    }

    suspend fun loadRecent(bufferKey: String, limit: Int): List<UiEvent> = withContext(dispatcher) {
        dao.latestForBuffer(bufferKey, limit)
            .mapNotNull(PersistedEventMapper::toUiEvent)
            .sortedBy { it.time }
    }

    suspend fun loadAll(limitPerBuffer: Int): Map<String, List<UiEvent>> = withContext(dispatcher) {
        dao.bufferKeys()
            .associateWith { key ->
                dao.latestForBuffer(key, limitPerBuffer)
                    .mapNotNull(PersistedEventMapper::toUiEvent)
                    .sortedBy { it.time }
            }
    }

    suspend fun pruneExpired() = withContext(dispatcher) {
        dao.deleteOlderThan(expirationCutoff())
    }

    private suspend fun pruneInternal(bufferKey: String) {
        val cutoff = expirationCutoff()
        dao.deleteOlderThan(cutoff)
        dao.trimToMax(bufferKey, MAX_ROWS_PER_BUFFER)
    }

    private fun expirationCutoff(): Long = now() - MAX_AGE_MS

    companion object {
        private const val MAX_ROWS_PER_BUFFER = 5_000
        private val MAX_AGE_MS = TimeUnit.DAYS.toMillis(30)
    }
}
