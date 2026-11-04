package pe.edu.upc.sems.energy.domain.repository

import pe.edu.upc.sems.energy.domain.model.DeviceConsumption
import pe.edu.upc.sems.shared.network.ApiResult

interface EnergyRepository {
    suspend fun listDeviceConsumptions(userId: String): ApiResult<List<DeviceConsumption>>
}
