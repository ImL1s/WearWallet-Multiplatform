package io.github.iml1s.crypto

// import org.kotlincrypto.hash.sha1.SHA1
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.hash.sha2.SHA512

import org.kotlincrypto.core.Resettable

public interface Digest {
    fun getAlgorithmName(): String
    fun getDigestSize(): Int
    fun update(input: Byte)
    fun update(input: ByteArray, inputOffset: Int, len: Int)
    fun doFinal(out: ByteArray, outOffset: Int): Int
    fun reset()
}

public object Digests {
    // fun sha1(): Digest = Sha1Digest()
    fun sha256(): Digest = Sha256Digest()
    fun sha512(): Digest = Sha512Digest()
    // fun ripemd160(): Digest = Ripemd160()
}

/*
private class Sha1Digest : Digest {
    private val delegate = SHA1()
    override fun getAlgorithmName(): String = "SHA-1"
    override fun getDigestSize(): Int = delegate.digestLength()
    override fun update(input: Byte) = delegate.update(byteArrayOf(input))
    override fun update(input: ByteArray, inputOffset: Int, len: Int) {
        if (inputOffset == 0 && len == input.size) delegate.update(input)
        else delegate.update(input.copyOfRange(inputOffset, inputOffset + len))
    }
    override fun doFinal(out: ByteArray, outOffset: Int): Int {
        val res = delegate.digest()
        res.copyInto(out, outOffset)
        return res.size
    }
    override fun reset() {
        if (delegate is Resettable) (delegate as Resettable).reset()
    }
}
*/

private class Sha256Digest : Digest {
    private val delegate = SHA256()
    override fun getAlgorithmName(): String = "SHA-256"
    override fun getDigestSize(): Int = delegate.digestLength()
    override fun update(input: Byte) = delegate.update(byteArrayOf(input))
    override fun update(input: ByteArray, inputOffset: Int, len: Int) {
        if (inputOffset == 0 && len == input.size) delegate.update(input)
        else delegate.update(input.copyOfRange(inputOffset, inputOffset + len))
    }
    override fun doFinal(out: ByteArray, outOffset: Int): Int {
        val res = delegate.digest()
        res.copyInto(out, outOffset)
        return res.size
    }
    override fun reset() {
        if (delegate is Resettable) (delegate as Resettable).reset()
    }
}

private class Sha512Digest : Digest {
    private val delegate = SHA512()
    override fun getAlgorithmName(): String = "SHA-512"
    override fun getDigestSize(): Int = delegate.digestLength()
    override fun update(input: Byte) = delegate.update(byteArrayOf(input))
    override fun update(input: ByteArray, inputOffset: Int, len: Int) {
        if (inputOffset == 0 && len == input.size) delegate.update(input)
        else delegate.update(input.copyOfRange(inputOffset, inputOffset + len))
    }
    override fun doFinal(out: ByteArray, outOffset: Int): Int {
        val res = delegate.digest()
        res.copyInto(out, outOffset)
        return res.size
    }
    override fun reset() {
        if (delegate is Resettable) (delegate as Resettable).reset()
    }
}
