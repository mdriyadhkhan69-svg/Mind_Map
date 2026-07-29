package com.example.mindmap.data

class LineRepository(private val dao: LineDao) {
    fun getAllLines() = dao.getAllLines()
    suspend fun insert(line: LineEntity) = dao.insert(line)
    suspend fun update(line: LineEntity) = dao.update(line)
    suspend fun delete(line: LineEntity) = dao.delete(line)
}