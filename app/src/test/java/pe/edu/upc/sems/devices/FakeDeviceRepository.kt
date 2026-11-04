package pe.edu.upc.sems.devices

import pe.edu.upc.sems.devices.domain.model.ConnectionProtocol
import pe.edu.upc.sems.devices.domain.model.Device
import pe.edu.upc.sems.devices.domain.model.DeviceDraft
import pe.edu.upc.sems.devices.domain.model.DeviceStatus
import pe.edu.upc.sems.devices.domain.repository.DeviceRepository
import pe.edu.upc.sems.shared.network.ApiResult

/** Configurable fake: every call is recorded and answers with the matching `...Result`. */
class FakeDeviceRepository : DeviceRepository {

    val listCalls = mutableListOf<String>()
    var listResult: ApiResult<List<Device>> = ApiResult.Success(emptyList())

    val getCalls = mutableListOf<String>()
    var getResult: ApiResult<Device> = ApiResult.Success(DEVICE)

    val createCalls = mutableListOf<Pair<String, DeviceDraft>>()
    var createResult: ApiResult<Device> = ApiResult.Success(DEVICE)

    val updateCalls = mutableListOf<Pair<String, DeviceDraft>>()
    var updateResult: ApiResult<Device> = ApiResult.Success(DEVICE)

    val deleteCalls = mutableListOf<String>()
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun listDevices(userId: String): ApiResult<List<Device>> {
        listCalls += userId
        return listResult
    }

    override suspend fun getDevice(deviceId: String): ApiResult<Device> {
        getCalls += deviceId
        return getResult
    }

    override suspend fun createDevice(userId: String, draft: DeviceDraft): ApiResult<Device> {
        createCalls += userId to draft
        return createResult
    }

    override suspend fun updateDevice(deviceId: String, draft: DeviceDraft): ApiResult<Device> {
        updateCalls += deviceId to draft
        return updateResult
    }

    override suspend fun deleteDevice(deviceId: String): ApiResult<Unit> {
        deleteCalls += deviceId
        return deleteResult
    }

    companion object {
        val DEVICE = Device(
            id = "dev-1",
            externalCode = "CAM-001",
            siteId = "site-1",
            zoneId = null,
            name = "Cámaras de frío",
            type = "REFRIGERATOR",
            brand = "Carrier",
            model = null,
            protocol = ConnectionProtocol.WIFI,
            status = DeviceStatus.ACTIVE,
        )
    }
}
