package pe.edu.upc.sems.organizations.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Organizations uses snake_case (backend OrganizationResources, @JsonNaming(SnakeCase)).
// Requests: no default values on fields the backend needs — kotlinx does not encode defaults.
// Null optional fields are omitted (explicitNulls = false).

// region Responses

@Serializable
data class OrganizationDto(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("legal_name") val legalName: String,
    @SerialName("trade_name") val tradeName: String? = null,
    @SerialName("tax_id") val taxId: String = "",
    @SerialName("business_type") val businessType: String? = null,
    @SerialName("status") val status: String? = null,
)

/** Item of GET /users/{userId}/organizations: the organization plus the caller's role there. */
@Serializable
data class MyOrganizationDto(
    @SerialName("organization") val organization: OrganizationDto,
    @SerialName("role") val role: String? = null,
    @SerialName("site_id") val siteId: String? = null,
)

@Serializable
data class SiteDto(
    @SerialName("site_id") val siteId: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("site_code") val siteCode: String = "",
    @SerialName("name") val name: String,
    @SerialName("address") val address: String? = null,
    @SerialName("district") val district: String? = null,
    @SerialName("floor_area_m2") val floorAreaM2: Double? = null,
    @SerialName("contracted_power_kw") val contractedPowerKw: Double = 0.0,
    @SerialName("tariff_category") val tariffCategory: String? = null,
    @SerialName("charges_for_demand") val chargesForDemand: Boolean = false,
    @SerialName("excludes_sundays_from_peak") val excludesSundaysFromPeak: Boolean = false,
    @SerialName("status") val status: String? = null,
)

@Serializable
data class ZoneDto(
    @SerialName("zone_id") val zoneId: String,
    @SerialName("site_id") val siteId: String,
    @SerialName("name") val name: String,
    @SerialName("zone_type") val zoneType: String? = null,
    @SerialName("operates_off_hours") val operatesOffHours: Boolean = false,
    @SerialName("status") val status: String? = null,
)

@Serializable
data class MembershipDto(
    @SerialName("membership_id") val membershipId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("email") val email: String? = null,
    @SerialName("role") val role: String? = null,
    @SerialName("site_id") val siteId: String? = null,
    @SerialName("status") val status: String? = null,
)

// endregion

// region Requests

@Serializable
data class CreateOrganizationRequestDto(
    @SerialName("legal_name") val legalName: String,
    @SerialName("trade_name") val tradeName: String?,
    @SerialName("tax_id") val taxId: String,
    @SerialName("business_type") val businessType: String,
    @SerialName("owner_user_id") val ownerUserId: String,
)

@Serializable
data class UpdateOrganizationRequestDto(
    @SerialName("legal_name") val legalName: String,
    @SerialName("trade_name") val tradeName: String?,
    @SerialName("business_type") val businessType: String,
)

@Serializable
data class CreateSiteRequestDto(
    @SerialName("site_code") val siteCode: String,
    @SerialName("name") val name: String,
    @SerialName("address") val address: String?,
    @SerialName("district") val district: String?,
    @SerialName("floor_area_m2") val floorAreaM2: Double?,
    @SerialName("contracted_power_kw") val contractedPowerKw: Double,
    @SerialName("tariff_category") val tariffCategory: String,
    @SerialName("excludes_sundays_from_peak") val excludesSundaysFromPeak: Boolean,
)

/** Same as creation without site_code: the backend does not allow changing it. */
@Serializable
data class UpdateSiteRequestDto(
    @SerialName("name") val name: String,
    @SerialName("address") val address: String?,
    @SerialName("district") val district: String?,
    @SerialName("floor_area_m2") val floorAreaM2: Double?,
    @SerialName("contracted_power_kw") val contractedPowerKw: Double,
    @SerialName("tariff_category") val tariffCategory: String,
    @SerialName("excludes_sundays_from_peak") val excludesSundaysFromPeak: Boolean,
)

@Serializable
data class ZoneRequestDto(
    @SerialName("name") val name: String,
    @SerialName("zone_type") val zoneType: String,
    @SerialName("operates_off_hours") val operatesOffHours: Boolean,
)

@Serializable
data class GrantMembershipRequestDto(
    @SerialName("email") val email: String,
    @SerialName("role") val role: String,
    @SerialName("site_id") val siteId: String?,
)

// endregion
