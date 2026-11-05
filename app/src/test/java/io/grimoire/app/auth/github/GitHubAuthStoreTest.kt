package io.grimoire.app.auth.github

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class GitHubAuthStoreTest {
    @Test fun keystoreFailureUsesMemoryForSaveAndClear() {
        val store = GitHubAuthStore { throw AssertionError("keystore unavailable") }
        assertNull(store.currentToken())
        store.save(GitHubAccount("secret", "reader", 42L))
        assertEquals("secret", store.currentToken())
        assertEquals("reader", store.account.value?.login)
        store.clear()
        assertNull(store.account.value)
    }

    @Test fun unreadableEncryptedValuesAlsoUseMemory() {
        val unreadable = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, _, _ -> throw IllegalStateException("encrypted value corrupt") } as SharedPreferences
        val store = GitHubAuthStore { unreadable }
        assertNull(store.account.value)
        store.save(GitHubAccount("new-secret", "reader", 42L))
        assertEquals("new-secret", store.currentToken())
        store.clear()
        assertNull(store.currentToken())
    }
    @Test fun writeFailureRetainsNewAccountOnlyInMemory() {
        var edits = 0
        val failing = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getString" -> "old-account"
                "getLong" -> 1L
                "edit" -> { edits++; throw IllegalStateException("keystore lost") }
                else -> error("Unexpected call: ${method.name}")
            }
        } as SharedPreferences
        val store = GitHubAuthStore { failing }
        assertEquals("old-account", store.currentToken())
        store.save(GitHubAccount("new-secret", "reader", 42L))
        assertEquals("new-secret", store.currentToken())
        store.clear()
        assertNull(store.currentToken())
        assertEquals(1, edits)
    }

}
