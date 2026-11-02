package pe.edu.upc.sems.organizations.data.mapper

import pe.edu.upc.sems.organizations.data.remote.dto.CreateOrganizationRequestDto
import pe.edu.upc.sems.organizations.data.remote.dto.CreateSiteRequestDto
import pe.edu.upc.sems.organizations.data.remote.dto.GrantMembershipRequestDto
import pe.edu.upc.sems.organizations.data.remote.dto.MembershipDto
import pe.edu.upc.sems.organizations.data.remote.dto.MyOrganizationDto
import pe.edu.upc.sems.organizations.data.remote.dto.OrganizationDto
import pe.edu.upc.sems.organizations.data.remote.dto.SiteDto
import pe.edu.upc.sems.organizations.data.remote.dto.UpdateOrganizationRequestDto
import pe.edu.upc.sems.organizations.data.remote.dto.UpdateSiteRequestDto
import pe.edu.upc.sems.organizations.data.remote.dto.ZoneDto
import pe.edu.upc.sems.organizations.data.remote.dto.ZoneRequestDto
import pe.edu.upc.sems.organizations.domain.model.BusinessType
import pe.edu.upc.sems.organizations.domain.model.Member
import pe.edu.upc.sems.organizations.domain.model.MemberGrant
import pe.edu.upc.sems.organizations.domain.model.MembershipRole
import pe.edu.upc.sems.organizations.domain.model.NewOrganization
import pe.edu.upc.sems.organizations.domain.model.OrgStatus
import pe.edu.upc.sems.organizations.domain.model.Organization
import pe.edu.upc.sems.organizations.domain.model.OrganizationAccess
import pe.edu.upc.sems.organizations.domain.model.OrganizationDetails
import pe.edu.upc.sems.organizations.domain.model.Site
import pe.edu.upc.sems.organizations.domain.model.SiteDraft
import pe.edu.upc.sems.organizations.domain.model.TariffCategory
import pe.edu.upc.sems.organizations.domain.model.Zone
import pe.edu.upc.sems.organizations.domain.model.ZoneDraft
import pe.edu.upc.sems.organizations.domain.model.ZoneType

// region DTO → domain

fun OrganizationDto.toDomain() = Organization(
    id = organizationId,
    legalName = legalName,
    tradeName = tradeName?.takeIf { it.isNotBlank() },
    taxId = taxId,
    businessType = BusinessType.from(businessType),
    status = OrgStatus.from(status),
)

fun MyOrganizationDto.toDomain() = OrganizationAccess(
    organization = organization.toDomain(),
    role = MembershipRole.from(role),
    siteId = siteId,
)

fun SiteDto.toDomain() = Site(
    id = siteId,
    organizationId = organizationId,
    siteCode = siteCode,
    name = name,
    address = address?.takeIf { it.isNotBlank() },
    district = district?.takeIf { it.isNotBlank() },
    floorAreaM2 = floorAreaM2,
    contractedPowerKw = contractedPowerKw,
    tariffCategory = TariffCategory.from(tariffCategory),
    chargesForDemand = chargesForDemand,
    excludesSundaysFromPeak = excludesSundaysFromPeak,
    status = OrgStatus.from(status),
)

fun ZoneDto.toDomain() = Zone(
    id = zoneId,
    siteId = siteId,
    name = name,
    zoneType = ZoneType.from(zoneType),
    operatesOffHours = operatesOffHours,
    status = OrgStatus.from(status),
)

fun MembershipDto.toDomain() = Member(
    membershipId = membershipId,
    userId = userId,
    email = email,
    role = MembershipRole.from(role),
    siteId = siteId,
    status = OrgStatus.from(status),
)

// endregion

// region Domain → request

/** Blank optional texts are sent as null (omitted), like the web app. */
private fun String?.blankToNull() = this?.trim()?.takeIf { it.isNotEmpty() }

fun NewOrganization.toRequest(ownerUserId: String) = CreateOrganizationRequestDto(
    legalName = legalName.trim(),
    tradeName = tradeName.blankToNull(),
    taxId = taxId.trim(),
    businessType = businessType.name,
    ownerUserId = ownerUserId,
)

fun OrganizationDetails.toRequest() = UpdateOrganizationRequestDto(
    legalName = legalName.trim(),
    tradeName = tradeName.blankToNull(),
    businessType = businessType.name,
)

fun SiteDraft.toCreateRequest() = CreateSiteRequestDto(
    // The backend stores it in upper case; sending it that way keeps the duplicate check predictable.
    siteCode = siteCode.trim().uppercase(),
    name = name.trim(),
    address = address.blankToNull(),
    district = district.blankToNull(),
    floorAreaM2 = floorAreaM2,
    contractedPowerKw = contractedPowerKw,
    tariffCategory = tariffCategory.name,
    excludesSundaysFromPeak = excludesSundaysFromPeak,
)

fun SiteDraft.toUpdateRequest() = UpdateSiteRequestDto(
    name = name.trim(),
    address = address.blankToNull(),
    district = district.blankToNull(),
    floorAreaM2 = floorAreaM2,
    contractedPowerKw = contractedPowerKw,
    tariffCategory = tariffCategory.name,
    excludesSundaysFromPeak = excludesSundaysFromPeak,
)

fun ZoneDraft.toRequest() = ZoneRequestDto(
    name = name.trim(),
    zoneType = zoneType.name,
    operatesOffHours = operatesOffHours,
)

fun MemberGrant.toRequest() = GrantMembershipRequestDto(
    email = email.trim(),
    role = role.name,
    siteId = siteId,
)

// endregion
