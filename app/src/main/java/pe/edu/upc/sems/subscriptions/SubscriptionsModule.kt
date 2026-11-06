package pe.edu.upc.sems.subscriptions

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pe.edu.upc.sems.subscriptions.data.remote.SubscriptionService
import pe.edu.upc.sems.subscriptions.data.repository.SubscriptionRepositoryImpl
import pe.edu.upc.sems.subscriptions.domain.repository.SubscriptionRepository
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class SubscriptionsModule {

    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(impl: SubscriptionRepositoryImpl): SubscriptionRepository

    @Binds
    abstract fun bindPendingPlanStore(impl: pe.edu.upc.sems.subscriptions.data.repository.SharedPrefsPendingPlanStore):
        pe.edu.upc.sems.subscriptions.domain.repository.PendingPlanStore

    companion object {
        @Provides
        @Singleton
        fun provideSubscriptionService(retrofit: Retrofit): SubscriptionService =
            retrofit.create(SubscriptionService::class.java)
    }
}
