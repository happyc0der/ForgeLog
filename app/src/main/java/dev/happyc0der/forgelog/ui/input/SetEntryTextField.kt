package dev.happyc0der.forgelog.ui.input

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.EntryPointAccessors
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.di.SettingsEntryPoint
import dev.happyc0der.forgelog.domain.workout.DurationInput
import dev.happyc0der.forgelog.domain.workout.DurationInputUnit
import dev.happyc0der.forgelog.ui.format.Formatters
import kotlinx.coroutines.launch

/**
 * The duration input unit, shared with Settings through the one settings store.
 *
 * It used to keep its own SharedPreferences key, which meant this toggle and the Settings screen
 * could disagree about the same preference.
 */
@Composable
fun rememberDurationInputUnit(): Pair<DurationInputUnit, (DurationInputUnit) -> Unit> {
    val context = LocalContext.current
    val repository = remember(context) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, SettingsEntryPoint::class.java)
            .settingsRepository()
    }
    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val unit = settings?.durationInputUnit ?: DurationInputUnit.SECONDS
    return unit to { next -> scope.launch { repository.setDurationInputUnit(next) } }
}

/**
 * The unit rest is typed in, kept separate from an exercise's own duration.
 *
 * A plank is 45 seconds and the rest after it is two minutes; one toggle for both meant choosing
 * minutes rendered the hold as "0.75".
 */
@Composable
fun rememberRestInputUnit(): Pair<DurationInputUnit, (DurationInputUnit) -> Unit> {
    val context = LocalContext.current
    val repository = remember(context) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, SettingsEntryPoint::class.java)
            .settingsRepository()
    }
    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val unit = settings?.restInputUnit ?: DurationInputUnit.SECONDS
    return unit to { next -> scope.launch { repository.setRestInputUnit(next) } }
}

fun Modifier.bringIntoViewWhenFocused(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, imeBottom) {
        if (focused) {
            requester.bringIntoView()
        }
    }
    bringIntoViewRequester(requester)
        .onFocusChanged { focused = it.hasFocus }
}

@Composable
fun DurationUnitToggle(
    unit: DurationInputUnit,
    onUnitChange: (DurationInputUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DurationChip(
            selected = unit == DurationInputUnit.SECONDS,
            label = stringResource(R.string.duration_unit_seconds),
            onClick = { onUnitChange(DurationInputUnit.SECONDS) },
        )
        DurationChip(
            selected = unit == DurationInputUnit.MINUTES,
            label = stringResource(R.string.duration_unit_minutes),
            onClick = { onUnitChange(DurationInputUnit.MINUTES) },
        )
    }
}

@Composable
private fun DurationChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
fun SetEntryTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number,
    singleLine: Boolean = true,
    minLines: Int = 1,
    durationUnit: DurationInputUnit? = null,
) {
    var focused by remember { mutableStateOf(false) }
    var textFieldValue by remember {
        val initial = fieldText(value, durationUnit, editing = false)
        mutableStateOf(TextFieldValue(initial, selection = TextRange(initial.length)))
    }

    LaunchedEffect(durationUnit) {
        val next = fieldText(value, durationUnit, editing = focused)
        textFieldValue = TextFieldValue(next, selection = TextRange(next.length))
    }

    LaunchedEffect(value, focused) {
        if (focused) return@LaunchedEffect
        val next = fieldText(value, durationUnit, editing = false)
        if (next != textFieldValue.text) {
            textFieldValue = TextFieldValue(next, selection = TextRange(next.length))
        }
    }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { raw ->
            // Numeric fields refuse a keystroke that would leave unparseable text, rather than
            // showing it and quietly storing null. Text fields (notes) are left alone.
            val numeric = keyboardType == KeyboardType.Number || keyboardType == KeyboardType.Decimal
            val incoming = if (numeric) {
                val kept = NumericInput.accept(raw.text, decimal = keyboardType == KeyboardType.Decimal)
                    ?: return@OutlinedTextField
                if (kept == raw.text) raw else raw.copy(text = kept)
            } else {
                raw
            }
            textFieldValue = incoming
            if (durationUnit != null) {
                if (incoming.text.isBlank()) {
                    onValueChange("")
                } else if (DurationInput.isParseable(incoming.text, durationUnit)) {
                    val seconds = DurationInput.parseSeconds(incoming.text, durationUnit)
                    onValueChange(seconds?.toString().orEmpty())
                }
            } else {
                onValueChange(incoming.text)
            }
        },
        modifier = modifier
            .bringIntoViewWhenFocused()
            .onFocusChanged { state ->
                val gained = state.isFocused && !focused
                focused = state.isFocused
                if (gained && durationUnit != null) {
                    // Swaps "1m 16s" for the editable "1.27" -- only on the way in, so nothing
                    // rewrites the text under the user's fingers while they type.
                    val editable = fieldText(value, durationUnit, editing = true)
                    textFieldValue = TextFieldValue(editable, selection = TextRange(editable.length))
                } else if (gained) {
                    textFieldValue = textFieldValue.copy(
                        selection = TextRange(textFieldValue.text.length),
                    )
                }
            },
        label = { Text(text = label) },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

/**
 * Clears focus when the keyboard is dismissed.
 *
 * Back hides the keyboard but leaves the field focused, and a focused duration field keeps its
 * editing form -- so an 89-second rest went on reading "1.48" after the user had finished with it,
 * until something else happened to take focus.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClearFocusWhenKeyboardHides() {
    val focusManager = LocalFocusManager.current
    val keyboardVisible = WindowInsets.isImeVisible
    var wasVisible by remember { mutableStateOf(keyboardVisible) }
    LaunchedEffect(keyboardVisible) {
        if (wasVisible && !keyboardVisible) focusManager.clearFocus()
        wasVisible = keyboardVisible
    }
}

/**
 * What a set field shows for [value], a whole number of seconds when [unit] is non-null.
 *
 * In minutes mode a field at rest reads "1m 16s", and only turns into decimal minutes while it is
 * being edited. Decimal minutes are quick to type but misleading to read: 76 seconds showed as
 * "1.27", which looks like one minute twenty-seven and means one minute sixteen.
 */
internal fun fieldText(value: String, unit: DurationInputUnit?, editing: Boolean): String = when {
    unit == null -> value
    unit == DurationInputUnit.MINUTES && !editing ->
        value.toIntOrNull()?.let(Formatters::seconds).orEmpty()
    else -> DurationInput.toDisplay(value.toIntOrNull(), unit)
}

@Composable
fun DurationSecondsField(
    secondsText: String,
    onSecondsTextChange: (String) -> Unit,
    label: String,
    unit: DurationInputUnit,
    onUnitChange: (DurationInputUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        SetEntryTextField(
            value = secondsText,
            onValueChange = onSecondsTextChange,
            label = label,
            modifier = Modifier.weight(1f),
            keyboardType = if (unit == DurationInputUnit.MINUTES) {
                KeyboardType.Decimal
            } else {
                KeyboardType.Number
            },
            durationUnit = unit,
        )
        DurationUnitToggle(
            unit = unit,
            onUnitChange = onUnitChange,
        )
    }
}
