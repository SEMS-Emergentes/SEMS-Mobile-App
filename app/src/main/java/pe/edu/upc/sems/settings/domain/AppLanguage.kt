package pe.edu.upc.sems.settings.domain

import java.util.Locale

/** The languages of the app. English is the default (README, CON04); Spanish is the alternative. */
enum class AppLanguage(val tag: String) {
    ENGLISH("en"),
    SPANISH("es"),
    ;

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        val DEFAULT = ENGLISH

        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: DEFAULT
    }
}

/** What language the user chose. It lives on the phone only: the backend has no such setting. */
interface LanguageStore {
    fun get(): AppLanguage

    fun set(language: AppLanguage)
}
