package com.example.mindmap.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LineDao {
    @Query("SELECT * FROM lines")
    fun getAllLines(): Flow<List<LineEntity>>

    @Insert
    suspend fun insert(line: LineEntity): Long

    @Update
    suspend fun update(line: LineEntity)

    @Delete
    suspend fun delete(line: LineEntity)
}