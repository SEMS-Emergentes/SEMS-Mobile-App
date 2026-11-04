package pe.edu.upc.sems.energy.data.repository

import javax.inject.Inject
import pe.edu.upc.sems.energy.data.mapper.toDomain
import pe.edu.upc.sems.energy.data.remote.EnergyService
import pe.edu.upc.sems.energy.domain.model.DeviceConsumption
import pe.edu.upc.sems.energy.domain.repository.EnergyRepository
import pe.edu.upc.sems.shared.network.ApiResult
import pe.edu.upc.sems.shared.network.map
import pe.edu.upc.sems.shared.network.safeApiCall

class EnergyRepositoryImpl @Inject constructor(
    private val service: EnergyService,
) : EnergyRepository {

    override suspend fun listDeviceConsumptions(userId: String): ApiResult<List<DeviceConsumption>> =
        safeApiCall { service.listDeviceConsumptions(userId) }.map { list -> list.map { it.toDomain() } }
}
