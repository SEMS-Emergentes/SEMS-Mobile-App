package pe.edu.upc.sems.alerts

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pe.edu.upc.sems.alerts.data.remote.AlertService
import pe.edu.upc.sems.alerts.data.repository.AlertRepositoryImpl
import pe.edu.upc.sems.alerts.domain.repository.AlertRepository
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class AlertsModule {

    @Binds
    @Singleton
    abstract fun bindAlertRepository(impl: AlertRepositoryImpl): AlertRepository

    companion object {
        @Provides
        @Singleton
        fun provideAlertService(retrofit: Retrofit): AlertService = retrofit.create(AlertService::class.java)
    }
}
