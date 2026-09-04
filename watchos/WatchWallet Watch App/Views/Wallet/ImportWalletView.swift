//
//  ImportWalletView.swift
//  WatchWallet Watch App
//
//  View for importing an existing wallet
//

import SwiftUI
import coreKmp

struct ImportWalletView: View {
    @State private var walletName = {
        #if DEBUG
        return "ImportedTestWallet"
        #else
        return ""
        #endif
    }()
    @State private var mnemonicWords: [String] = {
        #if DEBUG
        let words = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(separator: " ").map { String($0) }
        var arr = Array(repeating: "", count: 12)
        for (i, word) in words.enumerated() {
            if i < 12 { arr[i] = word }
        }
        return arr
        #else
        return Array(repeating: "", count: 12)
        #endif
    }()
    @State private var isImporting = false
    @State private var selectedWordCount = 12
    @State private var showWordCountPicker = false
    @State private var showError = false
    @State private var errorMessage = ""
    @State private var showSuccess = false
    @FocusState private var focusedWordIndex: Int?
    @Environment(\.dismiss) var dismiss
    
    let onImport: (String, String) -> Void
    let wordCounts = [12, 15, 18, 21, 24]
    
    var body: some View {
        // 使用 ScrollView 讓內容可以滑動，適應 watchOS 小螢幕
        ScrollView {
            VStack(spacing: 8) {
                // 說明
                VStack(spacing: 6) {
                    Image(systemName: "square.and.arrow.down")
                        .font(.system(size: 24))
                        .foregroundColor(.green)
                    
                    Text("導入錢包")
                        .font(.system(size: 14, weight: .medium))
                    
                    Text("輸入助記詞恢復錢包")
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                }
                .padding(.top, 8)
                
                // 錢包名稱
                VStack(alignment: .leading, spacing: 4) {
                    Text("錢包名稱")
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                    
                    TextField("輸入錢包名稱", text: $walletName)
                        .font(.system(size: 12))
                        .textFieldStyle(.plain)
                        .padding(6)
                        .background(Color.white.opacity(0.1))
                        .cornerRadius(6)
                        .accessibilityIdentifier("ImportWalletNameInput")
                }
                .padding(.horizontal, 12)
                
                // 助記詞數量選擇
                Button(action: { showWordCountPicker = true }) {
                    HStack {
                        Text("助記詞數量")
                            .font(.system(size: 11))
                            .foregroundColor(.secondary)
                        Spacer()
                        Text("\(selectedWordCount) 個詞")
                            .font(.system(size: 11))
                        Image(systemName: "chevron.down")
                            .font(.system(size: 9))
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 6)
                    .background(Color.white.opacity(0.1))
                    .cornerRadius(6)
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 12)
                
                // 助記詞輸入
                VStack(spacing: 6) {
                    Text("助記詞")
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 12)
                    
                    // 使用簡化的輸入方式
                    // accessibilityIdentifier 在 MnemonicInputView 內部的 TextField 上設置
                    MnemonicInputView(
                        words: $mnemonicWords,
                        wordCount: selectedWordCount
                    )

                }
                
                // 導入按鈕 - 確保在可見區域
                Button(action: importWallet) {
                    HStack {
                        if isImporting {
                            ProgressView()
                                .scaleEffect(0.7)
                                .tint(.white)
                        } else {
                            Text("導入錢包")
                                .font(.system(size: 12, weight: .medium))
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background(canImport ? Color.green : Color.gray)
                    .foregroundColor(.white)
                    .cornerRadius(8)
                }
                .accessibilityIdentifier("ImportWalletConfirmButton")
                .disabled(!canImport || isImporting)
                .padding(.horizontal, 12)
                .padding(.top, 4)
                
                // 錯誤提示
                if showError {
                    HStack {
                        Image(systemName: "exclamationmark.circle")
                            .font(.system(size: 10))
                        Text(errorMessage)
                            .font(.system(size: 10))
                    }
                    .foregroundColor(.red)
                    .padding(.horizontal, 12)
                }
                
                // 成功提示
                if showSuccess {
                    HStack {
                        Image(systemName: "checkmark.circle")
                            .font(.system(size: 10))
                        Text("錢包導入成功！")
                            .font(.system(size: 10))
                    }
                    .foregroundColor(.green)
                    .padding(.horizontal, 12)
                }
                
                // 安全警告
                HStack(spacing: 4) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 10))
                        .foregroundColor(.orange)
                    Text("請確保在安全環境下輸入助記詞")
                        .font(.system(size: 9))
                        .foregroundColor(.secondary)
                }
                .padding(.horizontal, 12)
                .padding(.bottom, 10)
            }
        }
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("取消") { dismiss() }
            }
        }
        .sheet(isPresented: $showWordCountPicker) {
            WordCountPicker(
                selectedCount: $selectedWordCount,
                onSelect: { count in
                    selectedWordCount = count
                    mnemonicWords = Array(repeating: "", count: count)
                    showWordCountPicker = false
                }
            )
        }
    }
    
    private var canImport: Bool {
        !walletName.isEmpty && mnemonicWords.prefix(selectedWordCount).allSatisfy { !$0.isEmpty }
    }
    
    private func importWallet() {
        let mnemonic = mnemonicWords.prefix(selectedWordCount).joined(separator: " ")
        logToDebugFile("Importing wallet: \(walletName)")

        isImporting = true
        showError = false
        showSuccess = false

        // 驗證助記詞格式
        let words = mnemonic.split(separator: " ").map { String($0) }
        if words.count != selectedWordCount {
            logToDebugFile("❌ Word count mismatch: expected \(selectedWordCount), got \(words.count)")
            showError = true
            errorMessage = "助記詞數量不正確"
            isImporting = false
            return
        }

        // 使用 CoreKmp ImportWalletUseCase
        Task {
            do {
                #if DEBUG
                // E2E Test Mock: Use WalletRepositoryManager's mock creation
                logToDebugFile("Mock mode: importing via WalletRepositoryManager")
                try? await Task.sleep(nanoseconds: 500_000_000)
                let mockResult = await WalletRepositoryManager.shared.importWallet(name: walletName, mnemonic: mnemonic)
                switch mockResult {
                case .success(let walletData):
                    await MainActor.run {
                        logToDebugFile("✅ Mock Wallet Imported: \(walletData.id)")
                        self.showSuccess = true
                        self.isImporting = false
                        NotificationCenter.default.post(name: .walletCreated, object: nil)
                        self.onImport(walletName, mnemonic)
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                            self.dismiss()
                        }
                    }
                    return
                case .failure(let error):
                    logToDebugFile("❌ Mock Import Failed: \(error)")
                    // Continue to real implementation if mock fails
                }
                #endif

                // 導入錢包
                logToDebugFile("Real mode: importing via WalletRepositoryManager")
                let result = await WalletRepositoryManager.shared.importWallet(
                    name: walletName,
                    mnemonic: mnemonic,
                    networkType: .ethereum
                )

                await MainActor.run {
                    switch result {
                    case .success(let walletData):
                        logToDebugFile("✅ Imported wallet successfully: \(walletData.id)")
                        self.showSuccess = true
                        self.isImporting = false
                        NotificationCenter.default.post(name: .walletCreated, object: nil)
                        self.onImport(walletName, mnemonic)
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                            self.dismiss()
                        }
                    case .failure(let error):
                        logToDebugFile("❌ Failed to import wallet: \(error.localizedDescription)")
                        self.errorMessage = error.localizedDescription
                        self.showError = true
                        self.isImporting = false
                    }
                }
            } catch {
                await MainActor.run {
                    self.errorMessage = "錢包導入失敗：\(error.localizedDescription)"
                    self.showError = true
                    self.isImporting = false
                    print("[ImportWallet] ❌ Import error: \(error)")
                }
            }
        }
    }

    private func logToDebugFile(_ message: String) {
        let logMessage = "\(Date()): [ImportWalletView] \(message)\n"
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
}

// 助記詞輸入組件 - 優化適用於滑動界面
struct MnemonicInputView: View {
    @Binding var words: [String]
    let wordCount: Int
    @State private var fullText = ""
    @FocusState private var isTextFieldFocused: Bool
    
    var body: some View {
        VStack(spacing: 6) {
            // 使用 TextField 替代 TextEditor（watchOS 不支援 TextEditor）
            VStack(alignment: .leading, spacing: 3) {
                TextField("輸入助記詞", text: $fullText)
                    .font(.system(size: 10, design: .monospaced))
                    .textFieldStyle(.plain)
                    .padding(6)
                    .background(Color.white.opacity(0.05))
                    .cornerRadius(6)
                    .overlay(
                        RoundedRectangle(cornerRadius: 6)
                            .stroke(Color.white.opacity(0.2), lineWidth: 1)
                    )
                    .focused($isTextFieldFocused)
                    .accessibilityIdentifier("ImportMnemonicInput")

                
                Text("請輸入 \(wordCount) 個助記詞，用空格分隔")
                    .font(.system(size: 9))
                    .foregroundColor(.secondary)
                    .padding(.horizontal, 4)
                
                // 顯示已輸入的助記詞數量
                HStack {
                    Text("已輸入：\(words.filter { !$0.isEmpty }.count) / \(wordCount)")
                        .font(.system(size: 9))
                        .foregroundColor(.secondary)
                    Spacer()
                }
                .padding(.horizontal, 4)
            }
            .padding(.horizontal, 12)
        }
        .onChange(of: fullText) { oldValue, newValue in
            // 解析輸入的文本為單詞數組
            let inputWords = newValue.split(separator: " ").map { String($0).lowercased() }
            
            // 更新單詞數組
            for index in 0..<wordCount {
                if index < inputWords.count {
                    words[index] = inputWords[index]
                } else {
                    words[index] = ""
                }
            }
        }
        .onAppear {
            // 初始化文本
            fullText = words.prefix(wordCount).joined(separator: " ")
            isTextFieldFocused = true
        }
    }
}

// 助記詞數量選擇器
struct WordCountPicker: View {
    @Binding var selectedCount: Int
    let onSelect: (Int) -> Void
    let wordCounts = [12, 15, 18, 21, 24]
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        List(wordCounts, id: \.self) { count in
                Button(action: { onSelect(count) }) {
                    HStack {
                        Text("\(count) 個詞")
                            .font(.system(size: 14))
                        Spacer()
                        if count == selectedCount {
                            Image(systemName: "checkmark")
                                .foregroundColor(.blue)
                                .font(.system(size: 12))
                        }
                    }
                }
                .buttonStyle(.plain)
        }
        .navigationTitle("選擇助記詞數量")
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("取消") { dismiss() }
            }
        }
    }
}

#Preview {
    ImportWalletView { name, mnemonic in
        print("Importing wallet: \(name)")
    }
}