package com.crsmthw.sheliak.data.provider

import com.crsmthw.sheliak.data.db.dao.LibraryDao
import com.crsmthw.sheliak.data.db.toIndexTrack
import com.crsmthw.sheliak.domain.TrackKey

/** [IndexReader] over the tracks table. */
class RoomIndexReader(private val libraryDao: LibraryDao) : IndexReader {
    override suspend fun track(key: TrackKey): IndexTrack? =
        libraryDao.track(key.providerId, key.itemId)?.toIndexTrack()
}
