package com.example.mindmap.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SectionDao {
    @Query("SELECT * FROM sections ORDER BY orderIndex")
    fun getAllSections(): Flow<List<SectionEntity>>

    @Insert
    suspend fun insert(section: SectionEntity): Long

    @Update
    suspend fun update(section: SectionEntity)

    @Transaction
    suspend fun updateAll(sections: List<SectionEntity>) {
        sections.forEach { update(it) }
    }

    @Delete
    suspend fun delete(section: SectionEntity)
}
