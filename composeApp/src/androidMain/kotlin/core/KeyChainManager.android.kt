package core

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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import android.os.Environment
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class KeyChainManagerAndroid(
    private val context: Context
    ) : KeyChainInterface {
    
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALIAS_PREFIX = "MetaSecret_"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val STORAGE_DIRECTORY = "MetaSecret"
        private const val IV_SUFFIX = "_iv"
        private const val PERMISSION_REQUEST_CODE = 123
    }

    init {
        checkPermissions()
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        val notGrantedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGrantedPermissions.isNotEmpty() && context is Activity) {
            ActivityCompat.requestPermissions(
                context,
                notGrantedPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
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

            saveToExternalStorage(key, encryptedValue)
            saveToExternalStorage(key + IV_SUFFIX, ivValue)
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    override suspend fun getString(key: String): String? = withContext(Dispatchers.IO) {
        try {
            val encryptedValue = readFromExternalStorage(key) ?: return@withContext null
            val ivValue = readFromExternalStorage(key + IV_SUFFIX) ?: return@withContext null
            
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
            deleteFromExternalStorage(key)
            deleteFromExternalStorage(key + IV_SUFFIX)

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
        fileExists(key) && fileExists(key + IV_SUFFIX)
    }
    
    override suspend fun clearAll(isCleanDB: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            println("🗝️KeyChainManager: Android: Starting clearAll process (isCleanDB: $isCleanDB)")
            val masterKey = getString("master_key")
            if (masterKey != null) {
                removeKey(masterKey)
            }

            if (isCleanDB) {
                val storageDir = getStorageDirectory()
                if (storageDir.exists()) {
                    storageDir.listFiles()?.forEach { it.delete() }
                }

                val aliases = keyStore.aliases()
                var deletedCount = 0
                while (aliases.hasMoreElements()) {
                    val alias = aliases.nextElement()
                    if (alias.startsWith(KEY_ALIAS_PREFIX)) {
                        keyStore.deleteEntry(alias)
                        deletedCount++
                    }
                }
                println("🗝️KeyChainManager: Android: Deleted $deletedCount keystore entries")
            }

            println("🗝️KeyChainManager: Android: ✅ clearAll completed successfully")
            true
        } catch (e: Exception) {
            println("🗝️KeyChainManager: Android: ❌ Error clearing KeyChain: ${e.message}")
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
    
    private fun getStorageDirectory(): File {
        val externalDir = Environment.getExternalStorageDirectory()
        val storageDir = File(externalDir, STORAGE_DIRECTORY)
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        return storageDir
    }

    private fun getFile(key: String): File {
        return File(getStorageDirectory(), key)
    }

    private fun fileExists(key: String): Boolean {
        return getFile(key).exists()
    }

    private fun saveToExternalStorage(key: String, data: String): Boolean {
        try {
            val file = getFile(key)
            FileOutputStream(file).use { outputStream ->
                outputStream.write(data.toByteArray(Charset.defaultCharset()))
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun readFromExternalStorage(key: String): String? {
        try {
            val file = getFile(key)
            if (!file.exists()) {
                return null
            }
            
            FileInputStream(file).use { inputStream ->
                val bytes = ByteArray(file.length().toInt())
                inputStream.read(bytes)
                return String(bytes, Charset.defaultCharset())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun deleteFromExternalStorage(key: String): Boolean {
        val file = getFile(key)
        return if (file.exists()) file.delete() else true
    }
} 