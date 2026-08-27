package com.ayaka7452.daymate.data.db

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
    entities = [VaultEventEntity::class, VaultFolderEntity::class],
    version = 2,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultEventDao(): VaultEventDao
    abstract fun vaultFolderDao(): VaultFolderDao

    companion object {
        fun build(context: Context): VaultDatabase =
            Room.databaseBuilder(context, VaultDatabase::class.java, "vault.db")
                // Alpha: 结构变更时直接重建（会清空 Vault 数据），正式版需替换为真实 Migration
                .fallbackToDestructiveMigration()
                .build()
    }
}
