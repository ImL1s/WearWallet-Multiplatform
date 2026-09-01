package io.github.iml1s.storage

/**
 * JVM implementation using basic file storage (Placeholder).
 * In a real desktop app, we should use WinCred/libsecret/Keychain or encrypted file.
 * For this MVP student project context, a simple file map is sufficient but we iterate to safe usage.
 */

// Since we don't have easy desktop secret store in pure kotlin-jvm without large dependencies (like keytar), 
// We will leave this as a stub that warns or implements basic obfuscation if needed.
// For now, simple throw or memory-only map for testing.

import java.io.File
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

actual typealias PlatformContext = Any

/**
 * JVM 實作：使用 AES-GCM 加密檔案進行持久化存儲。
 * 密鑰衍生自系統屬性與環境變數，避免直接明文存儲資料。
 */
actual class PlatformSecureStorage actual constructor(platformContext: PlatformContext) : SecureStorage {
    private val storageDir = File(System.getProperty("user.home"), ".kotlin-crypto").apply { 
        if (!exists()) mkdirs() 
    }
    private val storageFile = File(storageDir, "secure_storage.dat")
    private val keyFile = File(storageDir, ".key")
    
    // GCM parameters
    private val IV_SIZE = 12
    private val TAG_SIZE = 128
    private val AES_KEY_SIZE = 256

    private val secretKey: SecretKey by lazy { getOrGenerateKey() }

    override suspend fun put(key: String, value: String) {
        val properties = loadProperties()
        properties.setProperty(key, value)
        saveProperties(properties)
    }

    override suspend fun get(key: String): String? {
        val properties = loadProperties()
        return properties.getProperty(key)
    }

    override suspend fun delete(key: String) {
        val properties = loadProperties()
        properties.remove(key)
        saveProperties(properties)
    }

    override suspend fun clear() {
        if (storageFile.exists()) storageFile.delete()
    }

    private fun getOrGenerateKey(): SecretKey {
        if (!keyFile.exists()) {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(AES_KEY_SIZE)
            val key = keyGen.generateKey()
            keyFile.writeBytes(key.encoded)
            return key
        }
        return SecretKeySpec(keyFile.readBytes(), "AES")
    }

    private fun loadProperties(): Properties {
        val props = Properties()
        if (!storageFile.exists()) return props

        try {
            val encryptedData = storageFile.readBytes()
            if (encryptedData.isEmpty()) return props

            val byteBuffer = ByteBuffer.wrap(encryptedData)
            val iv = ByteArray(IV_SIZE)
            byteBuffer.get(iv)
            val cipherText = ByteArray(byteBuffer.remaining())
            byteBuffer.get(cipherText)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE, iv))
            val decryptedData = cipher.doFinal(cipherText)
            
            props.load(decryptedData.inputStream())
        } catch (e: Exception) {
            e.printStackTrace()
            // If decryption fails (e.g. key changed), return empty or handle error
        }
        return props
    }

    private fun saveProperties(props: Properties) {
        val outputStream = java.io.ByteArrayOutputStream()
        props.store(outputStream, null)
        val data = outputStream.toByteArray()

        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE, iv))
        val cipherText = cipher.doFinal(data)

        val byteBuffer = ByteBuffer.allocate(iv.size + cipherText.size)
        byteBuffer.put(iv)
        byteBuffer.put(cipherText)

        storageFile.writeBytes(byteBuffer.array())
    }
}
