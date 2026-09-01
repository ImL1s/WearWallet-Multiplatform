<div align="center">

**[English](./KEYSTONE_TROUBLESHOOTING.md)** | **[繁體中文](./KEYSTONE_TROUBLESHOOTING.zh-TW.md)**

</div>

# Keystone Hardware Wallet Troubleshooting Guide

This document provides solutions to common issues encountered when integrating Keystone hardware wallets with WearWallet.

## Table of Contents

- [EIP-1559 Signature Issues](#eip-1559-signature-issues)
- [QR Code Problems](#qr-code-problems)
- [Request ID Mismatches](#request-id-mismatches)
- [UI and UX Issues](#ui-and-ux-issues)
- [SDK Selection](#sdk-selection)

## EIP-1559 Signature Issues

### Problem: `Unsupported v parameter: 0`

**Symptoms:**
```
java.lang.IllegalArgumentException: Unsupported v parameter: 0
    at org.web3j.crypto.Sign.getRecId(Sign.java:353)
```

**Root Cause:**
- Keystone returns yParity (`0` or `1`) for EIP-1559 transactions
- Older web3j versions expect traditional v values (`27`/`28` for unprotected, `35+` for EIP-155)
- Web3j's `Sign.getRecId()` rejects `v=0`

**Solution:**
Convert yParity to web3j-compatible format before creating SignatureData:

```kotlin
// Convert EIP-1559 yParity (0/1) to web3j format (27/28)
val adjustedV = when {
    // EIP-1559 yParity: 0 -> 27, 1 -> 28 (web3j compatibility)
    vInt == 0 || vInt == 1 -> (27 + vInt).toByte()
    // Legacy transaction formats
    vInt >= 35 -> ((vInt - 35) / 2 + 27).toByte()
    vInt >= 27 -> vInt.toByte()
    else -> (27 + vInt).toByte() // Default as yParity
}

val signatureData = Sign.SignatureData(
    adjustedV,
    Numeric.hexStringToByteArray("0x$r"),
    Numeric.hexStringToByteArray("0x$s")
)
```

**Alternative Solutions:**
1. **Upgrade web3j**: Use version 4.10.3+ or 5.x that supports yParity natively
2. **Manual RLP encoding**: Implement custom EIP-1559 transaction encoding

**Related Files:**
- `WalletRepositoryImpl.kt:707-714`

## QR Code Problems

### Problem: QR Code Not Displaying

**Symptoms:**
- Keystone scanning screen shows blank space where QR code should be
- UI components render but QR code is missing

**Root Cause:**
- Incorrect data flow from ViewModel to UI component
- QR code data not properly generated or passed through component hierarchy

**Solution:**
Ensure proper data flow in the ViewModel:

```kotlin
// In SendViewModel.enableKeystoneMode()
fun enableKeystoneMode(toAddress: String, amount: String) {
    // Use sendTransaction() to trigger proper Keystone flow
    sendTransaction(toAddress, amount)
}

// In generateKeystoneQRCode()
private fun generateKeystoneQRCode(request: TransactionResult.RequiresHardwareSign) {
    val keystoneRequest = keystoneService.generateEthSignRequest(...)
    
    if (keystoneRequest.qrCodeData.isNotEmpty()) {
        _keystoneQRData.value = keystoneRequest.qrCodeData // Set proper QR data
        _transactionState.value = TransactionState.WaitingForSignature
    }
}
```

**Related Files:**
- `SendViewModel.kt:200-245`
- `KeystoneSendScreen.kt:202-205`

### Problem: QR Code Case Sensitivity

**Symptoms:**
- Keystone device restarts when scanning QR code
- QR code becomes unrecognizable

**Root Cause:**
- UR encoding case sensitivity requirements
- Incorrect SDK usage (EvmSDK vs EthereumSDK)

**Solution:**
Use correct SDK and case conversion:

```kotlin
// Use KeystoneEthereumSDK for Ethereum transactions
import com.keystone.sdk.KeystoneEthereumSDK

// Convert to uppercase to match MetaMask format
val urString = urEncoder.nextPart().uppercase()
```

**Related Files:**
- `KeystoneService.kt:4-6, 70`

## Request ID Mismatches

### Problem: Concurrent Request Processing

**Symptoms:**
```
Request ID mismatch! Expected: 75a51d77-..., Got: c7382837-...
```

**Root Cause:**
- Multiple coroutines processing the same signature result
- Lack of proper request-response pairing

**Impact:**
- **Does not affect transaction success**
- Creates error logs that may confuse developers
- One request succeeds, others fail with ID mismatch

**Current Status:**
This is a non-critical issue that serves as a protection mechanism against signature misuse.

**Future Improvement:**
Implement request ID-based routing to prevent multiple coroutines from processing the same signature:

```kotlin
// Potential solution (not implemented yet)
private val activeRequests = ConcurrentHashMap<String, CompletableDeferred<String>>()

fun handleSignatureResult(signedQRData: String) {
    val result = keystoneService.parseSignature(signedQRData)
    val request = activeRequests.remove(result.requestId)
    request?.complete(signedQRData)
}
```

**Related Files:**
- `WalletRepositoryImpl.kt:620-640`

## UI and UX Issues

### Problem: Non-Scrollable Content

**Symptoms:**
- Content overflow on smaller watch screens
- Unable to access all UI elements

**Solution:**
Add scrolling support to main content areas:

```kotlin
@Composable
private fun KeystoneQRContent(...) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState) // Add scrolling
            .padding(12.dp)
    ) {
        // Content...
    }
}
```

**Related Files:**
- `KeystoneSendScreen.kt:174-182`

### Problem: Poor Scan Feedback

**Symptoms:**
- Users unaware when phone starts scanning
- No visual indication of scan state

**Solution:**
Implement dynamic scan state with visual feedback:

```kotlin
// In scan button
when (scanState) {
    is ScanState.Scanning -> {
        CircularProgressIndicator(modifier = Modifier.size(16.dp))
        Text("手機掃描中...")
    }
    is ScanState.Success -> {
        Icon(Icons.Default.Hardware)
        Text("掃描完成")
    }
    // ... other states
}
```

**Related Files:**
- `KeystoneSendScreen.kt:332-380`

## SDK Selection

### Problem: Wrong SDK Usage

**Symptoms:**
- Different UR format generation (`ur:evm-sign-request` vs `ur:eth-sign-request`)
- Hardware wallet compatibility issues

**Solution:**
Use `KeystoneEthereumSDK` for Ethereum-based chains:

```kotlin
// Correct imports
import com.keystone.sdk.KeystoneSDK
import com.keystone.module.EthSignRequest
import com.keystone.sdk.KeystoneEthereumSDK

// Correct usage
val ethSignRequest = EthSignRequest(
    requestId,
    cleanTxHex,
    KeystoneEthereumSDK.DataType.TypedTransaction, // For EIP-1559
    chainId.toInt(),
    derivationPath,
    masterFingerprint,
    fromAddress,
    APP_ORIGIN
)
```

**Why EthereumSDK over EvmSDK:**
- Generates `ur:eth-sign-request` format (compatible with MetaMask)
- Better support for EIP-1559 transactions
- More widely supported by hardware wallets

**Related Files:**
- `KeystoneService.kt:4-6, 56-67`

## Best Practices

### 1. Error Handling
- Always validate request IDs before processing signatures
- Implement fallback mechanisms for signature parsing failures
- Log detailed information for debugging

### 2. Testing
- Test with actual Keystone hardware when possible
- Verify QR code readability on target devices
- Test various transaction types (legacy, EIP-1559)

### 3. Version Compatibility
- Keep web3j updated to latest stable version
- Monitor Keystone SDK updates for bug fixes
- Test signature compatibility across different wallet software

### 4. User Experience
- Provide clear scan state feedback
- Implement scrollable interfaces for smaller screens
- Add appropriate loading states and error messages

## Version Compatibility

### Web3j
- **Minimum**: 4.10.3 (for EIP-1559 yParity support)
- **Recommended**: 5.x (latest stable)
- **Current**: 4.12.3-android (as per gradle/libs.versions.toml)

### Keystone SDK
- **Required**: Latest version with KeystoneEthereumSDK
- **Critical**: Ensure UR encoding/decoding compatibility

## Additional Resources

- [Keystone Official Documentation](https://github.com/KeystoneHQ/keystone-sdk-android)
- [EIP-1559 Specification](https://eips.ethereum.org/EIPS/eip-1559)
- [UR Specification](https://github.com/BlockchainCommons/Research/blob/master/papers/bcr-2020-005-ur.md)
- [Web3j Documentation](https://docs.web3j.io/)