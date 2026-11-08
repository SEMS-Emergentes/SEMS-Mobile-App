package pe.edu.upc.sems.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pe.edu.upc.sems.settings.data.AppearanceDataStore
import pe.edu.upc.sems.settings.data.DataStoreAppearanceStore
import pe.edu.upc.sems.settings.domain.AppearanceStore

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    abstract fun bindAppearanceStore(impl: DataStoreAppearanceStore): AppearanceStore

    @Binds
    abstract fun bindLanguageStore(impl: pe.edu.upc.sems.settings.data.SharedPrefsLanguageStore):
        pe.edu.upc.sems.settings.domain.LanguageStore

    companion object {
        @Provides
        @Singleton
        @AppearanceDataStore
        fun provideAppearanceDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("sems_appearance") }
    }
}
