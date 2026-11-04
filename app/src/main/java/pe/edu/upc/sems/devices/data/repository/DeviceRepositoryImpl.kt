package pe.edu.upc.sems.devices.data.repository

import javax.inject.Inject
import pe.edu.upc.sems.devices.data.mapper.toCreateRequest
import pe.edu.upc.sems.devices.data.mapper.toDomain
import pe.edu.upc.sems.devices.data.mapper.toUpdateRequest
import pe.edu.upc.sems.devices.data.remote.DeviceService
import pe.edu.upc.sems.devices.domain.model.Device
import pe.edu.upc.sems.devices.domain.model.DeviceDraft
import pe.edu.upc.sems.devices.domain.repository.DeviceRepository
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.network.map
import pe.edu.upc.sems.shared.network.safeApiCall

class DeviceRepositoryImpl @Inject constructor(
    private val service: DeviceService,
) : DeviceRepository {

    override suspend fun listDevices(userId: String): ApiResult<List<Device>> =
        safeApiCall { service.listDevices(userId) }.map { list -> list.map { it.toDomain() } }

    override suspend fun getDevice(deviceId: String): ApiResult<Device> =
        safeApiCall { service.getDevice(deviceId) }.map { it.toDomain() }

    override suspend fun createDevice(userId: String, draft: DeviceDraft): ApiResult<Device> =
        safeApiCall { service.createDevice(draft.toCreateRequest(userId)) }.map { it.toDomain() }

    override suspend fun updateDevice(deviceId: String, draft: DeviceDraft): ApiResult<Device> =
        safeApiCall { service.updateDevice(deviceId, draft.toUpdateRequest()) }.map { it.toDomain() }

    override suspend fun deleteDevice(deviceId: String): ApiResult<Unit> =
        safeApiCall { service.deleteDevice(deviceId) }
}
