package com.ayaka7452.daymate.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String? = null,
    val color: Int? = null,
    val sortIndex: Int = 0,
    val isPinned: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("folderId")]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val targetDateEpochDay: Long,
    val repeatYearly: Boolean = false,
    val note: String? = null,
    val color: Int? = null,
    val folderId: Long? = null,
    /**
     * 对照数值：可选。目标日期已过去时显示「已过 X/N」。
     * 单位跟随 displayUnit（按天显示时是天数，按月/按年显示时即月数/年数），由表单保证。
     */
    val refDays: Int? = null,
    /** 倒计时显示单位：DAY/MONTH/YEAR，null 视为 DAY（按天）。随时可在编辑页更改。 */
    val displayUnit: String? = null,
    /** 循环规则：WEEKLY/MONTHLY/YEARLY，null = 不循环。目标日期过后自动锚定到下一周期同日。 */
    val repeatRule: String? = null,
    val sortIndex: Int = 0,
    val isPinned: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "vault_events",
    foreignKeys = [
        ForeignKey(
            entity = VaultFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("folderId")]
)
data class VaultEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val targetDateEpochDay: Long,
    val repeatYearly: Boolean = false,
    val note: String? = null,
    val color: Int? = null,
    val folderId: Long? = null,
    /**
     * 对照数值：可选。目标日期已过去时显示「已过 X/N」。
     * 单位跟随 displayUnit（按天显示时是天数，按月/按年显示时即月数/年数），由表单保证。
     */
    val refDays: Int? = null,
    /** 倒计时显示单位：DAY/MONTH/YEAR，null 视为 DAY（按天）。随时可在编辑页更改。 */
    val displayUnit: String? = null,
    /** 循环规则：WEEKLY/MONTHLY/YEARLY，null = 不循环。与主表 events.repeatRule 同义。 */
    val repeatRule: String? = null,
    val sortIndex: Int = 0,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "vault_folders")
data class VaultFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String? = null,
    val color: Int? = null,
    val sortIndex: Int = 0,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
