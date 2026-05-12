package io.grimoire.app.domain.auth

import io.grimoire.app.data.local.dao.CategoryDao
import io.grimoire.app.data.preferences.HiddenCategoriesPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HiddenCategoriesAuthManager @Inject constructor(
    private val preferences: HiddenCategoriesPreferences,
    private val categoryDao: CategoryDao,
) {

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    val hasPin: Flow<Boolean> = preferences.pinHash.changes().map { it.isNotEmpty() }
    val biometricEnabled: Flow<Boolean> = preferences.biometricEnabled.changes()

    suspend fun setPin(pin: String) {
        val saltBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val salt = saltBytes.toBase64()
        val hash = hash(salt, pin)
        preferences.pinSalt.set(salt)
        preferences.pinHash.set(hash)
    }

    suspend fun verifyPin(pin: String): Boolean {
        val salt = preferences.pinSalt.changes().first()
        val expected = preferences.pinHash.changes().first()
        if (salt.isEmpty() || expected.isEmpty()) return false
        return constantTimeEquals(hash(salt, pin), expected)
    }

    suspend fun clearPin() {
        preferences.pinHash.set("")
        preferences.pinSalt.set("")
        preferences.biometricEnabled.set(false)
        val hidden = categoryDao.getAll().first().filter { it.isHidden }
        hidden.forEach { categoryDao.upsert(it.copy(isHidden = false)) }
        _isUnlocked.value = false
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        preferences.biometricEnabled.set(enabled)
    }

    fun unlock() {
        _isUnlocked.value = true
    }

    fun lock() {
        _isUnlocked.value = false
    }

    private fun hash(salt: String, pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt.toByteArray(Charsets.UTF_8))
        md.update(pin.toByteArray(Charsets.UTF_8))
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }

    private fun ByteArray.toBase64(): String =
        android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
}
