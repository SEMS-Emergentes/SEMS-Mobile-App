package pe.edu.upc.sems.devices.data.mapper

import pe.edu.upc.sems.devices.data.remote.dto.CreateDeviceRequestDto
import pe.edu.upc.sems.devices.data.remote.dto.DeviceDto
import pe.edu.upc.sems.devices.data.remote.dto.UpdateDeviceRequestDto
import pe.edu.upc.sems.devices.domain.model.ConnectionProtocol
import pe.edu.upc.sems.devices.domain.model.Device
import pe.edu.upc.sems.devices.domain.model.DeviceDraft
import pe.edu.upc.sems.devices.domain.model.DeviceStatus

fun DeviceDto.toDomain() = Device(
    id = deviceId,
    externalCode = externalDeviceCode,
    siteId = siteId,
    zoneId = zoneId?.takeIf { it.isNotBlank() },
    name = deviceName,
    type = deviceType,
    brand = brand?.takeIf { it.isNotBlank() },
    model = model?.takeIf { it.isNotBlank() },
    protocol = ConnectionProtocol.from(connectionProtocol),
    status = DeviceStatus.from(status),
)

fun DeviceDraft.toCreateRequest(userId: String) = CreateDeviceRequestDto(
    externalDeviceCode = externalCode.trim(),
    userId = userId,
    siteId = siteId,
    zoneId = zoneId?.takeIf { it.isNotBlank() },
    deviceName = name.trim(),
    deviceType = type.trim(),
    brand = brand.trim().ifEmpty { null },
    model = model.trim().ifEmpty { null },
    connectionProtocol = protocol.name,
)

fun DeviceDraft.toUpdateRequest() = UpdateDeviceRequestDto(
    deviceName = name.trim(),
    deviceType = type.trim(),
    brand = brand.trim().ifEmpty { null },
    model = model.trim().ifEmpty { null },
    connectionProtocol = protocol.name,
    zoneId = zoneId?.takeIf { it.isNotBlank() },
)
