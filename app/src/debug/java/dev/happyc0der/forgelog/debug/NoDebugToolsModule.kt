package dev.happyc0der.forgelog.debug

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.happyc0der.forgelog.domain.debug.DebugTools
import javax.inject.Singleton

/**
 * The debug build is the one kept on the phone with real training in it, so it has no development
 * tools either. "Load sample data" used to sit in its Settings, one tap and no confirmation away
 * from writing twelve weeks of made-up sessions into a real history. It lives in the QA build now,
 * which has its own database.
 */
@Module
@InstallIn(SingletonComponent::class)
object NoDebugToolsModule {

    @Provides
    @Singleton
    fun provideDebugTools(): DebugTools = object : DebugTools {
        override val isAvailable: Boolean = false
        override suspend fun seedSampleData() = Unit
    }
}
