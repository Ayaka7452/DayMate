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
    /** 跟随的节日名（来自节假日数据源）：目标日期过后自动锚定到该节日的下一次日期。优先于 repeatRule。 */
    val linkedFestival: String? = null,
    /** 功能快捷事件标记：null=普通事件；"cycle"=周期管家入口（点击进入周期管家而非详情页）。 */
    val specialType: String? = null,
    val sortIndex: Int = 0,
    val isPinned: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** 周期管家：一次经期的登记记录（startDate 为该次经期首日，periodDays 为该次持续天数）。 */
@Entity(tableName = "cycle_logs")
data class CycleLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 经期首日（epoch day）。 */
    val startDateEpochDay: Long,
    /** 该次经期持续天数（2~10 合理区间，登记后可调整）。 */
    val periodDays: Int = 5,
    val note: String? = null,
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
    /** 跟随的节日名（来自节假日数据源）。与主表 events.linkedFestival 同义。 */
    val linkedFestival: String? = null,
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
