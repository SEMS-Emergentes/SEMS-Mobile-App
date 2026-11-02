package pe.edu.upc.sems.organizations.presentation.common

import androidx.annotation.StringRes
import pe.edu.upc.sems.R
import pe.edu.upc.sems.organizations.domain.model.BusinessType
import pe.edu.upc.sems.organizations.domain.model.MembershipRole
import pe.edu.upc.sems.organizations.domain.model.TariffCategory
import pe.edu.upc.sems.organizations.domain.model.ZoneType

// Spanish labels, same texts as the web app (SitesView.vue, SiteModal.vue, MembersCard.vue).

@StringRes
fun BusinessType.label(): Int = when (this) {
    BusinessType.SUPERMARKET -> R.string.business_type_supermarket
    BusinessType.CONVENIENCE_STORE -> R.string.business_type_convenience_store
    BusinessType.DEPARTMENT_STORE -> R.string.business_type_department_store
    BusinessType.RESTAURANT -> R.string.business_type_restaurant
    BusinessType.WAREHOUSE -> R.string.business_type_warehouse
    BusinessType.OTHER -> R.string.business_type_other
}

/** Long label for forms: "BT3 — baja tensión con cargo por potencia". */
@StringRes
fun TariffCategory.description(): Int = when (this) {
    TariffCategory.BT5B -> R.string.tariff_bt5b
    TariffCategory.BT3 -> R.string.tariff_bt3
    TariffCategory.BT4 -> R.string.tariff_bt4
    TariffCategory.MT2 -> R.string.tariff_mt2
    TariffCategory.MT3 -> R.string.tariff_mt3
    TariffCategory.UNKNOWN -> R.string.tariff_unknown
}

@StringRes
fun ZoneType.label(): Int = when (this) {
    ZoneType.SALES_FLOOR -> R.string.zone_type_sales_floor
    ZoneType.COLD_STORAGE -> R.string.zone_type_cold_storage
    ZoneType.WAREHOUSE -> R.string.zone_type_warehouse
    ZoneType.KITCHEN -> R.string.zone_type_kitchen
    ZoneType.OFFICES -> R.string.zone_type_offices
    ZoneType.HVAC -> R.string.zone_type_hvac
    ZoneType.PARKING -> R.string.zone_type_parking
    ZoneType.OTHER -> R.string.zone_type_other
}

@StringRes
fun MembershipRole.label(): Int = when (this) {
    MembershipRole.ORG_ADMIN -> R.string.membership_role_org_admin
    MembershipRole.SUPERVISOR -> R.string.membership_role_supervisor
    MembershipRole.OPERATOR -> R.string.membership_role_operator
    MembershipRole.UNKNOWN -> R.string.membership_role_unknown
}
