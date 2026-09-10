package dev.happyc0der.forgelog.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Launches [block] in the ViewModel scope and reports a failure instead of letting it escape.
 *
 * A throw inside a bare `viewModelScope.launch` reaches the default uncaught-exception handler and
 * kills the app. Several repository operations fail by design — duplicating a program that was
 * deleted on another screen, deleting an exercise that just gained session history — so every
 * user-triggered operation goes through here.
 *
 * [CancellationException] is rethrown: a cancelled coroutine is normal control flow, not an error
 * to show the user.
 */
fun ViewModel.launchSafely(
    onError: (Throwable) -> Unit,
    block: suspend CoroutineScope.() -> Unit,
): Job = viewModelScope.launch {
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        onError(throwable)
    }
}

/**
 * Keeps a UI-state flow alive when its source fails, reporting the failure and falling back to
 * [fallback].
 *
 * Without this a Room error completes the flow exceptionally and the collecting `stateIn` crashes,
 * so the screen's error state could never be reached. Note that `catch` ends the upstream flow, so
 * a screen offering a retry button has to re-subscribe — see the retry token in `ProgramsViewModel`.
 */
fun <T> Flow<T>.reportErrors(fallback: T, onError: (Throwable) -> Unit): Flow<T> =
    catch { throwable ->
        onError(throwable)
        emit(fallback)
    }
