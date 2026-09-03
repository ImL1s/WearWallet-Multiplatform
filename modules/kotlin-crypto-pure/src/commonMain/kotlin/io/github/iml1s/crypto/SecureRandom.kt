package io.github.iml1s.crypto

/**
 * 跨平台 CSPRNG 密碼學安全隨機數產生器
 */
expect fun secureRandomBytes(size: Int): ByteArray
