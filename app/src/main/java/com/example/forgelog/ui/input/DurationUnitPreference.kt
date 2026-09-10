package com.example.forgelog.ui.input

import android.content.Context
import com.example.forgelog.domain.workout.DurationInputUnit

object DurationUnitPreference {
    private const val PREFS = "forgelog_ui"
    private const val KEY_DURATION_UNIT = "duration_input_unit"

    fun read(context: Context): DurationInputUnit {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DURATION_UNIT, DurationInputUnit.SECONDS.name)
        return DurationInputUnit.entries.firstOrNull { it.name == raw }
            ?: DurationInputUnit.SECONDS
    }

    fun write(context: Context, unit: DurationInputUnit) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DURATION_UNIT, unit.name)
            .apply()
    }
}
