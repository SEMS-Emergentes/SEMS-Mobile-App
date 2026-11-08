package pe.edu.upc.sems.settings.data

import android.content.Context
import android.content.res.Configuration
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import pe.edu.upc.sems.settings.domain.AppLanguage
import pe.edu.upc.sems.settings.domain.LanguageStore
import pe.edu.upc.sems.shared.util.Formatters

/**
 * Keeps the chosen language in SharedPreferences. It is not DataStore on purpose: the activity needs the language
 * synchronously, before anything else starts, to build its resources in the right language.
 */
@Singleton
class SharedPrefsLanguageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : LanguageStore {

    override fun get(): AppLanguage = read(context)

    override fun set(language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.tag).apply()
        apply(language)
    }

    companion object {
        private const val PREFS = "sems_language"
        private const val KEY = "language"

        /** The saved language, or English when the user never chose one. */
        fun read(context: Context): AppLanguage =
            AppLanguage.fromTag(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

        /** Makes dates, numbers and the app's own formatting follow [language]. */
        fun apply(language: AppLanguage) {
            Locale.setDefault(language.locale)
            Formatters.useEnglishFormat = language == AppLanguage.ENGLISH
        }
    }
}

/** A copy of this context whose resources are in [language]. */
fun Context.withLanguage(language: AppLanguage): Context {
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(language.locale)
    return createConfigurationContext(configuration)
}
