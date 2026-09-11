package dev.happyc0der.forgelog.di

import android.content.Context
import androidx.datastore.dataStoreFile
import androidx.test.core.app.ApplicationProvider
import dev.happyc0der.forgelog.data.settings.toAppSettings
import dev.happyc0der.forgelog.domain.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A settings file that no longer parses gives the defaults, rather than an error on every screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsCorruptionTest {

    @Test
    fun `a corrupt settings file falls back to the defaults`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.dataStoreFile("forgelog_settings.preferences_pb")
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(0x7F, 0x00, 0x13, 0x37, 0x42))

        val dataStore = SettingsModule.providePreferencesDataStore(context, Dispatchers.IO)

        assertEquals(AppSettings(), dataStore.data.first().toAppSettings())
    }
}
