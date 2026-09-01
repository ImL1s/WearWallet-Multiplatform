//
//  SecuritySettingsView.swift
//  WatchWallet Watch App
//
//  Security settings for biometric authentication and passcode
//

import SwiftUI

struct SecuritySettingsView: View {
    @StateObject private var viewModel = SecuritySettingsViewModel()
    @State private var showPasscodeSetup = false
    @State private var showChangePasscode = false
    @State private var showAuthenticationError = false
    
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Biometric Authentication
                VStack(alignment: .leading, spacing: 12) {
                    Text("生物識別")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.secondary)
                    
                    Toggle(isOn: $viewModel.isBiometricEnabled) {
                        HStack {
                            Image(systemName: "faceid")
                                .font(.system(size: 16))
                                .foregroundColor(.blue)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Face ID")
                                    .font(.system(size: 14))
                                Text("使用 Face ID 解鎖錢包")
                                    .font(.system(size: 11))
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                    .toggleStyle(SwitchToggleStyle(tint: .blue))
                    .disabled(!viewModel.isBiometricAvailable)
                }
                .padding()
                .background(Color.white.opacity(0.05))
                .cornerRadius(8)
                
                // Passcode Settings
                VStack(alignment: .leading, spacing: 12) {
                    Text("密碼保護")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.secondary)
                    
                    if viewModel.hasPasscode {
                        Button(action: { showChangePasscode = true }) {
                            HStack {
                                Image(systemName: "lock.rotation")
                                    .font(.system(size: 16))
                                    .foregroundColor(.blue)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("更改密碼")
                                        .font(.system(size: 14))
                                    Text("修改您的錢包密碼")
                                        .font(.system(size: 11))
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 12))
                                    .foregroundColor(.secondary)
                            }
                            .padding(.vertical, 8)
                        }
                        .buttonStyle(.plain)
                        
                        Divider()
                        
                        Button(action: { viewModel.removePasscode() }) {
                            HStack {
                                Image(systemName: "lock.open")
                                    .font(.system(size: 16))
                                    .foregroundColor(.red)
                                Text("移除密碼")
                                    .font(.system(size: 14))
                                    .foregroundColor(.red)
                                Spacer()
                            }
                            .padding(.vertical, 8)
                        }
                        .buttonStyle(.plain)
                    } else {
                        Button(action: { showPasscodeSetup = true }) {
                            HStack {
                                Image(systemName: "lock.fill")
                                    .font(.system(size: 16))
                                    .foregroundColor(.blue)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("設定密碼")
                                        .font(.system(size: 14))
                                    Text("為錢包添加密碼保護")
                                        .font(.system(size: 11))
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 12))
                                    .foregroundColor(.secondary)
                            }
                            .padding(.vertical, 8)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding()
                .background(Color.white.opacity(0.05))
                .cornerRadius(8)
                
                // Auto-lock Settings
                VStack(alignment: .leading, spacing: 12) {
                    Text("自動鎖定")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.secondary)
                    
                    ForEach(AutoLockOption.allCases, id: \.self) { option in
                        Button(action: { viewModel.setAutoLockTime(option) }) {
                            HStack {
                                Text(option.title)
                                    .font(.system(size: 14))
                                    .foregroundColor(viewModel.autoLockTime == option ? .white : .primary)
                                Spacer()
                                if viewModel.autoLockTime == option {
                                    Image(systemName: "checkmark")
                                        .font(.system(size: 12))
                                        .foregroundColor(.white)
                                }
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(viewModel.autoLockTime == option ? Color.blue : Color.clear)
                            .cornerRadius(6)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding()
                .background(Color.white.opacity(0.05))
                .cornerRadius(8)
                
                // Security Tips
                SecurityTipsCard()
            }
            .padding()
        }
        .navigationTitle("安全設定")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showPasscodeSetup) {
            PasscodeSetupView { passcode in
                viewModel.setPasscode(passcode)
                showPasscodeSetup = false
            }
        }
        .sheet(isPresented: $showChangePasscode) {
            PasscodeChangeView { newPasscode in
                viewModel.setPasscode(newPasscode)
                showChangePasscode = false
            }
        }
        .alert("認證錯誤", isPresented: $showAuthenticationError) {
            Button("確定", role: .cancel) {}
        } message: {
            Text("無法啟用生物識別認證。請確保您的設備支援 Face ID 並已設定。")
        }
        .onChange(of: viewModel.isBiometricEnabled) { oldValue, newValue in
            if newValue && !oldValue {
                Task {
                    let success = await viewModel.enableBiometric()
                    if !success {
                        showAuthenticationError = true
                        viewModel.isBiometricEnabled = false
                    }
                }
            }
        }
    }
}

struct SecurityTipsCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: "shield.checkerboard")
                    .font(.system(size: 16))
                    .foregroundColor(.green)
                Text("安全提示")
                    .font(.system(size: 13, weight: .medium))
            }
            
            VStack(alignment: .leading, spacing: 6) {
                SecurityTip(text: "使用強密碼保護您的錢包")
                SecurityTip(text: "定期備份您的助記詞")
                SecurityTip(text: "不要與他人分享您的密碼或助記詞")
                SecurityTip(text: "在安全的環境下使用錢包")
            }
        }
        .padding()
        .background(Color.green.opacity(0.1))
        .cornerRadius(8)
    }
}

struct SecurityTip: View {
    let text: String
    
    var body: some View {
        HStack(alignment: .top, spacing: 6) {
            Text("•")
                .font(.system(size: 12))
                .foregroundColor(.green)
            Text(text)
                .font(.system(size: 11))
                .foregroundColor(.secondary)
        }
    }
}

// Passcode Setup View
struct PasscodeSetupView: View {
    @State private var passcode = ""
    @State private var confirmPasscode = ""
    @State private var isConfirming = false
    @State private var showError = false
    @Environment(\.dismiss) var dismiss
    
    let onComplete: (String) -> Void
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Image(systemName: isConfirming ? "lock.rotation" : "lock.fill")
                    .font(.system(size: 40))
                    .foregroundColor(.blue)
                
                Text(isConfirming ? "確認密碼" : "設定密碼")
                    .font(.system(size: 16, weight: .medium))
                
                Text(isConfirming ? "請再次輸入您的密碼" : "請輸入 6 位數密碼")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
                
                PasscodeField(passcode: isConfirming ? $confirmPasscode : $passcode)
                
                if showError {
                    Text("密碼不符，請重試")
                        .font(.system(size: 11))
                        .foregroundColor(.red)
                }
            }
            .padding()
            .navigationTitle("設定密碼")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
            }
            .onChange(of: passcode) { oldValue, newValue in
                if newValue.count == 6 && !isConfirming {
                    isConfirming = true
                }
            }
            .onChange(of: confirmPasscode) { oldValue, newValue in
                if newValue.count == 6 {
                    if confirmPasscode == passcode {
                        onComplete(passcode)
                    } else {
                        showError = true
                        confirmPasscode = ""
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                            showError = false
                        }
                    }
                }
            }
        }
    }
}

// Passcode Change View
struct PasscodeChangeView: View {
    @State private var currentPasscode = ""
    @State private var newPasscode = ""
    @State private var confirmPasscode = ""
    @State private var step = 0
    @State private var showError = false
    @State private var errorMessage = ""
    @Environment(\.dismiss) var dismiss
    
    let onComplete: (String) -> Void
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Image(systemName: stepIcon)
                    .font(.system(size: 40))
                    .foregroundColor(.blue)
                
                Text(stepTitle)
                    .font(.system(size: 16, weight: .medium))
                
                Text(stepSubtitle)
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
                
                PasscodeField(passcode: bindingForCurrentStep)
                
                if showError {
                    Text(errorMessage)
                        .font(.system(size: 11))
                        .foregroundColor(.red)
                }
            }
            .padding()
            .navigationTitle("更改密碼")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
            }
        }
    }
    
    private var stepIcon: String {
        switch step {
        case 0: return "lock.open"
        case 1: return "lock.fill"
        case 2: return "lock.rotation"
        default: return "lock"
        }
    }
    
    private var stepTitle: String {
        switch step {
        case 0: return "輸入當前密碼"
        case 1: return "設定新密碼"
        case 2: return "確認新密碼"
        default: return ""
        }
    }
    
    private var stepSubtitle: String {
        switch step {
        case 0: return "請輸入您的當前密碼"
        case 1: return "請輸入 6 位數新密碼"
        case 2: return "請再次輸入新密碼"
        default: return ""
        }
    }
    
    private var bindingForCurrentStep: Binding<String> {
        switch step {
        case 0: return $currentPasscode
        case 1: return $newPasscode
        case 2: return $confirmPasscode
        default: return .constant("")
        }
    }
}

// Passcode Field Component
struct PasscodeField: View {
    @Binding var passcode: String
    
    var body: some View {
        HStack(spacing: 12) {
            ForEach(0..<6) { index in
                Circle()
                    .fill(index < passcode.count ? Color.blue : Color.white.opacity(0.2))
                    .frame(width: 12, height: 12)
            }
        }
        .onAppear {
            // In a real implementation, you would show a numeric keyboard
        }
    }
}

// Auto-lock Options
enum AutoLockOption: CaseIterable {
    case immediately
    case oneMinute
    case fiveMinutes
    case fifteenMinutes
    case never
    
    var title: String {
        switch self {
        case .immediately: return "立即"
        case .oneMinute: return "1 分鐘"
        case .fiveMinutes: return "5 分鐘"
        case .fifteenMinutes: return "15 分鐘"
        case .never: return "永不"
        }
    }
    
    var seconds: Int? {
        switch self {
        case .immediately: return 0
        case .oneMinute: return 60
        case .fiveMinutes: return 300
        case .fifteenMinutes: return 900
        case .never: return nil
        }
    }
}

#Preview {
    NavigationStack {
        SecuritySettingsView()
    }
}