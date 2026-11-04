package pe.edu.upc.sems.devices

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pe.edu.upc.sems.devices.data.remote.DeviceService
import pe.edu.upc.sems.devices.data.repository.DeviceRepositoryImpl
import pe.edu.upc.sems.devices.domain.repository.DeviceRepository
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class DevicesModule {

    @Binds
    @Singleton
    abstract fun bindDeviceRepository(impl: DeviceRepositoryImpl): DeviceRepository

    companion object {
        @Provides
        @Singleton
        fun provideDeviceService(retrofit: Retrofit): DeviceService = retrofit.create(DeviceService::class.java)
    }
}
