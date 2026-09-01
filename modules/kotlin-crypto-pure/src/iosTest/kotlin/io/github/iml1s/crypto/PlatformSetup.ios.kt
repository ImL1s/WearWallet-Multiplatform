package io.github.iml1s.crypto

actual fun platformAesGcmTestSetup() {
    // No specific setup needed for iOS Unit Tests (likely uses placeholder or CommonCrypto via cinterop)
    // If iOS tests fail, we might need a Mock here too.
}
