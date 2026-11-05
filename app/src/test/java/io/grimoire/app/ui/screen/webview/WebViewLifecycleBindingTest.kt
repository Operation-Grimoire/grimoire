package io.grimoire.app.ui.screen.webview

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.junit.Assert.*
import org.junit.Test

class WebViewLifecycleBindingTest {
    private class Owner(initial: Lifecycle.State) : LifecycleOwner {
        val events = TestLifecycle(this, initial)
        override val lifecycle: Lifecycle get() = events
    }

    private class TestLifecycle(private val owner: LifecycleOwner, initial: State) : Lifecycle() {
        private var state = initial
        val observers = mutableSetOf<LifecycleObserver>()
        override val currentState: State get() = state
        override fun addObserver(observer: LifecycleObserver) {
            observers += observer
            if (state == State.RESUMED) (observer as DefaultLifecycleObserver).onResume(owner)
        }
        override fun removeObserver(observer: LifecycleObserver) { observers -= observer }
        fun pauseScreen() {
            state = State.STARTED
            observers.toList().forEach { (it as DefaultLifecycleObserver).onPause(owner) }
        }
        fun resumeScreen() {
            state = State.RESUMED
            observers.toList().forEach { (it as DefaultLifecycleObserver).onResume(owner) }
        }
    }

    private fun binding(owner: Owner, calls: MutableList<String>) = WebViewLifecycleBinding(owner.lifecycle,
        resume = { calls += "resume" }, pause = { calls += "pause" }, destroy = { calls += "destroy" })

    @Test fun screenPauseAndResumeReachOnlyTheBoundView() {
        val owner = Owner(Lifecycle.State.RESUMED)
        val calls = mutableListOf<String>()
        val binding = binding(owner, calls)
        owner.events.pauseScreen()
        owner.events.resumeScreen()
        assertEquals(listOf("resume", "pause", "resume"), calls)
        binding.release()
    }

    @Test fun releaseDestroysOnceAndStopsLifecycleCallbacks() {
        val owner = Owner(Lifecycle.State.RESUMED)
        val calls = mutableListOf<String>()
        val binding = binding(owner, calls)
        binding.release()
        binding.release()
        owner.events.pauseScreen()
        owner.events.resumeScreen()
        assertEquals(listOf("resume", "pause", "destroy"), calls)
        assertTrue(owner.events.observers.isEmpty())
    }

    @Test fun releaseStillDestroysAndDetachesWhenPauseFails() {
        val owner = Owner(Lifecycle.State.RESUMED)
        var destroyed = 0
        val failure = IllegalStateException("pause failed")
        val binding = WebViewLifecycleBinding(owner.lifecycle, resume = {},
            pause = { throw failure }, destroy = { destroyed++ })
        try {
            binding.release()
            fail("The pause error must remain observable")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
        binding.release()
        assertEquals(1, destroyed)
        assertTrue(owner.events.observers.isEmpty())
    }

    @Test fun initiallyInactiveViewStaysPausedUntilScreenResumes() {
        val owner = Owner(Lifecycle.State.STARTED)
        val calls = mutableListOf<String>()
        val binding = binding(owner, calls)
        assertEquals(listOf("pause"), calls)
        owner.events.resumeScreen()
        assertEquals(listOf("pause", "resume"), calls)
        binding.release()
    }
}
