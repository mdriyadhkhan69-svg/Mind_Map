package com.example.mindmap.data

class NodeRepository(private val dao: NodeDao) {
    fun getAllNodes() = dao.getAllNodes()
    suspend fun insert(node: NodeEntity) = dao.insert(node)
    suspend fun update(node: NodeEntity) = dao.update(node)
    suspend fun delete(node: NodeEntity) = dao.delete(node)
}