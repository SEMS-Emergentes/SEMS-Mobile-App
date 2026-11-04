package pe.edu.upc.sems.energy

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pe.edu.upc.sems.energy.data.remote.EnergyService
import pe.edu.upc.sems.energy.data.repository.EnergyRepositoryImpl
import pe.edu.upc.sems.energy.domain.repository.EnergyRepository
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class EnergyModule {

    @Binds
    @Singleton
    abstract fun bindEnergyRepository(impl: EnergyRepositoryImpl): EnergyRepository

    companion object {
        @Provides
        @Singleton
        fun provideEnergyService(retrofit: Retrofit): EnergyService = retrofit.create(EnergyService::class.java)
    }
}
