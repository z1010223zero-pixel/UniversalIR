package com.zero.universalir.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "devices")
data class RemoteDeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val room: String = "",
    val type: String,
    val brand: String = "",
    val model: String = "",
    /** 码库条目 id（CodeLibrary.Entry.id），flipper 或 smartir_climate。 */
    val libraryFileId: String? = null,
    /** flipper 类：试码命中的主信号名（开机/开关）。 */
    val boundSignalName: String? = null,
    // 空调当前状态
    val acPower: Boolean = false,
    val acMode: String? = null,
    val acFan: String? = null,
    val acTemp: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "buttons", indices = [Index("deviceId")])
data class DeviceButtonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: Long,
    val label: String,
    /** 库内信号名（flipper 码表）。 */
    val signalName: String? = null,
    val sortOrder: Int = 0,
)

@Dao
interface RemoteDao {
    @Query("SELECT * FROM devices ORDER BY createdAt DESC")
    fun devices(): Flow<List<RemoteDeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :id")
    fun deviceFlow(id: Long): Flow<RemoteDeviceEntity?>

    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun device(id: Long): RemoteDeviceEntity?

    @Query("SELECT * FROM devices")
    suspend fun allDevices(): List<RemoteDeviceEntity>

    @Insert
    suspend fun insertDevice(d: RemoteDeviceEntity): Long

    @Update
    suspend fun updateDevice(d: RemoteDeviceEntity)

    /** 空调状态定向更新：只写空调列，避免并发下用陈旧快照整行覆盖。 */
    @Query(
        "UPDATE devices SET acPower = :acPower, acMode = :acMode, " +
            "acFan = :acFan, acTemp = :acTemp WHERE id = :id"
    )
    suspend fun updateAcState(id: Long, acPower: Boolean, acMode: String?, acFan: String?, acTemp: Int?)

    /** 仅更新空调电源位。 */
    @Query("UPDATE devices SET acPower = :acPower WHERE id = :id")
    suspend fun updateAcPower(id: Long, acPower: Boolean)

    @Delete
    suspend fun deleteDevice(d: RemoteDeviceEntity)

    @Query("SELECT * FROM buttons WHERE deviceId = :deviceId ORDER BY sortOrder")
    fun buttonsFlow(deviceId: Long): Flow<List<DeviceButtonEntity>>

    @Insert
    suspend fun insertButtons(buttons: List<DeviceButtonEntity>)

    @Delete
    suspend fun deleteButton(button: DeviceButtonEntity)

    @Query("DELETE FROM buttons WHERE deviceId = :deviceId")
    suspend fun deleteButtonsOf(deviceId: Long)

    /** 删除设备与其按键：单事务执行，进程中断也不会留下孤儿按键。 */
    @Transaction
    suspend fun deleteDeviceWithButtons(device: RemoteDeviceEntity) {
        deleteButtonsOf(device.id)
        deleteDevice(device)
    }
}

@Database(
    entities = [RemoteDeviceEntity::class, DeviceButtonEntity::class],
    version = 1,
    // schema JSON 提交到 app/schemas/，为将来迁移留基准
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): RemoteDao
}
