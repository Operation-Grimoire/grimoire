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
 * If the keystore-backed file ever becomes unreadable (rare, but happens on
 * backup/restore or factory-reset edge cases), we wipe it and start over —
 * the worst case for the user is having to reconnect.
 */
@Singleton
class GitHubAuthStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences = openPrefs(context)
    private val _account = MutableStateFlow(load())
    val account: StateFlow<GitHubAccount?> = _account.asStateFlow()

    fun save(account: GitHubAccount) {
        prefs.edit()
            .putString(KEY_TOKEN, account.accessToken)
            .putString(KEY_LOGIN, account.login)
            .putLong(KEY_CONNECTED_AT, account.connectedAtMillis)
            .apply()
        _account.value = account
    }

    fun clear() {
        prefs.edit().clear().apply()
        _account.value = null
    }

    /** Fast synchronous read for the OkHttp interceptor. */
    fun currentToken(): String? = _account.value?.accessToken

    private fun load(): GitHubAccount? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val login = prefs.getString(KEY_LOGIN, null) ?: return null
        val connectedAt = prefs.getLong(KEY_CONNECTED_AT, 0L)
        return GitHubAccount(token, login, connectedAt)
    }

    private companion object {
        const val FILE = "github_auth"
        const val KEY_TOKEN = "access_token"
        const val KEY_LOGIN = "login"
        const val KEY_CONNECTED_AT = "connected_at"

        fun openPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return try {
                build(context, masterKey)
            } catch (_: Exception) {
                // Keystore corruption or backup/restore edge case — drop the
                // file and retry. The user will need to reconnect, which is
                // the cheapest possible recovery.
                context.deleteSharedPreferences(FILE)
                build(context, masterKey)
            }
        }

        fun build(context: Context, masterKey: MasterKey): SharedPreferences =
            EncryptedSharedPreferences.create(
                context,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
    }
}
