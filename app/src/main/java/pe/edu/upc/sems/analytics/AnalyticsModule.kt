package pe.edu.upc.sems.analytics

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pe.edu.upc.sems.analytics.data.remote.AnalyticsService
import pe.edu.upc.sems.analytics.data.repository.AnalyticsRepositoryImpl
import pe.edu.upc.sems.analytics.domain.repository.AnalyticsRepository
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {

    @Binds
    @Singleton
    abstract fun bindAnalyticsRepository(impl: AnalyticsRepositoryImpl): AnalyticsRepository

    companion object {
        @Provides
        @Singleton
        fun provideAnalyticsService(retrofit: Retrofit): AnalyticsService =
            retrofit.create(AnalyticsService::class.java)
    }
}
