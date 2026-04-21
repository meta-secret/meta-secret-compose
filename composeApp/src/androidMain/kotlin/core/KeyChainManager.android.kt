package core

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import java.io.IOException
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class KeyChainManagerAndroid(
    private val context: Context,
    private val logger: DebugLoggerInterface
    ) : KeyChainInterface {
    
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALIAS_PREFIX = "MetaSecret_"
        private const val GCM_TAG_LENGTH = 128
        private const val STORAGE_DIRECTORY = "MetaSecret"
        private const val STORAGE_RELATIVE_PATH = "Download/MetaSecret/"
        private const val STORAGE_RELATIVE_PATH_NO_SLASH = "Download/MetaSecret"
        private const val STORAGE_MIME_TYPE = "text/plain"
        private const val IV_SUFFIX = "_iv"
        private const val MASTER_KEY_STORAGE_KEY = "master_key"
        private const val PERMISSION_REQUEST_CODE = 123
    }

    init {
        checkPermissions()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return
        }

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
            if (isPersistentPlaintextKey(key)) {
                val saved = saveToExternalStorage(key, value)
                if (!saved) {
                    logger.log(LogTag.KeyChainManager.Message.ErrorSaving, "key=$key", success = false)
                }
                return@withContext saved
            }

            val secretKey = getOrCreateKey(getKeyAlias(key))
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            
            val encryptedBytes = cipher.doFinal(value.toByteArray(Charset.defaultCharset()))
            val encryptedValue = Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
            val ivValue = Base64.encodeToString(iv, Base64.DEFAULT)

            val dataSaved = saveToExternalStorage(key, encryptedValue)
            val ivSaved = saveToExternalStorage(key + IV_SUFFIX, ivValue)
            val allSaved = dataSaved && ivSaved

            if (!allSaved) {
                logger.log(
                    LogTag.KeyChainManager.Message.ErrorSaving,
                    "key=$key dataSaved=$dataSaved ivSaved=$ivSaved",
                    success = false
                )
            }

            allSaved
        } catch (e: Exception) {
            logger.log(LogTag.KeyChainManager.Message.ErrorSaving, "${e.message}", success = false)
            e.printStackTrace()
            false
        }
    }
    
    override suspend fun getString(key: String): String? = withContext(Dispatchers.IO) {
        try {
            if (isPersistentPlaintextKey(key)) {
                val persistentValue = readFromExternalStorage(key)
                if (persistentValue != null) {
                    return@withContext persistentValue
                }
            }

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

            if (isPersistentPlaintextKey(key)) {
                // Clean legacy encrypted IV artifact if it exists from old versions.
                deleteFromExternalStorage(key + IV_SUFFIX)
                return@withContext true
            }

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
        if (isPersistentPlaintextKey(key)) {
            fileExists(key)
        } else {
            fileExists(key) && fileExists(key + IV_SUFFIX)
        }
    }
    
    override suspend fun clearAll(isCleanDB: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            logger.log(LogTag.KeyChainManager.Message.StartingClearAll, "isCleanDB: $isCleanDB", success = true)
            val masterKey = getString("master_key")
            if (masterKey != null) {
                removeKey(masterKey)
            }

            if (isCleanDB) {
                deleteAllPersistentEntries()

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
            }

            logger.log(LogTag.KeyChainManager.Message.ClearAllCompleted, success = true)
            true
        } catch (e: Exception) {
            logger.log(LogTag.KeyChainManager.Message.ErrorClearing, "${e.message}", success = false)
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
    
    private fun fileExists(key: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findMediaStoreUri(key) != null
        } else {
            getLegacyPublicFile(key).exists()
        }
    }

    private fun saveToExternalStorage(key: String, data: String): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStoreDownloads(key, data)
            } else {
                val file = getLegacyPublicFile(key)
                val parentDir = file.parentFile
                if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                    throw IOException("Unable to create storage directory: ${parentDir.absolutePath}")
                }
                FileOutputStream(file).use { outputStream ->
                    outputStream.write(data.toByteArray(Charset.defaultCharset()))
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun readFromExternalStorage(key: String): String? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return readFromMediaStoreDownloads(key)
            } else {
                val file = getLegacyPublicFile(key)
                if (!file.exists()) {
                    return null
                }

                FileInputStream(file).use { inputStream ->
                    val bytes = ByteArray(file.length().toInt())
                    inputStream.read(bytes)
                    return String(bytes, Charset.defaultCharset())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun deleteFromExternalStorage(key: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                deleteFromMediaStoreDownloads(key)
            } else {
                val file = getLegacyPublicFile(key)
                if (file.exists()) file.delete() else true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun deleteAllPersistentEntries() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            deleteAllFromMediaStoreDownloads()
        } else {
            val storageDir = getLegacyPublicDirectory()
            if (storageDir.exists()) {
                storageDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("ms_")) {
                        file.delete()
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun findMediaStoreUri(key: String): Uri? {
        val fileName = getStorageFileName(key)
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME}=? AND (${MediaStore.Downloads.RELATIVE_PATH}=? OR ${MediaStore.Downloads.RELATIVE_PATH}=?)",
            arrayOf(fileName, STORAGE_RELATIVE_PATH, STORAGE_RELATIVE_PATH_NO_SLASH),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                return Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
            }
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStoreDownloads(key: String, data: String) {
        val fileName = getStorageFileName(key)
        val uri = findMediaStoreUri(key) ?: run {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, STORAGE_MIME_TYPE)
                put(MediaStore.Downloads.RELATIVE_PATH, STORAGE_RELATIVE_PATH)
            }
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Unable to create MediaStore entry for key: $key")
        }

        context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
            outputStream.write(data.toByteArray(Charset.defaultCharset()))
        } ?: throw IOException("Unable to open output stream for key: $key")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun readFromMediaStoreDownloads(key: String): String? {
        val uri = findMediaStoreUri(key) ?: return null
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            return inputStream.bufferedReader(Charset.defaultCharset()).readText()
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteFromMediaStoreDownloads(key: String): Boolean {
        val uri = findMediaStoreUri(key) ?: return true
        return context.contentResolver.delete(uri, null, null) > 0
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteAllFromMediaStoreDownloads() {
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "(${MediaStore.Downloads.RELATIVE_PATH}=? OR ${MediaStore.Downloads.RELATIVE_PATH}=?) AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
            arrayOf(STORAGE_RELATIVE_PATH, STORAGE_RELATIVE_PATH_NO_SLASH, "ms_%"),
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                context.contentResolver.delete(uri, null, null)
            }
        }
    }

    private fun getStorageFileName(key: String): String {
        val encodedKey = Base64.encodeToString(
            key.toByteArray(Charset.defaultCharset()),
            Base64.URL_SAFE or Base64.NO_WRAP
        )
        return "ms_$encodedKey"
    }

    private fun isPersistentPlaintextKey(key: String): Boolean {
        return key == MASTER_KEY_STORAGE_KEY
    }

    private fun getLegacyPublicDirectory(): File {
        return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), STORAGE_DIRECTORY)
    }

    private fun getLegacyPublicFile(key: String): File {
        return File(getLegacyPublicDirectory(), getStorageFileName(key))
    }
} 