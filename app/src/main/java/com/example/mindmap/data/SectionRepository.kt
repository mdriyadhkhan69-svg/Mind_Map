package com.example.mindmap.data

class SectionRepository(private val dao: SectionDao) {
    fun getAllSections() = dao.getAllSections()
    suspend fun insert(section: SectionEntity) = dao.insert(section)
    suspend fun update(section: SectionEntity) = dao.update(section)
    suspend fun updateAll(sections: List<SectionEntity>) = dao.updateAll(sections)
    suspend fun delete(section: SectionEntity) = dao.delete(section)
}
