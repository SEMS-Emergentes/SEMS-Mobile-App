package pe.edu.upc.sems.organizations.domain.model

/** Company that owns the sites (Organization → Site → Zone → Meter). */
data class Organization(
    val id: String,
    val legalName: String,
    val tradeName: String?,
    /** RUC: 11 digits. Cannot be changed after registration. */
    val taxId: String,
    val businessType: BusinessType,
    val status: OrgStatus,
)

/** The organization of the logged-in user with their membership (GET /users/{id}/organizations). */
data class OrganizationAccess(
    val organization: Organization,
    val role: MembershipRole,
    /** Site the membership is tied to; null means the whole organization. */
    val siteId: String?,
)

data class Site(
    val id: String,
    val organizationId: String,
    /** Unique per organization, stored in upper case. Cannot be changed. */
    val siteCode: String,
    val name: String,
    val address: String?,
    val district: String?,
    val floorAreaM2: Double?,
    val contractedPowerKw: Double,
    val tariffCategory: TariffCategory,
    /** Comes from the backend (depends on the tariff category): never computed in the app. */
    val chargesForDemand: Boolean,
    val excludesSundaysFromPeak: Boolean,
    val status: OrgStatus,
)

data class Zone(
    val id: String,
    val siteId: String,
    val name: String,
    val zoneType: ZoneType,
    val operatesOffHours: Boolean,
    val status: OrgStatus,
)

data class Member(
    val membershipId: String,
    val userId: String,
    /** Null when the account was deleted and the membership was left behind. */
    val email: String?,
    val role: MembershipRole,
    val siteId: String?,
    val status: OrgStatus,
)

// region Inputs

data class NewOrganization(
    val legalName: String,
    val tradeName: String?,
    val taxId: String,
    val businessType: BusinessType,
)

data class OrganizationDetails(
    val legalName: String,
    val tradeName: String?,
    val businessType: BusinessType,
)

/** Site form. [siteCode] is only sent on creation. */
data class SiteDraft(
    val siteCode: String,
    val name: String,
    val address: String?,
    val district: String?,
    val floorAreaM2: Double?,
    val contractedPowerKw: Double,
    val tariffCategory: TariffCategory,
    val excludesSundaysFromPeak: Boolean,
)

data class ZoneDraft(
    val name: String,
    val zoneType: ZoneType,
    val operatesOffHours: Boolean,
)

data class MemberGrant(
    val email: String,
    val role: MembershipRole,
    /** Required for SUPERVISOR, forbidden for ORG_ADMIN, optional for OPERATOR (checked by the backend too). */
    val siteId: String?,
)

// endregion
