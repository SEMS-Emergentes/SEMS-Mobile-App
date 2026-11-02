package pe.edu.upc.sems.organizations

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pe.edu.upc.sems.organizations.data.remote.OrganizationService
import pe.edu.upc.sems.organizations.data.repository.OrganizationRepositoryImpl
import pe.edu.upc.sems.organizations.domain.repository.OrganizationRepository
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class OrganizationsModule {

    @Binds
    @Singleton
    abstract fun bindOrganizationRepository(impl: OrganizationRepositoryImpl): OrganizationRepository

    companion object {
        @Provides
        @Singleton
        fun provideOrganizationService(retrofit: Retrofit): OrganizationService =
            retrofit.create(OrganizationService::class.java)
    }
}
