package pe.edu.upc.sems

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import pe.edu.upc.sems.settings.data.SharedPrefsLanguageStore

@HiltAndroidApp
class SemsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Dates and numbers follow the language the user chose (English until they pick another).
        SharedPrefsLanguageStore.apply(SharedPrefsLanguageStore.read(this))
    }
}
