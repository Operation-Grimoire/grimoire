package io.grimoire.app.auth.athenaeum

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
 * Persists the paired Athenaeum device token + its scopes, at-rest encrypted
 * via the Android keystore (same pattern as [io.grimoire.app.auth.github.GitHubAuthStore]).
 * Self-heals by wiping the file if the keystore-backed store becomes unreadable.
 */
@Singleton
class AthenaeumAuthStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences = openPrefs(context)
    private val _account = MutableStateFlow(load())
    val account: StateFlow<AthenaeumAccount?> = _account.asStateFlow()

    fun save(account: AthenaeumAccount) {
        prefs.edit()
            .putString(KEY_TOKEN, account.token)
            .putString(KEY_SCOPES, account.scopes.joinToString(" "))
            .putLong(KEY_CONNECTED_AT, account.connectedAtMillis)
            .putString(KEY_EMAIL, account.email)
            .putString(KEY_USERNAME, account.username)
            .apply()
        _account.value = account
    }

    fun clear() {
        prefs.edit().clear().apply()
        _account.value = null
    }

    /** Fast synchronous read for the OkHttp interceptor. */
    fun currentToken(): String? = _account.value?.token

    private fun load(): AthenaeumAccount? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val scopes = prefs.getString(KEY_SCOPES, "").orEmpty().split(' ').filter { it.isNotBlank() }.toSet()
        return AthenaeumAccount(
            token = token,
            scopes = scopes,
            connectedAtMillis = prefs.getLong(KEY_CONNECTED_AT, 0L),
            email = prefs.getString(KEY_EMAIL, null),
            username = prefs.getString(KEY_USERNAME, null),
        )
    }

    private companion object {
        const val FILE = "athenaeum_auth"
        const val KEY_TOKEN = "device_token"
        const val KEY_SCOPES = "scopes"
        const val KEY_CONNECTED_AT = "connected_at"
        const val KEY_EMAIL = "email"
        const val KEY_USERNAME = "username"

        fun openPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return try {
                build(context, masterKey)
            } catch (_: Exception) {
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
