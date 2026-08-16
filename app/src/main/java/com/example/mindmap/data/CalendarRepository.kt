package com.example.mindmap.data

class CalendarRepository(private val dao: CalendarEventDao) {
    fun getAllEvents() = dao.getAllEvents()
    suspend fun insert(event: CalendarEventEntity) = dao.insert(event)
    suspend fun update(event: CalendarEventEntity) = dao.update(event)
    suspend fun delete(event: CalendarEventEntity) = dao.delete(event)
}