package com.example.mindmap.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes")
    fun getAllNodes(): Flow<List<NodeEntity>>

    @Insert
    suspend fun insert(node: NodeEntity): Long

    @Update
    suspend fun update(node: NodeEntity)

    @Delete
    suspend fun delete(node: NodeEntity)
}