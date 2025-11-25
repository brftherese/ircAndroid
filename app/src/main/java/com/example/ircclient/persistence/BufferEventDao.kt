package com.example.ircclient.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BufferEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(events: List<BufferEventEntity>)

    @Query("SELECT DISTINCT buffer_key FROM buffer_events")
    suspend fun bufferKeys(): List<String>

    @Query(
        """
        SELECT * FROM buffer_events
        WHERE buffer_key = :bufferKey
        ORDER BY time DESC
        LIMIT :limit
        """
    )
    suspend fun latestForBuffer(bufferKey: String, limit: Int): List<BufferEventEntity>

    @Query("DELETE FROM buffer_events WHERE time < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query(
        """
        DELETE FROM buffer_events
        WHERE buffer_key = :bufferKey AND id NOT IN (
            SELECT id FROM buffer_events
            WHERE buffer_key = :bufferKey
            ORDER BY time DESC
            LIMIT :maxRows
        )
        """
    )
    suspend fun trimToMax(bufferKey: String, maxRows: Int): Int
}
