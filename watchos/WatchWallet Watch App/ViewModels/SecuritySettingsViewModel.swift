//
//  SecuritySettingsViewModel.swift
//  WatchWallet Watch App
//
//  ViewModel for security settings
//

import Foundation
import SwiftUI

// MARK: - Security Error Types

enum SecurityError: Error, LocalizedError {
    case wipeFailure(String)
    case verificationFailure(String)
    case emergencyCleanupFailure(String)
    case authenticationRequired
    case invalidPasscode
    case keychainAccessDenied
    
    var errorDescription: String? {
        switch self {
        case .wipeFailure(let message):
            return "Secure wipe failed: \(message)"
        case .verificationFailure(let message):
            return "Cleanup verification failed: \(message)"
        case .emergencyCleanupFailure(let message):
            return "Emergency cleanup failed: \(message)"
        case .authenticationRequired:
            return "Authentication is required to proceed"
        case .invalidPasscode:
            return "Invalid passcode entered"
        case .keychainAccessDenied:
            return "Access to secure storage was denied"
        }
    }
}

@MainActor
class SecuritySettingsViewModel: ObservableObject {
    @Published var isBiometricEnabled = false
    @Published var isBiometricAvailable = false
    @Published var hasPasscode = false
    @Published var autoLockTime: AutoLockOption = .fiveMinutes
    @Published var requireAuthForTransactions = true
    @Published var requireAuthForSensitiveData = true
    
    // MARK: - Properties
    private let secureWalletManager = SecureWalletManager.shared
    
    // MARK: - Initialization
    
    init() {
        checkBiometricAvailability()
        loadSecuritySettings()
    }
    
    // MARK: - Biometric Methods
    
    func checkBiometricAvailability() {
        // Biometric authentication is not available on watchOS
        // Apple Watch uses passcode and wrist detection for security
        isBiometricAvailable = false
    }
    
    func enableBiometric() async -> Bool {
        // Biometric authentication is not available on watchOS
        // This function is kept for UI compatibility but always returns false
        return false
    }
    
    // MARK: - Passcode Methods
    
    func setPasscode(_ passcode: String) {
        // Store passcode securely using Keychain
        let passcodeData = passcode.data(using: .utf8)!
        let result = KeychainManager.shared.storeWalletData(passcodeData, walletId: "user_passcode")
        
        switch result {
        case .success:
            hasPasscode = true
            print("[SecuritySettingsViewModel] Passcode set successfully")
        case .failure(let error):
            print("[SecuritySettingsViewModel] Failed to set passcode: \(error)")
        }
        
        // Update settings
        updateSecuritySettings()
    }
    
    func removePasscode() {
        // Remove passcode from Keychain
        let result = KeychainManager.shared.deleteWalletData(walletId: "user_passcode")
        
        switch result {
        case .success:
            hasPasscode = false
            print("[SecuritySettingsViewModel] Passcode removed successfully")
        case .failure(let error):
            print("[SecuritySettingsViewModel] Failed to remove passcode: \(error)")
        }
        
        // Update settings
        updateSecuritySettings()
    }
    
    func verifyPasscode(_ passcode: String) -> Bool {
        let result = KeychainManager.shared.retrieveWalletData(Data.self, walletId: "user_passcode")
        
        switch result {
        case .success(let storedData):
            let inputData = passcode.data(using: .utf8)!
            return storedData == inputData
        case .failure:
            return false
        }
    }
    
    // MARK: - Auto Lock Methods
    
    func setAutoLockTime(_ option: AutoLockOption) {
        autoLockTime = option
        updateSecuritySettings()
    }
    
    // MARK: - Authentication Settings
    
    func setRequireAuthForTransactions(_ require: Bool) {
        requireAuthForTransactions = require
        updateSecuritySettings()
    }
    
    func setRequireAuthForSensitiveData(_ require: Bool) {
        requireAuthForSensitiveData = require
        updateSecuritySettings()
    }
    
    // MARK: - Settings Management
    
    private func loadSecuritySettings() {
        let settingsResult = secureWalletManager.getSettings()
        
        switch settingsResult {
        case .success(let settings):
            isBiometricEnabled = settings.biometricAuthenticationEnabled
            requireAuthForTransactions = settings.requireAuthenticationForTransactions
            requireAuthForSensitiveData = settings.requireAuthenticationForSensitiveData
            
            // Convert minutes to AutoLockOption
            if settings.autoLockTimeoutMinutes == 0 {
                autoLockTime = .never
            } else {
                autoLockTime = AutoLockOption.allCases.first { 
                    $0.seconds == settings.autoLockTimeoutMinutes * 60 
                } ?? .fiveMinutes
            }
            
            print("[SecuritySettingsViewModel] Security settings loaded successfully")
            
        case .failure(let error):
            print("[SecuritySettingsViewModel] Failed to load security settings: \(error)")
            // Use default settings
            useDefaultSettings()
        }
        
        // Check if passcode exists
        hasPasscode = checkPasscodeExists()
    }
    
    private func updateSecuritySettings() {
        let settingsResult = secureWalletManager.getSettings()
        
        var settings: SecureWalletSettings
        switch settingsResult {
        case .success(let existingSettings):
            settings = existingSettings
        case .failure:
            settings = .default
        }
        
        // Update settings
        settings.biometricAuthenticationEnabled = isBiometricEnabled
        settings.requireAuthenticationForTransactions = requireAuthForTransactions
        settings.requireAuthenticationForSensitiveData = requireAuthForSensitiveData
        settings.autoLockTimeoutMinutes = autoLockTime.seconds == nil ? 0 : autoLockTime.seconds! / 60
        
        // Save settings
        let updateResult = secureWalletManager.updateSettings(settings)
        
        switch updateResult {
        case .success:
            print("[SecuritySettingsViewModel] Security settings updated successfully")
        case .failure(let error):
            print("[SecuritySettingsViewModel] Failed to update security settings: \(error)")
        }
    }
    
    private func useDefaultSettings() {
        isBiometricEnabled = false
        requireAuthForTransactions = true
        requireAuthForSensitiveData = true
        autoLockTime = .fiveMinutes
    }
    
    private func checkPasscodeExists() -> Bool {
        let result = KeychainManager.shared.retrieveWalletData(Data.self, walletId: "user_passcode")
        
        switch result {
        case .success:
            return true
        case .failure:
            return false
        }
    }
    
    // MARK: - Authentication
    
    func authenticate() async -> Bool {
        // On watchOS, authentication is handled differently
        // The watch uses passcode entry when first worn and continuous authentication while on wrist
        
        // If passcode is enabled, we assume the user has already authenticated
        // by wearing the watch and entering the passcode
        if hasPasscode {
            // In a real implementation, you might want to:
            // 1. Check if the watch is on the wrist
            // 2. Request passcode re-entry for sensitive operations
            // 3. Use WatchConnectivity to verify with paired iPhone
            return true
        }
        
        return true // No security enabled
    }
    
    func authenticateForSensitiveOperation() async -> Bool {
        if requireAuthForSensitiveData {
            return await authenticate()
        }
        return true
    }
    
    func authenticateForTransaction() async -> Bool {
        if requireAuthForTransactions {
            return await authenticate()
        }
        return true
    }
    
    // MARK: - Utility Methods
    
    func clearAllSecurityData() {
        // Clear passcode
        let _ = KeychainManager.shared.deleteWalletData(walletId: "user_passcode")
        
        // Reset all settings
        hasPasscode = false
        useDefaultSettings()
        updateSecuritySettings()
        
        print("[SecuritySettingsViewModel] All security data cleared")
    }
    
    // MARK: - Secure Cleanup Methods
    
    /// Perform secure wipe of all sensitive data
    /// This is a destructive operation that cannot be undone
    /// - Returns: Result indicating success or error
    func performSecureWipe() -> Result<Void, SecurityError> {
        print("[SecuritySettingsViewModel] Starting secure wipe...")
        
        // Step 1: Clear all security settings first
        clearAllSecurityData()
        
        // Step 2: Use SecureWalletManager to wipe all wallet data
        let wipeResult = secureWalletManager.secureWipeAllData()
        
        switch wipeResult {
        case .success:
            print("[SecuritySettingsViewModel] Secure wipe completed successfully")
            return .success(())
        case .failure(let error):
            print("[SecuritySettingsViewModel] Secure wipe failed: \(error)")
            return .failure(.wipeFailure(error.localizedDescription))
        }
    }
    
    /// Verify that all sensitive data has been properly cleared
    /// - Returns: Result containing verification status
    func verifySecureCleanup() -> Result<DataCleanupStatus, SecurityError> {
        let verificationResult = secureWalletManager.verifyDataCleanup()
        
        switch verificationResult {
        case .success(let status):
            print("[SecuritySettingsViewModel] Cleanup verification: \(status.isCompletelyCleared ? "CLEAN" : "PARTIAL")")
            return .success(status)
        case .failure(let error):
            print("[SecuritySettingsViewModel] Cleanup verification failed: \(error)")
            return .failure(.verificationFailure(error.localizedDescription))
        }
    }
    
    /// Force cleanup of any remaining data (emergency use)
    /// - Returns: Result indicating success or error
    func emergencyCleanup() -> Result<Void, SecurityError> {
        print("[SecuritySettingsViewModel] Starting emergency cleanup...")
        
        // Clear all security settings
        clearAllSecurityData()
        
        // Force cleanup remaining data
        let forceResult = secureWalletManager.forceCleanupRemainingData()
        
        switch forceResult {
        case .success:
            print("[SecuritySettingsViewModel] Emergency cleanup completed")
            return .success(())
        case .failure(let error):
            print("[SecuritySettingsViewModel] Emergency cleanup failed: \(error)")
            return .failure(.emergencyCleanupFailure(error.localizedDescription))
        }
    }
    
    /// Check if device has any sensitive data that needs cleanup
    /// - Returns: True if cleanup is needed, false otherwise
    func needsSecureCleanup() -> Bool {
        let verificationResult = secureWalletManager.verifyDataCleanup()
        
        switch verificationResult {
        case .success(let status):
            return !status.isCompletelyCleared
        case .failure:
            return true // Assume cleanup is needed if verification fails
        }
    }
    
    /// Get detailed cleanup status for debugging
    /// - Returns: Detailed status information
    func getCleanupStatus() -> String {
        let verificationResult = secureWalletManager.verifyDataCleanup()
        
        switch verificationResult {
        case .success(let status):
            var statusInfo = [
                "Mnemonic cleared: \(status.mnemonicCleared)",
                "Private keys cleared: \(status.privateKeysCleared)",
                "Wallet data cleared: \(status.walletDataCleared)",
                "Settings cleared: \(status.settingsCleared)",
                "Overall status: \(status.isCompletelyCleared ? "CLEAN" : "NEEDS CLEANUP")"
            ]
            
            if !status.remainingItems.isEmpty {
                statusInfo.append("Remaining items: \(status.remainingItems.joined(separator: ", "))")
            }
            
            return statusInfo.joined(separator: "\n")
            
        case .failure(let error):
            return "Status check failed: \(error.localizedDescription)"
        }
    }
}