// Test Kotlin address generation logic
import kotlin.math.abs

fun generatePublicKey(privateKeyHex: String): String {
    // Simulate secp256k1 public key generation
    val hash1 = abs((privateKeyHex + "secp256k1").hashCode().toLong())
    val hash2 = abs((privateKeyHex + "publickey").hashCode().toLong())
    val hash3 = abs((privateKeyHex + "elliptic").hashCode().toLong())
    val hash4 = abs((privateKeyHex + "curve").hashCode().toLong())
    
    // Generate 64-byte public key (x + y coordinates)
    val publicKey = hash1.toString(16).padStart(16, '0') + 
                   hash2.toString(16).padStart(16, '0') + 
                   hash3.toString(16).padStart(16, '0') + 
                   hash4.toString(16).padStart(16, '0')
    
    return publicKey.padStart(128, '0').take(128)
}

fun keccak256(input: String): String {
    // Simulate Keccak-256 hash function
    val hash1 = abs((input + "keccak").hashCode().toLong())
    val hash2 = abs((input + "256").hashCode().toLong())
    val hash3 = abs((input + "ethereum").hashCode().toLong())
    val hash4 = abs((input + "hash").hashCode().toLong())
    
    val result = hash1.toString(16).padStart(16, '0') + 
                hash2.toString(16).padStart(16, '0') + 
                hash3.toString(16).padStart(16, '0') + 
                hash4.toString(16).padStart(16, '0')
    
    return result.padStart(64, '0').take(64)
}

fun getAddress(privateKey: String, coinType: Int): String {
    // Remove 0x prefix if present
    val cleanPrivateKey = privateKey.removePrefix("0x")
    
    // Ensure private key is 64 characters (32 bytes)
    val paddedPrivateKey = cleanPrivateKey.padStart(64, '0')
    
    // Simulate secp256k1 public key generation (64 bytes)
    val publicKey = generatePublicKey(paddedPrivateKey)
    
    // Simulate Keccak-256 hash
    val hash = keccak256(publicKey)
    
    // Take last 20 bytes (40 hex characters) for address
    val addressHex = hash.takeLast(40)
    
    return when (coinType) {
        60 -> "0x$addressHex"  // Ethereum
        else -> "0x$addressHex"
    }
}

// Test the logic
fun main() {
    val testPrivateKey = "0x00000000000000000000000000000000000000000000000000000000e812afe6"
    println("Testing Kotlin address generation...")
    println("Private key: $testPrivateKey")
    
    val address = getAddress(testPrivateKey, 60)
    println("Generated address: $address")
    println("Address length: ${address.length}")
    println("Is valid format: ${address.startsWith("0x") && address.length == 42}")
    
    // Test with different private keys
    val testKeys = listOf(
        "e812afe6",
        "1234567890abcdef",
        "0xbc8be86a"
    )
    
    for (key in testKeys) {
        val addr = getAddress(key, 60)
        println("Key: $key -> Address: $addr")
    }
}

main()