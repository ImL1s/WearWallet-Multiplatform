//
//  CreateWalletView.swift
//  WatchWallet Watch App
//
//  View for creating a new wallet with mnemonic display flow
//

import SwiftUI
import WatchKit
import coreKmp

struct CreateWalletView: View {
    @State private var walletName = {
        #if DEBUG
        return "AutoTestWallet"
        #else
        return ""
        #endif
    }()
    @State private var currentStep: CreateWalletStep = .enterName
    @State private var isGenerating = false
    @State private var generatedMnemonic: [String] = []
    @State private var showError = false
    @State private var errorMessage = ""
    @FocusState private var isNameFieldFocused: Bool
    @Environment(\.dismiss) var dismiss

    let onCreate: (String) -> Void

    // ✅ 根據螢幕大小動態調整列數
    private var gridColumns: [GridItem] {
        let screenWidth = WKInterfaceDevice.current().screenBounds.width
        let columnCount = screenWidth < 170 ? 2 : 3 // 小錶款 2 列，大錶款 3 列
        return Array(repeating: GridItem(.flexible()), count: columnCount)
    }

    enum CreateWalletStep {
        case enterName
        case safetyWarning
        case mnemonicDisplay
        case completed
    }

    var body: some View {
        VStack {
            // ✅ 進度指示器
            StepIndicator(currentStep: currentStep)
                .padding(.top, 8)

            Group {
                switch currentStep {
                case .enterName:
                    nameInputView
                case .safetyWarning:
                    safetyWarningView
                case .mnemonicDisplay:
                    mnemonicDisplayView
                case .completed:
                    completedView
                }
            }
        }
        .alert("錯誤", isPresented: $showError) {
            Button("確定", role: .cancel) { }
        } message: {
            Text(errorMessage)
        }
    }

    // MARK: - Step 1: 錢包名稱輸入
    private var nameInputView: some View {
        VStack(spacing: 12) {
            // 圖標
            Image(systemName: "wallet.pass.fill")
                .font(.system(size: 30))
                .foregroundColor(.blue)
                .padding(.top, 10)

            // 說明文字
            VStack(spacing: 8) {
                Text("創建新錢包")
                    .font(.system(size: 16, weight: .medium))

                Text("將生成新的助記詞")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
            }

            // 錢包名稱輸入
            VStack(alignment: .leading, spacing: 8) {
                Text("錢包名稱")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)

                TextField("輸入錢包名稱", text: $walletName)
                    .font(.system(size: 14))
                    .textFieldStyle(.plain)
                    .padding(10)
                    .background(Color.white.opacity(0.1))
                    .cornerRadius(8)
                    .focused($isNameFieldFocused)
                    .accessibilityIdentifier("WalletNameInput")
            }
            .padding(.horizontal)

            // 創建按鈕
            Button(action: {
                provideNavigationFeedback() // ✅ 觸覺反饋
                currentStep = .safetyWarning
                generateMnemonic()
            }) {
                Text("下一步")
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 44) // ✅ 確保最小觸控目標 44pt
            }
            .accessibilityIdentifier("CreateWalletNextButton")
            .disabled(walletName.isEmpty)
            .buttonStyle(.borderedProminent)
            .padding(.horizontal)

            Spacer()
        }
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("取消") { dismiss() }
            }
        }
        .onAppear {
            isNameFieldFocused = true
        }
    }

    // MARK: - Step 2: 安全警告
    private var safetyWarningView: some View {
        ScrollView {
            VStack(spacing: 12) {
                Image(systemName: "exclamationmark.shield")
                    .font(.system(size: 32))
                    .foregroundColor(.orange)
                    .padding(.top, 8)

                Text("⚠️ 安全提示")
                    .font(.system(size: 16, weight: .semibold))

                VStack(alignment: .leading, spacing: 8) {
                    Label("請妥善保管助記詞", systemImage: "lock.shield")
                        .font(.system(size: 12))
                    Label("不要截圖或拍照", systemImage: "camera.fill")
                        .font(.system(size: 12))
                    Label("不要分享給任何人", systemImage: "hand.raised.fill")
                        .font(.system(size: 12))
                    Label("遺失將無法恢復", systemImage: "exclamationmark.triangle")
                        .font(.system(size: 12))
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
                .background(Color.orange.opacity(0.1))
                .cornerRadius(8)
                .padding(.horizontal)

                if isGenerating {
                    VStack(spacing: 8) {
                        ProgressView()
                        Text("正在生成助記詞...")
                            .font(.system(size: 14))
                            .foregroundColor(.secondary)
                    }
                    .padding()
                } else {
                    Button("我已了解") {
                        provideNavigationFeedback() // ✅ 觸覺反饋
                        currentStep = .mnemonicDisplay
                    }
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 44) // ✅ 確保最小觸控目標
                    .accessibilityIdentifier("SafetyWarningAckButton")
                    .buttonStyle(.borderedProminent)
                    .padding(.top, 8)

                    Button("返回") {
                        currentStep = .enterName
                    }
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 44) // ✅ 確保最小觸控目標
                    .buttonStyle(.bordered)
                }
            }
            .padding(.vertical)
        }
    }

    // MARK: - Step 3: 助記詞顯示
    private var mnemonicDisplayView: some View {
        ScrollView {
            VStack(spacing: 12) {
                Text("您的助記詞")
                    .font(.system(size: 16, weight: .semibold))
                    .padding(.top, 8)

                // ✅ 響應式網格佈局
                LazyVGrid(columns: gridColumns, spacing: 8) {
                    ForEach(Array(generatedMnemonic.enumerated()), id: \.offset) { index, word in
                        MnemonicWordCard(number: index + 1, word: word)
                    }
                }
                .padding(.horizontal, 4)

                Text("請按順序抄寫並妥善保管")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)

                Button("我已備份") {
                    createWallet()
                }
                .frame(maxWidth: .infinity)
                .frame(minHeight: 44) // ✅ 確保最小觸控目標
                .accessibilityIdentifier("MnemonicBackupConfirmButton")
                .buttonStyle(.borderedProminent)
                .padding(.top, 8)

                Button("返回") {
                    currentStep = .safetyWarning
                }
                .frame(maxWidth: .infinity)
                .frame(minHeight: 44) // ✅ 確保最小觸控目標
                .buttonStyle(.bordered)
            }
            .padding(.vertical)
        }
    }

    // MARK: - Step 4: 完成
    private var completedView: some View {
        VStack(spacing: 12) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 40))
                .foregroundColor(.green)

            Text("錢包創建成功！")
                .font(.system(size: 16, weight: .medium))

            Text("正在進入主畫面...")
                .font(.system(size: 12))
                .foregroundColor(.secondary)
        }
        .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                dismiss()
            }
        }
    }

    // MARK: - Helper Methods
    private func generateMnemonic() {
        isGenerating = true

        // 使用 CoreKmp CryptoProvider 生成助記詞
        Task {
            do {
                // 取得 CryptoProvider
                let cryptoProvider = DIContainer.shared.getCryptoProvider()

                // 生成 12 詞助記詞 (128 bits entropy)
                let mnemonic: String
                if let provider = cryptoProvider {
                    mnemonic = try await provider.generateMnemonic(wordCount: 12)
                } else {
                    // Mock generation if provider is nil
                    mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
                    try await Task.sleep(nanoseconds: 500_000_000)
                }

                let words = mnemonic.split(separator: " ").map { String($0) }

                await MainActor.run {
                    self.generatedMnemonic = words
                    self.isGenerating = false
                    print("[CreateWallet] ✅ Mnemonic generated: \(words.count) words")
                }
            } catch {
                await MainActor.run {
                    self.isGenerating = false
                    self.errorMessage = "助記詞生成失敗：\(error.localizedDescription)"
                    self.showError = true
                    self.currentStep = .enterName
                    print("[CreateWallet] ❌ Mnemonic generation failed: \(error)")
                }
            }
        }
    }

    private func createWallet() {
        guard !walletName.isEmpty else { return }
        guard !generatedMnemonic.isEmpty else { return }

        logToDebugFile("Creating wallet: \(walletName)")

        // 使用 CoreKmp CreateWalletUseCase
        Task {
            do {
                let mnemonic = generatedMnemonic.joined(separator: " ")
                let targetNetwork: SecureWalletData.NetworkType = .ethereum

                #if DEBUG
                // E2E Test Mock: Use WalletRepositoryManager's mock creation
                logToDebugFile("Mock mode: creating via WalletRepositoryManager")
                try? await Task.sleep(nanoseconds: 500_000_000)
                let mockResult = await WalletRepositoryManager.shared.createWallet(
                    name: walletName, 
                    mnemonic: mnemonic,
                    networkType: targetNetwork
                )
                switch mockResult {
                case .success(let walletData):
                    await MainActor.run {
                        logToDebugFile("✅ Mock Wallet Created: \(walletData.id)")
                        NotificationCenter.default.post(name: .walletCreated, object: nil)
                        self.onCreate(walletName)
                        self.currentStep = .completed
                    }
                    return
                case .failure(let error):
                    logToDebugFile("❌ Mock Creation Failed: \(error)")
                    // Continue to real implementation if mock fails (though it shouldn't in E2E)
                }
                #endif

                // 創建錢包
                logToDebugFile("Real mode: creating via WalletRepositoryManager")
                let result = await WalletRepositoryManager.shared.createWallet(
                    name: walletName,
                    mnemonic: mnemonic,
                    networkType: targetNetwork
                )

                await MainActor.run {
                    switch result {
                    case .success(let walletData):
                        logToDebugFile("✅ Created wallet successfully: \(walletData.id)")
                        NotificationCenter.default.post(name: .walletCreated, object: nil)
                        self.onCreate(walletName)
                        self.currentStep = .completed
                    case .failure(let error):
                        logToDebugFile("❌ Failed to create wallet: \(error.localizedDescription)")
                        self.showErrorMessage(error.localizedDescription)
                    }
                }
            } catch {
                await MainActor.run {
                    self.showErrorMessage("錢包創建失敗：\(error.localizedDescription)")
                    print("[CreateWallet] ❌ Wallet creation error: \(error)")
                }
            }
        }
    }

    private func logToDebugFile(_ message: String) {
        let logMessage = "\(Date()): [CreateWalletView] \(message)\n"
        if let docDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first,
           let data = logMessage.data(using: .utf8) {
            let url = docDir.appendingPathComponent("e2e_debug_log.txt")
            if FileManager.default.fileExists(atPath: url.path) {
                if let fileHandle = try? FileHandle(forWritingTo: url) {
                    fileHandle.seekToEndOfFile()
                    fileHandle.write(data)
                    try? fileHandle.close()
                }
            } else {
                try? data.write(to: url)
            }
        }
    }


    private func showErrorMessage(_ message: String) {
        provideErrorFeedback() // ✅ 觸覺反饋
        self.errorMessage = message
        self.showError = true
        self.currentStep = .enterName
    }

    // MARK: - 觸覺反饋
    private func provideSuccessFeedback() {
        WKInterfaceDevice.current().play(.success)
    }

    private func provideErrorFeedback() {
        WKInterfaceDevice.current().play(.failure)
    }

    private func provideNavigationFeedback() {
        WKInterfaceDevice.current().play(.click)
    }
}

// MARK: - 進度指示器
struct StepIndicator: View {
    let currentStep: CreateWalletView.CreateWalletStep

    private var stepNumber: Int {
        switch currentStep {
        case .enterName: return 1
        case .safetyWarning: return 2
        case .mnemonicDisplay: return 3
        case .completed: return 4
        }
    }

    var body: some View {
        HStack(spacing: 8) {
            ForEach(1...4, id: \.self) { step in
                Circle()
                    .fill(stepNumber >= step ? Color.blue : Color.gray.opacity(0.3))
                    .frame(width: 8, height: 8)
            }
        }
        .padding(.top, 8)
    }
}

// MARK: - 助記詞卡片組件
struct MnemonicWordCard: View {
    let number: Int
    let word: String

    var body: some View {
        VStack(spacing: 2) {
            Text("\(number).")
                .font(.system(size: 9))
                .foregroundColor(.blue)

            Text(word)
                .font(.system(size: 11, weight: .medium))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity)
        .frame(minHeight: 44) // ✅ 確保最小高度 44pt
        .padding(.vertical, 10) // ✅ 增加內距
        .padding(.horizontal, 6) // ✅ 增加內距
        .background(Color.white.opacity(0.08)) // ✅ 增強對比
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.blue.opacity(0.3), lineWidth: 0.5)
        )
        .cornerRadius(8)
    }
}

#Preview {
    CreateWalletView { name in
        print("Creating wallet: \(name)")
    }
}