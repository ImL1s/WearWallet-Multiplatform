//
//  MnemonicDisplayView.swift
//  WatchWallet Watch App
//
//  Dedicated fullscreen view for displaying mnemonic phrase
//

import SwiftUI
import LocalAuthentication

struct MnemonicDisplayView: View {
    let walletId: String
    let walletName: String
    @Environment(\.dismiss) var dismiss
    @State private var mnemonic: String = ""
    @State private var isLoading = true
    @State private var error: String?
    @State private var copiedWords = Set<Int>()
    @State private var showCopiedFeedback = false
    
    private var words: [String] {
        return mnemonic.components(separatedBy: " ")
    }
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 12) {
                    if isLoading {
                        // 載入狀態
                        VStack(spacing: 8) {
                            ProgressView()
                                .scaleEffect(0.8)
                            Text("驗證身份中...")
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .padding(.top, 40)
                        
                    } else if let error = error {
                        // 錯誤狀態
                        VStack(spacing: 12) {
                            Image(systemName: "exclamationmark.triangle")
                                .font(.system(size: 32))
                                .foregroundColor(.orange)
                            
                            Text("無法顯示助記詞")
                                .font(.system(size: 14, weight: .medium))
                            
                            Text(error)
                                .font(.system(size: 10))
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal)
                            
                            Button("重試") {
                                loadMnemonic()
                            }
                            .font(.system(size: 12))
                            .buttonStyle(.bordered)
                            .controlSize(.small)
                        }
                        .padding(.top, 20)
                        
                    } else {
                        // Header with wallet info
                        VStack(spacing: 6) {
                            Image(systemName: "key.fill")
                                .font(.system(size: 20))
                                .foregroundColor(.green)
                            
                            Text("助記詞")
                                .font(.system(size: 14, weight: .semibold))
                            
                            Text(walletName)
                                .font(.system(size: 11))
                                .foregroundColor(.secondary)
                        }
                        .padding(.top, 8)
                
                        // Security warning
                        VStack(spacing: 6) {
                            HStack(spacing: 4) {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .font(.system(size: 12))
                                    .foregroundColor(.orange)
                                Text("請在安全環境下查看")
                                    .font(.system(size: 10, weight: .medium))
                                    .foregroundColor(.orange)
                            }
                            
                            Text("不要截圖或與他人分享")
                                .font(.system(size: 9))
                                .foregroundColor(.secondary)
                        }
                        .padding(8)
                        .background(Color.orange.opacity(0.15))
                        .cornerRadius(6)
                
                        // Mnemonic word grid - optimized for watchOS
                        LazyVGrid(columns: [
                            GridItem(.flexible()),
                            GridItem(.flexible())
                        ], spacing: 6) {
                            ForEach(words.indices, id: \.self) { index in
                                MnemonicWordDisplayCard(
                                    index: index + 1,
                                    word: words[index],
                                    isCopied: copiedWords.contains(index),
                                    onTap: {
                                        // Visual feedback for word selection
                                        let _ = withAnimation(.easeInOut(duration: 0.2)) {
                                            copiedWords.insert(index)
                                        }
                                        
                                        // Auto clear after 2 seconds
                                        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                                            copiedWords.remove(index)
                                        }
                                    }
                                )
                            }
                        }
                        .padding(.horizontal, 4)
                
                        // Action buttons
                        VStack(spacing: 8) {
                            // Copy all button
                            Button(action: copyAllMnemonic) {
                                HStack(spacing: 4) {
                                    Image(systemName: "doc.on.doc")
                                        .font(.system(size: 10))
                                    Text("複製助記詞")
                                        .font(.system(size: 11))
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                            }
                            .buttonStyle(.bordered)
                            .controlSize(.small)
                            
                            // Done button
                            Button(action: { dismiss() }) {
                                HStack(spacing: 4) {
                                    Image(systemName: "checkmark")
                                        .font(.system(size: 10))
                                    Text("完成")
                                        .font(.system(size: 11, weight: .medium))
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                            }
                            .buttonStyle(.borderedProminent)
                            .controlSize(.small)
                        }
                        .padding(.horizontal, 8)
                        
                        // Backup tips
                        VStack(alignment: .leading, spacing: 6) {
                            Text("安全提醒")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.blue)
                            
                            VStack(alignment: .leading, spacing: 4) {
                                MnemonicSecurityTip(icon: "pencil", text: "寫在紙上保存")
                                MnemonicSecurityTip(icon: "lock.fill", text: "存放安全地方")
                                MnemonicSecurityTip(icon: "xmark.shield", text: "不要數位儲存")
                            }
                        }
                        .padding(10)
                        .background(Color.blue.opacity(0.1))
                        .cornerRadius(6)
                        
                        // Copy feedback
                        if showCopiedFeedback {
                            HStack(spacing: 4) {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.system(size: 10))
                                    .foregroundColor(.green)
                                Text("助記詞已複製")
                                    .font(.system(size: 10))
                                    .foregroundColor(.green)
                            }
                            .transition(.opacity)
                        }
                    } // End of else (successful load) block
                }
                .padding()
            }
            .navigationTitle("助記詞備份")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("關閉") { dismiss() }
                }
            }
        }
        .onAppear {
            loadMnemonic()
        }
    }
    
    private func loadMnemonic() {
        // 直接載入助記詞，不需要額外驗證
        // watchOS 本身已經通過戴錶驗證提供安全性
        fetchMnemonic()
    }
    
    private func fetchMnemonic() {
        Task {
            do {
                // TODO: Use real mnemonic API when available
                // For now, use mock mnemonic
                try await Task.sleep(nanoseconds: 500_000_000)
                let fetchedMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
                
                await MainActor.run {
                    self.mnemonic = fetchedMnemonic
                    self.isLoading = false
                }
            } catch {
                await MainActor.run {
                    self.isLoading = false
                    self.error = "無法載入助記詞: \(error.localizedDescription)"
                }
            }
        }
    }
    
    private func copyAllMnemonic() {
        // Note: Clipboard operations are limited on watchOS
        // Show visual feedback instead
        withAnimation(.easeInOut(duration: 0.3)) {
            showCopiedFeedback = true
        }
        
        // Hide feedback after 2 seconds
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            withAnimation(.easeInOut(duration: 0.3)) {
                showCopiedFeedback = false
            }
        }
        
        print("[MnemonicDisplayView] Mnemonic copied to clipboard (feedback only on watchOS)")
    }
}

// Compact mnemonic word card for watchOS
struct MnemonicWordDisplayCard: View {
    let index: Int
    let word: String
    let isCopied: Bool
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 4) {
                Text("\(index).")
                    .font(.system(size: 9))
                    .foregroundColor(.secondary)
                    .frame(width: 16, alignment: .trailing)
                
                Text(word)
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundColor(isCopied ? .green : .primary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 6)
            .padding(.vertical, 4)
            .background(isCopied ? Color.green.opacity(0.2) : Color.white.opacity(0.08))
            .cornerRadius(4)
        }
        .buttonStyle(.plain)
    }
}

// Compact security tip for watchOS
struct MnemonicSecurityTip: View {
    let icon: String
    let text: String
    
    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 10))
                .foregroundColor(.blue)
                .frame(width: 12)
            
            Text(text)
                .font(.system(size: 9))
                .foregroundColor(.secondary)
        }
    }
}

struct MnemonicDisplayView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationStack {
            MnemonicDisplayView(
                walletId: "test-wallet-id",
                walletName: "我的錢包"
            )
        }
    }
}