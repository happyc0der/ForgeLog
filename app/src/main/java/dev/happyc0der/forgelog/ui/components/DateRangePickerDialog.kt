package dev.happyc0der.forgelog.ui.components

import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.happyc0der.forgelog.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Picks an arbitrary span of days, for when the presets do not cover what the user wants.
 *
 * The Compose picker works in UTC-midnight millis, which is not the same instant as midnight where
 * the user lives. Both ends are therefore converted through [zone]: the start becomes the first
 * instant of that local day and the end the first instant of the *following* local day, so the
 * result is the half-open range the rest of the app uses and the last day picked is included
 * rather than silently dropped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    zone: ZoneId,
    onConfirm: (fromEpochMs: Long, untilEpochMs: Long) -> Unit,
    onDismiss: () -> Unit,
    initialFromEpochMs: Long? = null,
    initialUntilEpochMs: Long? = null,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialFromEpochMs?.let { utcMillisFor(it, zone) },
        // The stored end is exclusive; show the last day actually included.
        initialSelectedEndDateMillis = initialUntilEpochMs?.let { utcMillisFor(it - 1, zone) },
    )
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = start != null && end != null,
                onClick = {
                    val from = start ?: return@TextButton
                    val until = end ?: return@TextButton
                    onConfirm(
                        localDayStart(from, zone, plusDays = 0),
                        // Exclusive: the start of the day after the one picked. Computed as a
                        // calendar day rather than "+24h", which is wrong across a DST change.
                        localDayStart(until, zone, plusDays = 1),
                    )
                },
            ) {
                Text(text = stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    ) {
        DateRangePicker(state = state)
    }
}

/** The picker's UTC-midnight value for the local day containing [epochMs]. */
private fun utcMillisFor(epochMs: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

/** The first instant of the local day the picker's UTC-midnight value names, offset by [plusDays]. */
private fun localDayStart(utcMidnightMs: Long, zone: ZoneId, plusDays: Long): Long =
    Instant.ofEpochMilli(utcMidnightMs)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .plusDays(plusDays)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
