import Foundation
import CryptoKit
import CommonCrypto

#if canImport(secp256k1)
import secp256k1
#endif

/// WatchOS Crypto Implementation using P256K (secp256k1)
/// Located in WearWalletWatch package to ensure proper linking of secp256k1 dependency.
public class WatchCrypto {
    
    /// Derive an Ethereum address from an xpub and derivation path
    /// Supports BIP32 public key derivation (CKDpub)
    /// - Parameters:
    ///   - xpub: Base58Check encoded extended public key
    ///   - path: Derivation path (e.g. "m/44'/60'/0'/0/0" or "0/0")
    /// - Returns: Derived Ethereum address (0x...)
    public static func deriveAddressFromXpub(xpub: String, derivationPath: String) throws -> String {
        print("[WatchCrypto] deriveAddressFromXpub(path: \(derivationPath))")
        
        // 1. Decode Xpub
        guard let decoded = Base58.decode(xpub) else {
            throw NSError(domain: "WatchCrypto", code: 1, userInfo: [NSLocalizedDescriptionKey: "Invalid xpub encoding"])
        }
        
        guard decoded.count == 82 else {
             throw NSError(domain: "WatchCrypto", code: 2, userInfo: [NSLocalizedDescriptionKey: "Invalid xpub length: \(decoded.count)"])
        }
        
        // 2. Parse Components
        let depth = Int(decoded[4])
        let chainCode = decoded.subdata(in: 13..<45)
        let publicKeyData = decoded.subdata(in: 45..<78)
        
        // 3. Parse Path
        var indices = parseDerivationPath(derivationPath)
        
        // Adjust for absolute paths
        if derivationPath.lowercased().hasPrefix("m/") {
            if indices.count > depth {
                indices = Array(indices.dropFirst(depth))
            } else if indices.count == depth {
                indices = []
            } else {
                 print("[WatchCrypto] Warning: Path depth < Xpub depth. Returning parent key.")
                 indices = []
            }
        }
        
        // 4. Derive
        var currentKeyData = publicKeyData
        var currentChainCode = chainCode
        
        #if canImport(secp256k1)
        for index in indices {
            if index >= 0x80000000 {
                throw NSError(domain: "WatchCrypto", code: 6, userInfo: [NSLocalizedDescriptionKey: "Cannot derive hardened child from public key"])
            }
            
            let key = SymmetricKey(data: currentChainCode)
            var dataToHMAC = Data()
            dataToHMAC.append(currentKeyData)
            dataToHMAC.append(UInt8((index >> 24) & 0xFF))
            dataToHMAC.append(UInt8((index >> 16) & 0xFF))
            dataToHMAC.append(UInt8((index >> 8) & 0xFF))
            dataToHMAC.append(UInt8(index & 0xFF))
            
            let hmac = HMAC<SHA512>.authenticationCode(for: dataToHMAC, using: key)
            let hmacData = Data(hmac)
            
            let I_L = hmacData.prefix(32)
            let I_R = hmacData.suffix(32)
            
            let parentKey = try secp256k1.Signing.PublicKey(dataRepresentation: [UInt8](currentKeyData), format: .compressed)
            let tweak = [UInt8](I_L)
            let childKey = try parentKey.add(tweak)
            
            currentKeyData = Data(childKey.dataRepresentation(format: .compressed))
            currentChainCode = I_R
        }
        
        // 5. Convert to Address
        let finalPublicKey = try secp256k1.Signing.PublicKey(dataRepresentation: [UInt8](currentKeyData), format: .compressed)
        let uncompressedData = finalPublicKey.uncompressedRepresentation
        let dataToHash = Data(uncompressedData.dropFirst())
        let hash = Keccak256.hash(data: dataToHash)
        let addressBytes = hash.suffix(20)
        let address = "0x" + addressBytes.map { String(format: "%02x", $0) }.joined()
        
        // Checksum
        return toChecksumAddress(address)
        
        #else
        throw NSError(domain: "WatchCrypto", code: 5, userInfo: [NSLocalizedDescriptionKey: "secp256k1 library not available"])
        #endif
    }
    
    // MARK: - Helpers
    
    private static func parseDerivationPath(_ path: String) -> [UInt32] {
        var cleanPath = path
        if cleanPath.lowercased().hasPrefix("m/") {
            cleanPath = String(cleanPath.dropFirst(2))
        }
        let components = cleanPath.split(separator: "/")
        return components.compactMap { component -> UInt32? in
            var text = component
            var isHardened = false
            if text.hasSuffix("'") {
                isHardened = true
                text = text.dropLast()
            }
            guard let val = UInt32(text) else { return nil }
            return isHardened ? (val | 0x80000000) : val
        }
    }
    
    private static func toChecksumAddress(_ address: String) -> String {
        let cleanAddress = address.hasPrefix("0x") ? String(address.dropFirst(2)) : address
        let addressLowercase = cleanAddress.lowercased()
        let hash = Keccak256.hash(data: addressLowercase.data(using: .utf8)!).hexString
        
        var result = "0x"
        for (i, char) in addressLowercase.enumerated() {
            if i < hash.count {
                let hashChar = hash[hash.index(hash.startIndex, offsetBy: i)]
                if hashChar >= "8" {
                    result += char.uppercased()
                } else {
                    result += String(char)
                }
            } else {
                result += String(char)
            }
        }
        return result
    }
}

// MARK: - Primitives (Internal to package)

class Base58 {
    static let alphabet = [UInt8]("123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".utf8)
    
    static func decode(_ string: String) -> Data? {
        var answer = [UInt8](repeating: 0, count: string.count * 733 / 1000 + 1)
        for char in string.utf8 {
            guard let index = alphabet.firstIndex(of: char) else { return nil }
            var carry = Int(index)
            for j in (0..<answer.count).reversed() {
                carry += 58 * Int(answer[j])
                answer[j] = UInt8(carry % 256)
                carry /= 256
            }
        }
        return Data(answer.drop(while: { $0 == 0 }))
    }
}

class Keccak256 {
    static func hash(data: Data) -> Data {
        var state = [UInt64](repeating: 0, count: 25)
        let blockSize = 136
        var input = [UInt8](data)
        input.append(0x01)
        while input.count % blockSize != (blockSize - 1) {
             input.append(0)
        }
        input.append(0x80)
        
        for i in stride(from: 0, to: input.count, by: blockSize) {
            let chunk = input[i..<min(i+blockSize, input.count)]
             for j in 0..<chunk.count/8 {
                 var val: UInt64 = 0
                 for k in 0..<8 {
                     val |= UInt64(chunk[chunk.startIndex + j*8 + k]) << (k * 8)
                 }
                 state[j] ^= val
            }
            keccakF1600(state: &state)
        }
        
        var output = Data()
        for i in 0..<4 {
            let val = state[i]
            for k in 0..<8 {
                 output.append(UInt8((val >> (k * 8)) & 0xFF))
            }
        }
        return output
    }
    
    private static func keccakF1600(state: inout [UInt64]) {
        let RC: [UInt64] = [
            0x0000000000000001, 0x0000000000008082, 0x800000000000808A, 0x8000000080008000,
            0x000000000000808B, 0x0000000080000001, 0x8000000080008081, 0x8000000000008009,
            0x000000000000008A, 0x0000000000000088, 0x0000000080008009, 0x000000008000000A,
            0x000000008000808B, 0x800000000000008B, 0x8000000000008089, 0x8000000000008003,
            0x8000000000008002, 0x8000000000000080, 0x000000000000800A, 0x800000008000000A,
            0x8000000080008081, 0x8000000000008080, 0x0000000080000001, 0x8000000080008008
        ]
        let RHO_OFFSETS: [Int] = [0, 1, 62, 28, 27, 36, 44, 6, 55, 20, 3, 10, 43, 25, 39, 41, 45, 15, 21, 8, 18, 2, 61, 56, 14]
        
        for round in 0..<24 {
            var C = [UInt64](repeating: 0, count: 5)
            for x in 0..<5 { C[x] = state[x] ^ state[x+5] ^ state[x+10] ^ state[x+15] ^ state[x+20] }
            var D = [UInt64](repeating: 0, count: 5)
            for x in 0..<5 { D[x] = C[(x+4)%5] ^ rotateLeft(C[(x+1)%5], 1) }
            for i in 0..<25 { state[i] ^= D[i%5] }
            
            var B = [UInt64](repeating: 0, count: 25)
            for x in 0..<5 {
                for y in 0..<5 {
                    let destIndex = y + 5*((2*x + 3*y)%5)
                    let srcIndex = x + 5*y
                    B[destIndex] = rotateLeft(state[srcIndex], RHO_OFFSETS[srcIndex])
                }
            }
            for i in 0..<25 { state[i] = B[i] }
            
            for yY in stride(from: 0, to: 25, by: 5) {
                var rowCopy = [UInt64](repeating: 0, count: 5)
                for x in 0..<5 { rowCopy[x] = state[yY + x] }
                for x in 0..<5 {
                    let next1 = rowCopy[(x+1)%5]
                    let next2 = rowCopy[(x+2)%5]
                    state[yY + x] ^= (~next1 & next2)
                }
            }
            state[0] ^= RC[round]
        }
    }
    
    private static func rotateLeft(_ value: UInt64, _ bits: Int) -> UInt64 {
        return (value << bits) | (value >> (64 - bits))
    }
}

extension Data {
    var hexString: String {
        return map { String(format: "%02x", $0) }.joined()
    }
}
