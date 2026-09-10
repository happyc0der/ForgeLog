package dev.happyc0der.forgelog.debug

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.happyc0der.forgelog.domain.debug.DebugTools
import javax.inject.Singleton

/** Release builds have no development tools, and no code to perform them. */
@Module
@InstallIn(SingletonComponent::class)
object ReleaseDebugToolsModule {

    @Provides
    @Singleton
    fun provideDebugTools(): DebugTools = object : DebugTools {
        override val isAvailable: Boolean = false
        override suspend fun seedSampleData() = Unit
    }
}
