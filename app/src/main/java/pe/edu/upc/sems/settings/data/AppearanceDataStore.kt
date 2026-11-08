package pe.edu.upc.sems.settings.data

import javax.inject.Qualifier

/** Distinguishes the appearance DataStore from the session one. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppearanceDataStore
