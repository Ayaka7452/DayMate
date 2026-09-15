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
        VaultFolderEntity::class,
        CycleLogEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class DayMateDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun folderDao(): FolderDao
    abstract fun vaultEventDao(): VaultEventDao
    abstract fun vaultFolderDao(): VaultFolderDao
    abstract fun cycleLogDao(): CycleLogDao

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

        /**
         * v3 -> v4：事件与 Vault 事件新增「对照天数」refDays（可空）。
         * 注意：历史版本发布包漏把 version 升到 4，导致部分设备的 v3 库已带 refDays 列，
         * 故此处必须幂等——列已存在时跳过，否则 ALTER TABLE 会因重复列名而崩溃。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                addColumnIfMissing(db, "events", "refDays", "ALTER TABLE events ADD COLUMN refDays INTEGER")
                addColumnIfMissing(db, "vault_events", "refDays", "ALTER TABLE vault_events ADD COLUMN refDays INTEGER")
            }
        }

        /** v4 -> v5：事件与 Vault 事件新增「显示单位」displayUnit（可空，DAY/MONTH/YEAR，null 按天）。 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                addColumnIfMissing(db, "events", "displayUnit", "ALTER TABLE events ADD COLUMN displayUnit TEXT")
                addColumnIfMissing(db, "vault_events", "displayUnit", "ALTER TABLE vault_events ADD COLUMN displayUnit TEXT")
            }
        }

        /** v5 -> v6：事件与 Vault 事件新增「循环规则」repeatRule（可空，WEEKLY/MONTHLY/YEARLY，null 不循环）。 */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                addColumnIfMissing(db, "events", "repeatRule", "ALTER TABLE events ADD COLUMN repeatRule TEXT")
                addColumnIfMissing(db, "vault_events", "repeatRule", "ALTER TABLE vault_events ADD COLUMN repeatRule TEXT")
            }
        }

        /** v6 -> v7：事件与 Vault 事件新增「跟随节日」linkedFestival（可空，节日名）。 */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                addColumnIfMissing(db, "events", "linkedFestival", "ALTER TABLE events ADD COLUMN linkedFestival TEXT")
                addColumnIfMissing(db, "vault_events", "linkedFestival", "ALTER TABLE vault_events ADD COLUMN linkedFestival TEXT")
            }
        }

        /** v7 -> v8：周期管家——事件新增 specialType 标记 + 新表 cycle_logs（经期登记记录）。 */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                addColumnIfMissing(db, "events", "specialType", "ALTER TABLE events ADD COLUMN specialType TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cycle_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        startDateEpochDay INTEGER NOT NULL,
                        periodDays INTEGER NOT NULL DEFAULT 5,
                        note TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v8 -> v9：移除僵尸列 repeatYearly（events / vault_events 各一列）。
         *
         * 该列自 v6 引入 repeatRule（WEEKLY/MONTHLY/YEARLY）后已**无任何业务语义**：全项目没有任何一处
         * 读取它做判断，仅 VaultBridge 搬运事件时原样透传、旧 vault.db 一次性迁移时赋值。剔除它可让每行
         * 少存一个 INTEGER，并让表结构与代码重新对齐。
         *
         * 实现为「建新表 → 逐列复制 → 删旧表 → 改名 → 重建索引」，三个要点：
         *  1. **按旧表实际存在的列拼 SELECT**——历史发布包出现过库结构与版本号不同步的设备
         *     （见 MIGRATION_3_4 的注释），缺列一律以默认值补齐，避免 `no such column` 让迁移失败。
         *     由于已移除破坏性回退，迁移失败将直接抛出而非静默清库，故健壮性是硬要求。
         *  2. **语义搬运不丢功能**——旧数据中 repeatYearly=1 且 repeatRule 为空的行，改写为
         *     repeatRule='YEARLY'，「每年重复」的行为原样保留。
         *  3. **顺带修复悬空外键**——folderId 指向已不存在的文件夹时复制为 NULL：既清理了脏数据，
         *     也避免外键约束让整条 INSERT 失败。
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                rebuildWithoutRepeatYearly(
                    db = db,
                    table = "events",
                    parentTable = "folders",
                    targetColumns = listOf(
                        "id", "title", "targetDateEpochDay", "note", "color", "folderId",
                        "refDays", "displayUnit", "repeatRule", "linkedFestival", "specialType",
                        "sortIndex", "isPinned", "isDeleted", "deletedAt", "createdAt", "updatedAt"
                    ),
                    fallbacks = mapOf(
                        "title" to "''",
                        "targetDateEpochDay" to "0",
                        "sortIndex" to "0",
                        "isPinned" to "0",
                        "isDeleted" to "0",
                        "deletedAt" to "0",
                        "createdAt" to "0",
                        "updatedAt" to "0"
                    ),
                    createSql = """
                        CREATE TABLE events__v9 (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            title TEXT NOT NULL,
                            targetDateEpochDay INTEGER NOT NULL,
                            note TEXT,
                            color INTEGER,
                            folderId INTEGER,
                            refDays INTEGER,
                            displayUnit TEXT,
                            repeatRule TEXT,
                            linkedFestival TEXT,
                            specialType TEXT,
                            sortIndex INTEGER NOT NULL DEFAULT 0,
                            isPinned INTEGER NOT NULL DEFAULT 0,
                            isDeleted INTEGER NOT NULL DEFAULT 0,
                            deletedAt INTEGER NOT NULL DEFAULT 0,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            FOREIGN KEY(folderId) REFERENCES folders(id) ON DELETE SET NULL
                        )
                    """.trimIndent(),
                    indexSql = "CREATE INDEX IF NOT EXISTS index_events_folderId ON events(folderId)"
                )
                rebuildWithoutRepeatYearly(
                    db = db,
                    table = "vault_events",
                    parentTable = "vault_folders",
                    targetColumns = listOf(
                        "id", "title", "targetDateEpochDay", "note", "color", "folderId",
                        "refDays", "displayUnit", "repeatRule", "linkedFestival",
                        "sortIndex", "isPinned", "createdAt", "updatedAt"
                    ),
                    fallbacks = mapOf(
                        "title" to "''",
                        "targetDateEpochDay" to "0",
                        "sortIndex" to "0",
                        "isPinned" to "0",
                        "createdAt" to "0",
                        "updatedAt" to "0"
                    ),
                    createSql = """
                        CREATE TABLE vault_events__v9 (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            title TEXT NOT NULL,
                            targetDateEpochDay INTEGER NOT NULL,
                            note TEXT,
                            color INTEGER,
                            folderId INTEGER,
                            refDays INTEGER,
                            displayUnit TEXT,
                            repeatRule TEXT,
                            linkedFestival TEXT,
                            sortIndex INTEGER NOT NULL DEFAULT 0,
                            isPinned INTEGER NOT NULL DEFAULT 0,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            FOREIGN KEY(folderId) REFERENCES vault_folders(id) ON DELETE SET NULL
                        )
                    """.trimIndent(),
                    indexSql = "CREATE INDEX IF NOT EXISTS index_vault_events_folderId ON vault_events(folderId)"
                )
            }
        }

        /**
         * 重建一张事件表并丢弃 repeatYearly 列，见 [MIGRATION_8_9] 的说明。
         * @param table 目标表名（重建完成后仍是该名字）。
         * @param parentTable 外键指向的父表（folders / vault_folders），用于剔除悬空引用。
         * @param targetColumns 重建后应有的列（顺序即复制顺序）。
         * @param fallbacks 旧表缺列时的兜底表达式（未列出的一律按 NULL）。
         * @param createSql 新表的建表语句（表名须为 `<table>__v9`）。
         * @param indexSql 重建索引语句（DROP TABLE 会连带删掉旧索引）。
         */
        private fun rebuildWithoutRepeatYearly(
            db: androidx.sqlite.db.SupportSQLiteDatabase,
            table: String,
            parentTable: String,
            targetColumns: List<String>,
            fallbacks: Map<String, String>,
            createSql: String,
            indexSql: String
        ) {
            val existing = mutableSetOf<String>()
            db.query("PRAGMA table_info($table)").use { c ->
                while (c.moveToNext()) existing.add(c.getString(1))
            }
            // 表不存在或结构异常（没有主键列）→ 交给 Room 按当前实体自行建表，不在此处硬造
            if ("id" !in existing) return

            val tmp = "${table}__v9"

            fun exprFor(col: String): String {
                if (col == "folderId") {
                    // 悬空文件夹引用置空：修复脏数据 + 避免复制时撞外键约束
                    return if (col in existing) {
                        "CASE WHEN $col IN (SELECT id FROM $parentTable) THEN $col ELSE NULL END"
                    } else "NULL"
                }
                val raw = if (col in existing) col else (fallbacks[col] ?: "NULL")
                if (col == "repeatRule" && "repeatYearly" in existing) {
                    // 旧的「每年重复」布尔位 → repeatRule='YEARLY'（表结构改变，行为不变）
                    return "CASE WHEN COALESCE(repeatYearly, 0) = 1 AND ($raw) IS NULL " +
                        "THEN 'YEARLY' ELSE $raw END"
                }
                return raw
            }

            db.execSQL(createSql)
            db.execSQL(
                "INSERT INTO $tmp (" + targetColumns.joinToString(", ") + ") " +
                    "SELECT " + targetColumns.joinToString(", ") { exprFor(it) } + " FROM $table"
            )
            db.execSQL("DROP TABLE $table")
            db.execSQL("ALTER TABLE $tmp RENAME TO $table")
            db.execSQL(indexSql)
        }

        /** 幂等加列：列已存在时跳过（防重复 ALTER TABLE 崩溃）。 */
        private fun addColumnIfMissing(
            db: androidx.sqlite.db.SupportSQLiteDatabase,
            table: String,
            column: String,
            ddl: String
        ) {
            val existing = mutableListOf<String>()
            db.query("PRAGMA table_info($table)").use { c ->
                while (c.moveToNext()) existing.add(c.getString(1))
            }
            if (column !in existing) db.execSQL(ddl)
        }

        fun build(context: Context): DayMateDatabase {
            // 路线 A：主库永远建在应用内部沙盒（getDatabasePath("daymate.db")），
            // 不直接碰外部存储路径，因此不需要 MANAGE_EXTERNAL_STORAGE 等任何存储权限。
            // 用户数据「备份到自选文件夹」由 StorageBackup 通过 SAF 持久化 URI 完成，
            // 与 Room 主库的物理位置解耦。
            //
            // 注意：此处**刻意不使用 fallbackToDestructiveMigration()**。
            // 它会在「库结构与实体类不一致且无对应迁移」时直接删库重建——数据静默蒸发，
            // 与「绝不丢用户数据」的原则冲突。改为让迁移必须显式提供：宁可抛错暴露问题
            // （数据仍在文件里，可后续修复），也不要静默清空用户的倒数日。
            return Room.databaseBuilder(context, DayMateDatabase::class.java, "daymate.db")
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9
                )
                .build()
        }
    }
}
