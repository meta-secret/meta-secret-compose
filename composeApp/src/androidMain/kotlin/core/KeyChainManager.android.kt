package core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.metasecret.core.MetaSecretNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyChainManagerAndroid(
    private val context: Context,
    private val logger: DebugLoggerInterface
) : KeyChainInterface {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALIAS_PREFIX = "MetaSecret_"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_SUFFIX = "_iv"
        private const val KEYS_DIR = "keys"
        private const val DB_PREFIX = "meta-secret-"
        private const val DB_SUFFIX = ".db"
        private const val LEGACY_MASTER_KEY_PREFS = "metasecret_master_key_cache"
        private const val LEGACY_MASTER_KEY_FIELD = "master_key_cached_value"
    }

    private val keysDir: File by lazy {
        File(context.noBackupFilesDir, KEYS_DIR).also { if (!it.exists()) it.mkdirs() }
    }

    override suspend fun saveString(key: String, value: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val secretKey = getOrCreateKey(getKeyAlias(key))
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            getKeyFile(key).writeBytes(cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
            getIvFile(key).writeBytes(cipher.iv)
            true
        } catch (e: Exception) {
            logger.log(LogTag.KeyChainManager.Message.ErrorSaving, "key=$key ${e.message}", success = false)
            false
        }
    }

    override suspend fun getString(key: String): String? = withContext(Dispatchers.IO) {
        try {
            val keyFile = getKeyFile(key)
            val ivFile = getIvFile(key)
            if (!keyFile.exists() || !ivFile.exists()) {
                return@withContext migrateFromLegacy(key)
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(getKeyAlias(key)), GCMParameterSpec(GCM_TAG_LENGTH, ivFile.readBytes()))
            String(cipher.doFinal(keyFile.readBytes()), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun removeKey(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            getKeyFile(key).delete()
            getIvFile(key).delete()
            val alias = getKeyAlias(key)
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun containsKey(key: String): Boolean = withContext(Dispatchers.IO) {
        getKeyFile(key).exists() && getIvFile(key).exists()
    }

    override suspend fun clearAll(isCleanDB: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            logger.log(LogTag.KeyChainManager.Message.StartingClearAll, "isCleanDB: $isCleanDB", success = true)

            if (isCleanDB) {
                deleteAllDatabaseFiles()

                keysDir.listFiles()?.forEach { it.delete() }

                val aliases = keyStore.aliases()
                var deletedCount = 0
                while (aliases.hasMoreElements()) {
                    val alias = aliases.nextElement()
                    if (alias.startsWith(KEY_ALIAS_PREFIX)) {
                        keyStore.deleteEntry(alias)
                        deletedCount++
                    }
                }
                logger.log(LogTag.KeyChainManager.Message.DeletedKeystoreEntries, "$deletedCount", success = true)
                clearLegacyData()
            } else {
                removeKey("master_key")
            }

            logger.log(LogTag.KeyChainManager.Message.ClearAllCompleted, success = true)
            true
        } catch (e: Exception) {
            logger.log(LogTag.KeyChainManager.Message.ErrorClearing, "${e.message}", success = false)
            false
        }
    }

    private fun deleteAllDatabaseFiles() {
        try {
            MetaSecretNative.clean_up_database()
        } catch (e: Exception) {
            // best-effort: release native DB connection before deletion
        }
        val baseDir = context.noBackupFilesDir
        val dbFiles = baseDir.listFiles { file ->
            file.name.startsWith(DB_PREFIX) && file.name.endsWith(DB_SUFFIX)
        } ?: return
        for (file in dbFiles) {
            val deleted = file.delete()
            logger.log(LogTag.MetaSecretCoreService.Message.DbFileDeleted, "${file.name} deleted=$deleted", success = deleted)
        }
    }

    private fun getKeyAlias(key: String): String = KEY_ALIAS_PREFIX + key

    private fun getKeyFile(key: String): File = File(keysDir, encodeFileName(key))
    private fun getIvFile(key: String): File = File(keysDir, encodeFileName(key) + IV_SUFFIX)

    private fun encodeFileName(key: String): String =
        Base64.encodeToString(key.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)

    private fun getOrCreateKey(keyAlias: String): SecretKey {
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            keyGenerator.init(
                KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            return keyGenerator.generateKey()
        }
        return keyStore.getKey(keyAlias, null) as SecretKey
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    }

    private suspend fun migrateFromLegacy(key: String): String? {
        if (key != "master_key") return null
        val prefs = context.getSharedPreferences(LEGACY_MASTER_KEY_PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getString(LEGACY_MASTER_KEY_FIELD, null)
        if (!cached.isNullOrEmpty()) {
            saveString(key, cached)
            prefs.edit().clear().apply()
            logger.log(LogTag.KeyChainManager.Message.StartingClearAll, "Migrated master_key from legacy cache", success = true)
            return cached
        }
        return null
    }

    private fun clearLegacyData() {
        try {
            context.getSharedPreferences(LEGACY_MASTER_KEY_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        } catch (e: Exception) {
            // best-effort
        }
    }
}
