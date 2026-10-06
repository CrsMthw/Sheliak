package com.crsmthw.sheliak.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.crsmthw.sheliak.data.db.dao.HistoryDao
import com.crsmthw.sheliak.data.db.dao.IndexDao
import com.crsmthw.sheliak.data.db.dao.LibraryDao
import com.crsmthw.sheliak.data.db.dao.PlaylistDao
import com.crsmthw.sheliak.data.db.dao.ProviderDao
import com.crsmthw.sheliak.data.db.dao.QueueDao
import com.crsmthw.sheliak.data.db.entity.AlbumEntity
import com.crsmthw.sheliak.data.db.entity.AlbumFts
import com.crsmthw.sheliak.data.db.entity.ArtistEntity
import com.crsmthw.sheliak.data.db.entity.ArtistFts
import com.crsmthw.sheliak.data.db.entity.DownloadEntity
import com.crsmthw.sheliak.data.db.entity.LyricsCacheEntity
import com.crsmthw.sheliak.data.db.entity.PlayHistoryEntity
import com.crsmthw.sheliak.data.db.entity.PlaylistEntity
import com.crsmthw.sheliak.data.db.entity.PlaylistEntryEntity
import com.crsmthw.sheliak.data.db.entity.ProviderEntity
import com.crsmthw.sheliak.data.db.entity.QueueEntryEntity
import com.crsmthw.sheliak.data.db.entity.QueueStateEntity
import com.crsmthw.sheliak.data.db.entity.TrackEntity
import com.crsmthw.sheliak.data.db.entity.TrackFts

/**
 * The merged index, `sheliak.db` (docs/DESIGN.md §2, as built: docs/INDEX.md). The schema is exported to
 * `app/schemas/` by the Room Gradle plugin and committed; any change bumps [version] with a migration checked
 * against the exported schema it starts from.
 */
@Database(
    entities = [
        ProviderEntity::class,
        ArtistEntity::class,
        AlbumEntity::class,
        TrackEntity::class,
        TrackFts::class,
        AlbumFts::class,
        ArtistFts::class,
        PlaylistEntity::class,
        PlaylistEntryEntity::class,
        DownloadEntity::class,
        LyricsCacheEntity::class,
        QueueEntryEntity::class,
        QueueStateEntity::class,
        PlayHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SheliakDatabase : RoomDatabase() {

    abstract fun providerDao(): ProviderDao
    abstract fun libraryDao(): LibraryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun indexDao(): IndexDao
    abstract fun historyDao(): HistoryDao
    abstract fun queueDao(): QueueDao

    companion object {
        const val NAME: String = "sheliak.db"

        /** Opened lazily by Room on the first query, never on the calling thread. */
        fun build(context: Context): SheliakDatabase =
            Room.databaseBuilder(context.applicationContext, SheliakDatabase::class.java, NAME).build()
    }
}
