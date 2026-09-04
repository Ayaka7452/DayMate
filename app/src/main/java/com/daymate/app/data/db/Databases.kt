package com.ayaka7452.daymate.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        EventEntity::class,
        FolderEntity::class,
        VaultEventEntity::class,
        VaultFolderEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class DayMateDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun folderDao(): FolderDao
    abstract fun vaultEventDao(): VaultEventDao
    abstract fun vaultFolderDao(): VaultFolderDao

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

        /** v2 -> v3：把 Vault 表并入主库（去掉独立 vault.db）。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE vault_folders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        icon TEXT,
                        color INTEGER,
                        sortIndex INTEGER NOT NULL DEFAULT 0,
                        isPinned INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE vault_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        targetDateEpochDay INTEGER NOT NULL,
                        repeatYearly INTEGER NOT NULL DEFAULT 0,
                        note TEXT,
                        color INTEGER,
                        folderId INTEGER,
                        sortIndex INTEGER NOT NULL DEFAULT 0,
                        isPinned INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(folderId) REFERENCES vault_folders(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX index_vault_events_folderId ON vault_events(folderId)")
            }
        }

        /** v3 -> v4：事件与 Vault 事件新增「对照天数」refDays（可空，用于已过日期显示 X/N）。 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN refDays INTEGER")
                db.execSQL("ALTER TABLE vault_events ADD COLUMN refDays INTEGER")
            }
        }

        fun build(context: Context): DayMateDatabase {
            // 路线 A：主库永远建在应用内部沙盒（getDatabasePath("daymate.db")），
            // 不直接碰外部存储路径，因此不需要 MANAGE_EXTERNAL_STORAGE 等任何存储权限。
            // 用户数据「备份到自选文件夹」由 StorageBackup 通过 SAF 持久化 URI 完成，
            // 与 Room 主库的物理位置解耦。
            return Room.databaseBuilder(context, DayMateDatabase::class.java, "daymate.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
