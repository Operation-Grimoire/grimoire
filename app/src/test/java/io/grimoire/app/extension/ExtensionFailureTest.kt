package io.grimoire.app.extension

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExtensionFailureTest {
    @Test fun thirdPartyErrorDoesNotPreventNextCapability() = runBlocking {
        val rejected = AssertionError("invalid saved preference")
        val result = catchExtensionFailure { throw rejected }
        var hostApplied = false
        catchExtensionFailure { hostApplied = true }
        assertSame(rejected, result.exceptionOrNull())
        assertTrue(hostApplied)
    }

    @Test fun cancelledPreferenceReadPropagatesCancellation() = runBlocking {
        val cancelled = CancellationException("scan cancelled")
        try {
            catchExtensionFailure { throw cancelled }
            fail("Cancellation must stop the scan")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
    }
}
