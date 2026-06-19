package io.grimoire.app.ui.screen.crash

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.grimoire.app.data.crash.CrashLogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class CrashReportViewModel @Inject constructor(
    private val crashLogStore: CrashLogStore,
) : ViewModel() {

    // The crash file is a few KB; reading it once at construction is cheap and
    // avoids an empty-then-populated flicker on the first frame.
    private val _report = MutableStateFlow(crashLogStore.readPending().orEmpty())
    val report: StateFlow<String> = _report.asStateFlow()

    /** Deletes the persisted crash so it stops appearing on future launches. */
    fun dismiss() {
        crashLogStore.clear()
    }
}
