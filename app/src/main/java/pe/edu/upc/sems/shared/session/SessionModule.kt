package pe.edu.upc.sems.shared.session

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
import javax.inject.Qualifier
import javax.inject.Singleton

/** Distinguishes the session DataStore from other DataStores added later (e.g. app settings). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SessionDataStore

/** File name: files/datastore/sems_session.preferences_pb. Keep in sync with res/xml backup rules. */
internal const val SESSION_DATASTORE_NAME = "sems_session"

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {

    @Binds
    abstract fun bindSessionManager(impl: DataStoreSessionManager): SessionManager

    companion object {
        @Provides
        @Singleton
        @SessionDataStore
        fun provideSessionDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile(SESSION_DATASTORE_NAME) },
            )
    }
}
