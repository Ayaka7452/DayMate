package com.ayaka7452.daymate.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import java.io.File

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

        fun build(context: Context, file: File? = null): DayMateDatabase {
            // 关键修复：不要使用 createFromFile(file)。
            // Room 2.6.x 的 createFromFile 只是“把已有文件复制到内部默认路径”，
            // 源文件必须已存在，否则首次选择文件夹（外部还没有 daymate.db）会抛
            // FileNotFoundException 导致进程崩溃；且它始终把 DB 建在内部存储，
            // 并不真正落在用户所选目录。
            //
            // 这里改为：把外部文件的【绝对路径】直接作为 Room 的数据库名。
            // Room 会把这个 name 透传给框架 SQLiteOpenHelper，而
            // Context.getDatabasePath(绝对路径) 会原样返回该绝对路径，
            // 于是数据库真正创建/打开在用户所选目录，数据真正落盘到外部存储。
            val name = file?.absolutePath ?: "daymate.db"
            file?.parentFile?.mkdirs()
            return Room.databaseBuilder(context, DayMateDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}

/**
 * 仅用于「旧版独立 vault.db」的一次性明文迁移读取（[com.ayaka7452.daymate.DayMateApp] 启动时）。
 * 合并后正常运行不再使用本类；其 schema 必须与历史 vault.db 一致。
 */
@Database(
    entities = [VaultEventEntity::class, VaultFolderEntity::class],
    version = 2,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultEventDao(): VaultEventDao
    abstract fun vaultFolderDao(): VaultFolderDao

    companion object {
        fun buildForMigration(context: Context, file: File): VaultDatabase {
            return Room.databaseBuilder(context, VaultDatabase::class.java, "vault_migration_tmp")
                .createFromFile(file)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
