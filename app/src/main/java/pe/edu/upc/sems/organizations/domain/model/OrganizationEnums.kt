package pe.edu.upc.sems.organizations.domain.model

// Backend enums (organizations context). Unknown values map to a fallback so a new value
// added by the backend does not break the app.

enum class MembershipRole {
    ORG_ADMIN,
    SUPERVISOR,
    OPERATOR,

    /** Unknown value: treated as the most restricted role. */
    UNKNOWN,
    ;

    /** Create sites, edit the company and manage members. */
    val canManageOrganization: Boolean get() = this == ORG_ADMIN

    /** Edit or archive the site and create, edit or archive its zones (within the membership's site). */
    val canEditSites: Boolean get() = this == ORG_ADMIN || this == SUPERVISOR

    companion object {
        fun from(value: String?): MembershipRole = entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

/** Peruvian commercial tariff (OSINERGMIN). Whether it charges for demand comes from the backend. */
enum class TariffCategory {
    BT5B,
    BT3,
    BT4,
    MT2,
    MT3,
    UNKNOWN,
    ;

    companion object {
        /** Options offered in forms, in the web app's order. */
        val selectable = listOf(BT5B, BT3, BT4, MT2, MT3)

        fun from(value: String?): TariffCategory = entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

enum class ZoneType {
    SALES_FLOOR,
    COLD_STORAGE,
    WAREHOUSE,
    KITCHEN,
    OFFICES,
    HVAC,
    PARKING,
    OTHER,
    ;

    companion object {
        fun from(value: String?): ZoneType = entries.firstOrNull { it.name == value } ?: OTHER
    }
}

enum class BusinessType {
    SUPERMARKET,
    CONVENIENCE_STORE,
    DEPARTMENT_STORE,
    RESTAURANT,
    WAREHOUSE,
    OTHER,
    ;

    companion object {
        fun from(value: String?): BusinessType = entries.firstOrNull { it.name == value } ?: OTHER
    }
}

enum class OrgStatus {
    ACTIVE,
    SUSPENDED,
    ARCHIVED,
    UNKNOWN,
    ;

    companion object {
        fun from(value: String?): OrgStatus = entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}
