package dev.happyc0der.forgelog.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.happyc0der.forgelog.domain.settings.SettingsRepository

/**
 * Lets a composable read settings without a ViewModel of its own.
 *
 * Used by the duration-unit toggle, which appears inline next to the fields it affects on three
 * different screens. Threading it through three ViewModels would be more plumbing than the toggle is
 * worth, and having it keep its own SharedPreferences — as it used to — meant Settings and the
 * loggers could disagree about the same preference.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsEntryPoint {
    fun settingsRepository(): SettingsRepository
}
