package com.example.ircclient.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "buffer_events",
    indices = [
        Index(value = ["buffer_key", "time"], unique = false),
        Index(value = ["time"], unique = false)
    ]
)
data class BufferEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "buffer_key")
    val bufferKey: String,
    @ColumnInfo(name = "event_type")
    val eventType: String,
    @ColumnInfo(name = "payload")
    val payload: String,
    @ColumnInfo(name = "time")
    val time: Long
)
