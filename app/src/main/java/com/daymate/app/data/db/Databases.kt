package com.daymate.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [EventEntity::class, FolderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DayMateDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun folderDao(): FolderDao

    companion object {
        fun build(context: Context): DayMateDatabase =
            Room.databaseBuilder(context, DayMateDatabase::class.java, "daymate.db").build()
    }
}

@Database(
    entities = [VaultEventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultEventDao(): VaultEventDao

    companion object {
        fun build(context: Context): VaultDatabase =
            Room.databaseBuilder(context, VaultDatabase::class.java, "vault.db").build()
    }
}
