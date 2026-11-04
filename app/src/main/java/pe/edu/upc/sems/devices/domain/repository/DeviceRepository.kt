package pe.edu.upc.sems.devices.domain.repository

import pe.edu.upc.sems.devices.domain.model.Device
import pe.edu.upc.sems.devices.domain.model.DeviceDraft
import pe.edu.upc.sems.shared.network.ApiResult

interface DeviceRepository {
    suspend fun listDevices(userId: String): ApiResult<List<Device>>
    suspend fun getDevice(deviceId: String): ApiResult<Device>
    suspend fun createDevice(userId: String, draft: DeviceDraft): ApiResult<Device>
    suspend fun updateDevice(deviceId: String, draft: DeviceDraft): ApiResult<Device>
    suspend fun deleteDevice(deviceId: String): ApiResult<Unit>
}
