package com.ayaka7452.daymate.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import java.io.File

@Database(
    entities = [EventEntity::class, FolderEntity::class],
    version = 2,
    exportSchema = false
)
abstract class DayMateDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun folderDao(): FolderDao

    companion object {
        /** v1 -> v2：新增回收站软删除字段。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE events ADD COLUMN deletedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE folders ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE folders ADD COLUMN deletedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context, file: File? = null): DayMateDatabase {
            val builder = Room.databaseBuilder(context, DayMateDatabase::class.java, "daymate.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
            return if (file == null) builder.build()
            else builder.createFromFile(file).build()
        }
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
        fun build(context: Context, file: File? = null): VaultDatabase {
            val builder = Room.databaseBuilder(context, VaultDatabase::class.java, "vault.db")
                // Alpha: 结构变更时直接重建（会清空 Vault 数据），正式版需替换为真实 Migration
                .fallbackToDestructiveMigration()
            return if (file == null) builder.build()
            else builder.createFromFile(file).build()
        }
    }
}
