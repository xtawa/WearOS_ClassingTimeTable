package com.classing.shared.sync

import java.time.Instant

enum class SyncMode { FULL, INCREMENTAL }

data class SyncCursor(
    val lastSyncedAt: Instant?,
    val token: String?,
)

data class SyncEnvelope(
    val mode: SyncMode,
    val changes: List<SyncChange>,
    val cursor: SyncCursor,
)

data class SyncChange(
    val entityType: String,
    val entityId: Long,
    val op: Operation,
    val payload: String,
    val version: Long,
)

enum class Operation { UPSERT, DELETE }

enum class ConflictStrategy { LAST_WRITE_WINS, PHONE_AUTHORITATIVE }

interface SyncGateway {
    fun pull(mode: SyncMode, cursor: SyncCursor): Result<SyncEnvelope>
}

interface SyncLocalStore {
    fun apply(change: SyncChange, strategy: ConflictStrategy)
    fun getCursor(): SyncCursor
    fun saveCursor(cursor: SyncCursor)
}

class SyncCoordinator(
    private val gateway: SyncGateway,
    private val localStore: SyncLocalStore,
    private val strategy: ConflictStrategy,
) {
    fun sync(mode: SyncMode, maxRetry: Int = 2): Result<Int> {
        val cursor = if (mode == SyncMode.FULL) SyncCursor(null, null) else localStore.getCursor()
        repeat(maxRetry + 1) { attempt ->
            val pulled = gateway.pull(mode, cursor)
            if (pulled.isSuccess) {
                val envelope = pulled.getOrThrow()
                envelope.changes.forEach { localStore.apply(it, strategy) }
                localStore.saveCursor(envelope.cursor.copy(lastSyncedAt = Instant.now()))
                return Result.success(envelope.changes.size)
            }
            if (attempt == maxRetry) return Result.failure(pulled.exceptionOrNull()!!)
        }
        return Result.success(0)
    }
}
