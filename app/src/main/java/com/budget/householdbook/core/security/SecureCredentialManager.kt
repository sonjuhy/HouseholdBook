package com.budget.householdbook.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.budget.householdbook.domain.model.DEFAULT_QUICK_INQUIRY_INSTITUTION_ID
import com.budget.householdbook.domain.model.QuickInquiryCredentials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [Beta] KB국민은행 무로그인 빠른조회 자격증명(계좌번호, 생년월일/사업자번호, 빠른조회 비밀번호)을
 * Android Keystore AES/GCM으로 암호화하여 저장하는 매니저
 */
class SecureCredentialManager(context: Context) {

    private val prefs = context.getSharedPreferences("kb_quick_secure_store", Context.MODE_PRIVATE)
    private val keyAlias = "HouseholdBook_KB_Quick_AES_Key"

    private val _credentialsFlow = MutableStateFlow(loadCredentials())
    val credentialsFlow: StateFlow<QuickInquiryCredentials> = _credentialsFlow.asStateFlow()

    fun saveCredentials(credentials: QuickInquiryCredentials) {
        prefs.edit()
            .putString("inst", credentials.institutionId)
            .putString("acc", encrypt(credentials.accountNumber.trim()))
            .putString("birth", encrypt(credentials.birthOrBizNumber.trim()))
            .putString("pw4", encrypt(credentials.quickPassword4Digit.trim()))
            .apply()
        _credentialsFlow.value = credentials
    }

    fun clearCredentials() {
        prefs.edit().clear().apply()
        _credentialsFlow.value = QuickInquiryCredentials()
    }

    private fun loadCredentials(): QuickInquiryCredentials {
        val acc = prefs.getString("acc", null)?.let { decrypt(it) }.orEmpty()
        val birth = prefs.getString("birth", null)?.let { decrypt(it) }.orEmpty()
        val pw4 = prefs.getString("pw4", null)?.let { decrypt(it) }.orEmpty()
        return QuickInquiryCredentials(
            institutionId = prefs.getString("inst", null) ?: DEFAULT_QUICK_INQUIRY_INSTITUTION_ID,
            accountNumber = acc,
            birthOrBizNumber = birth,
            quickPassword4Digit = pw4
        )
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existingKey = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
        if (existingKey != null) return existingKey.secretKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + encrypted.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        }.getOrDefault("")
    }

    private fun decrypt(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return runCatching {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val ivSize = combined[0].toInt()
            val iv = combined.copyOfRange(1, 1 + ivSize)
            val encrypted = combined.copyOfRange(1 + ivSize, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrDefault("")
    }
}
