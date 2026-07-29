package com.example.mindmap.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media ORDER BY id DESC")
    fun getAllMedia(): Flow<List<MediaEntity>>

    @Insert
    suspend fun insert(media: MediaEntity): Long

    @Update
    suspend fun update(media: MediaEntity)

    @Query("DELETE FROM media WHERE nodeId = :nodeId AND type = :type")
    suspend fun removeNodeMedia(nodeId: Long, type: MediaType)

    @Query("DELETE FROM media WHERE id = :mediaId")
    suspend fun delete(mediaId: Long)
}
