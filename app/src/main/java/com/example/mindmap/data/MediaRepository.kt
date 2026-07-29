package com.example.mindmap.data

class MediaRepository(private val dao: MediaDao) {
    fun getAllMedia() = dao.getAllMedia()

    suspend fun replaceNodeMedia(media: MediaEntity) {
        dao.removeNodeMedia(media.nodeId, media.type)
        dao.insert(media)
    }

    suspend fun update(media: MediaEntity) = dao.update(media)

    suspend fun delete(mediaId: Long) = dao.delete(mediaId)
}
