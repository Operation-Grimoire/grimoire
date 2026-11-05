package io.grimoire.app.auth.github

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the connected GitHub account (token + login). Backed by
 * [EncryptedSharedPreferences] so the token is at-rest encrypted with a key
 * from the Android keystore.
 *
 * If encrypted storage fails, the account lives only in memory for this session.
 * No token is ever written to unencrypted preferences.
 */
@Singleton
class GitHubAuthStore internal constructor(openPrefs: () -> SharedPreferences) {
    @Inject constructor(@ApplicationContext context: Context) : this({ openPrefs(context) })

    private var prefs: SharedPreferences? = runCatching(openPrefs).getOrNull()
    private val _account = MutableStateFlow(runCatching { load() }.getOrElse {
        prefs = null
        null
    })
    val account: StateFlow<GitHubAccount?> = _account.asStateFlow()

    fun save(account: GitHubAccount) {
        persist {
            putString(KEY_TOKEN, account.accessToken)
            putString(KEY_LOGIN, account.login)
            putLong(KEY_CONNECTED_AT, account.connectedAtMillis)
        }
        _account.value = account
    }

    fun clear() {
        persist { clear() }
        _account.value = null
    }

    private fun persist(update: SharedPreferences.Editor.() -> Unit) {
        runCatching { prefs?.edit()?.apply(update)?.apply() }
            .onFailure { prefs = null }
    }

    /** Fast synchronous read for the OkHttp interceptor. */
    fun currentToken(): String? = _account.value?.accessToken

    private fun load(): GitHubAccount? {
        val storage = prefs ?: return null
        val token = storage.getString(KEY_TOKEN, null) ?: return null
        val login = storage.getString(KEY_LOGIN, null) ?: return null
        val connectedAt = storage.getLong(KEY_CONNECTED_AT, 0L)
        return GitHubAccount(token, login, connectedAt)
    }

    private companion object {
        const val FILE = "github_auth"
        const val KEY_TOKEN = "access_token"
        const val KEY_LOGIN = "login"
        const val KEY_CONNECTED_AT = "connected_at"

        fun openPrefs(context: Context): SharedPreferences {
            return runCatching { build(context) }.getOrElse {
                // A restored encrypted file may refer to a key that no longer exists.
                context.deleteSharedPreferences(FILE)
                build(context)
            }
        }

        fun build(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
