package pe.edu.upc.sems.devices.data.remote

import pe.edu.upc.sems.devices.data.remote.dto.CreateDeviceRequestDto
import pe.edu.upc.sems.devices.data.remote.dto.DeviceDto
import pe.edu.upc.sems.devices.data.remote.dto.UpdateDeviceRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Devices bounded context (backend DeviceController, base /api/v1/device-management). */
interface DeviceService {

    @GET("device-management/users/{userId}/devices")
    suspend fun listDevices(@Path("userId") userId: String): List<DeviceDto>

    @GET("device-management/devices/{deviceId}")
    suspend fun getDevice(@Path("deviceId") deviceId: String): DeviceDto

    @POST("device-management/devices")
    suspend fun createDevice(@Body body: CreateDeviceRequestDto): DeviceDto

    @PUT("device-management/devices/{deviceId}")
    suspend fun updateDevice(@Path("deviceId") deviceId: String, @Body body: UpdateDeviceRequestDto): DeviceDto

    @DELETE("device-management/devices/{deviceId}")
    suspend fun deleteDevice(@Path("deviceId") deviceId: String)
}
