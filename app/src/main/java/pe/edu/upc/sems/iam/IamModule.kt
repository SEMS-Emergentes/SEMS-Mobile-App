package pe.edu.upc.sems.iam

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pe.edu.upc.sems.iam.data.remote.AuthService
import pe.edu.upc.sems.iam.data.repository.AuthRepositoryImpl
import pe.edu.upc.sems.iam.domain.repository.AuthRepository
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class IamModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: pe.edu.upc.sems.iam.data.repository.ProfileRepositoryImpl):
        pe.edu.upc.sems.iam.domain.repository.ProfileRepository

    companion object {
        @Provides
        @Singleton
        fun provideAuthService(retrofit: Retrofit): AuthService = retrofit.create(AuthService::class.java)

        @Provides
        @Singleton
        fun provideUserService(retrofit: Retrofit): pe.edu.upc.sems.iam.data.remote.UserService =
            retrofit.create(pe.edu.upc.sems.iam.data.remote.UserService::class.java)
    }
}
