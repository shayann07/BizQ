// app/src/main/java/com/example/finalproject/di/RepositoryModule.kt
package com.example.finalproject.di

import com.example.finalproject.colorsApi.repository.ColorRepository
import com.example.finalproject.colorsApi.repository.ColorRepositoryImpl
import com.example.finalproject.data.repository.*
import com.example.finalproject.data.repository.FirebaseImpl.AuthRepositoryFirebase
import com.example.finalproject.data.repository.FirebaseImpl.AvailabilityRepositoryFirebase
import com.example.finalproject.data.repository.FirebaseImpl.OnboardingRepositoryFirebase
import com.example.finalproject.data.repository.FirebaseImpl.OnboardingStateRepositoryFirebase
import com.example.finalproject.data.repository.onboarding.OnboardingRepository
import com.example.finalproject.data.repository.onboarding.OnboardingStateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBusinessRepository(impl: BusinessRepositoryImpl): BusinessRepository

    @Binds
    @Singleton
    abstract fun bindServiceRepository(impl: ServiceRepositoryImpl): ServiceRepository
    // OR if you only use Room directly for services:
    // abstract fun bindServiceRepository(impl: ServicesRepositoryFirebase): ServiceRepository

    @Binds @Singleton
    abstract fun bindAvailabilityRepo(impl: AvailabilityRepositoryImpl): AvailabilityRepository


    @Binds
    @Singleton
    abstract fun bindOnboardingDraftRepository(impl: OnboardingRepositoryFirebase): OnboardingRepository

    @Binds
    @Singleton
    abstract fun bindOnboardingStateRepository(impl: OnboardingStateRepositoryFirebase): OnboardingStateRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryFirebase): AuthRepository
    // OR use your AuthRepositoryImpl – just keep it consistent with the interface above.


    @Binds
    @Singleton
    abstract fun bindColorRepository(impl: ColorRepositoryImpl): ColorRepository
}
