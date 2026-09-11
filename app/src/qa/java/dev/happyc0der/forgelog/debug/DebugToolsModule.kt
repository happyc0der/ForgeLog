package dev.happyc0der.forgelog.debug

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.happyc0der.forgelog.domain.debug.DebugTools
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DebugToolsModule {

    @Provides
    @Singleton
    fun provideDebugTools(seedData: SeedData): DebugTools = object : DebugTools {
        override val isAvailable: Boolean = true
        override suspend fun seedSampleData() = seedData.seed()
    }
}
