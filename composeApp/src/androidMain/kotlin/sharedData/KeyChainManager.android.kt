package sharedData

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import android.content.SharedPreferences

class KeyChainManagerAndroid(private val context: Context) : KeyChainInterface {
    
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALIAS_PREFIX = "MetaSecret_"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val ENCRYPTED_DATA_PREFS = "encrypted_data_prefs"
        private const val IV_SUFFIX = "_iv"
    }

    override suspend fun saveString(key: String, value: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val secretKey = getOrCreateKey(getKeyAlias(key))
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            
            val encryptedBytes = cipher.doFinal(value.toByteArray(Charset.defaultCharset()))
            val encryptedValue = Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
            val ivValue = Base64.encodeToString(iv, Base64.DEFAULT)

            sharedPreferences.edit()
                .putString(key, encryptedValue)
                .putString(key + IV_SUFFIX, ivValue)
                .apply()
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    override suspend fun getString(key: String): String? = withContext(Dispatchers.IO) {
        try {
            val encryptedValue = sharedPreferences.getString(key, null) ?: return@withContext null
            val ivValue = sharedPreferences.getString(key + IV_SUFFIX, null) ?: return@withContext null
            
            val encryptedBytes = Base64.decode(encryptedValue, Base64.DEFAULT)
            val iv = Base64.decode(ivValue, Base64.DEFAULT)
            
            val secretKey = getOrCreateKey(getKeyAlias(key))
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charset.defaultCharset())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    override suspend fun removeKey(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            sharedPreferences.edit()
                .remove(key)
                .remove(key + IV_SUFFIX)
                .apply()

            try {
                val keyAlias = getKeyAlias(key)
                if (keyStore.containsAlias(keyAlias)) {
                    keyStore.deleteEntry(keyAlias)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    override suspend fun containsKey(key: String): Boolean = withContext(Dispatchers.IO) {
        sharedPreferences.contains(key) && sharedPreferences.contains(key + IV_SUFFIX)
    }
    
    override suspend fun clearAll(): Boolean = withContext(Dispatchers.IO) {
        try {
            sharedPreferences.edit().clear().apply()

            val aliases = keyStore.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                if (alias.startsWith(KEY_ALIAS_PREFIX)) {
                    keyStore.deleteEntry(alias)
                }
            }
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    private fun getKeyAlias(key: String): String {
        return KEY_ALIAS_PREFIX + key
    }
    
    private fun getOrCreateKey(keyAlias: String): SecretKey {
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
            
            keyGenerator.init(keyGenParameterSpec)
            return keyGenerator.generateKey()
        }

        return keyStore.getKey(keyAlias, null) as SecretKey
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }
    }

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(ENCRYPTED_DATA_PREFS, Context.MODE_PRIVATE)
    }

} 