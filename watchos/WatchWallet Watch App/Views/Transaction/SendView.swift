//
//  SendView.swift
//  WatchWallet Watch App
//
//  View for sending cryptocurrency transactions
//

import SwiftUI
import coreKmp
import WatchKit

struct SendView: View {
    @StateObject private var viewModel = SendViewModel()
    @State private var amount = ""
    @State private var showingQRScanner = false
    @State private var showingConfirmation = false
    @State private var useKeystoneSignature = false
    @State private var showError = false
    @FocusState private var amountFieldFocused: Bool
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        ZStack {
            ScrollView {
                VStack(spacing: 12) {
                    tokenDisplaySection
                    addressInputSection
                    amountInputSection
                    
                    // 密碼輸入
                    VStack(alignment: .leading, spacing: 4) {
                        Text("錢包密碼")
                            .font(.system(size: 11))
                            .foregroundColor(.secondary)
                        
                        SecureField("輸入密碼", text: $viewModel.password)
                            .font(.system(size: 14))
                            .textFieldStyle(.plain)
                            .padding(.vertical, 8)
                            .padding(.horizontal, 10)
                            .background(Color.white.opacity(0.1))
                            .cornerRadius(8)
                    }
                    
                    sendButton
                    
                    // 添加底部空間，確保最後一個元素可以完全顯示
                    Spacer()
                        .frame(height: 20)
                }
                .padding()
            }
            .navigationTitle("發送資產")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
            }
            
            // 全域載入動畫
            if viewModel.isLoading {
                let loadingMsg: String = {
                    switch viewModel.transactionState {
                    case .sending: return "正在發送交易..."
                    case .keystoneSigning: return "正在請求 Keystone 簽名..."
                    default: return "正在處理..."
                    }
                }()
                LoadingView(message: loadingMsg)
            }
        }
        .toast(isPresented: $showError, message: viewModel.error ?? "", type: .error)
        .onChange(of: viewModel.error) { oldValue, newValue in
            if newValue != nil {
                showError = true
            }
        }
        .sheet(isPresented: $showingQRScanner) {
            QRScannerView { scannedValue in
                viewModel.recipientAddress = scannedValue
                showingQRScanner = false
            }
        }
        .sheet(isPresented: $showingConfirmation) {
            confirmationSheet
        }
        .onTapGesture {
            amountFieldFocused = false
        }
        .onReceive(NotificationCenter.default.publisher(for: .qrCodeScanned)) { notification in
            if let address = notification.userInfo?["address"] as? String {
                viewModel.recipientAddress = address
            }
        }
    }
    
    // MARK: - View Components
    
    private var tokenDisplaySection: some View {
        HStack {
            if let token = viewModel.selectedToken {
                Image(systemName: "circle.fill")
                    .foregroundColor(.blue)
                    .font(.system(size: 14))
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(token.name)
                        .font(.system(size: 14, weight: .bold))
                    Text("餘額: \(token.balance ?? "0.00") \(token.symbol)")
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                }
            } else {
                Text("未選擇代幣")
                    .font(.system(size: 14))
                    .foregroundColor(.secondary)
            }
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white.opacity(0.1))
        .cornerRadius(10)
    }
    
    private var addressInputSection: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text("收款地址")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
                Spacer()
                Button(action: { qrScanAction() }) {
                    Image(systemName: "qrcode.viewfinder")
                        .font(.system(size: 14))
                }
                .buttonStyle(.plain)
            }

            Button(action: { showTextInputController() }) {
                Text(viewModel.recipientAddress.isEmpty ? "點擊輸入或掃描" : formatDisplayAddress(viewModel.recipientAddress))
                    .accessibilityIdentifier("SendAddressInput")
                    .font(.system(size: 12))
                    .foregroundColor(viewModel.recipientAddress.isEmpty ? .secondary : .primary)
                    .padding(.vertical, 8)
                    .padding(.horizontal, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(addressInputBackground)
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(addressBorderColor, lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)

            // 地址驗證訊息
            if !viewModel.addressValidationMessage.isEmpty {
                Text(viewModel.addressValidationMessage)
                    .font(.system(size: 10))
                    .foregroundColor(viewModel.isAddressValid ? .green : .orange)
                    .transition(.opacity)
            }
        }
    }

    // 地址輸入框背景色
    private var addressInputBackground: Color {
        if viewModel.recipientAddress.isEmpty {
            return Color.white.opacity(0.1)
        } else if viewModel.isAddressValid {
            return Color.green.opacity(0.1)
        } else {
            return Color.orange.opacity(0.1)
        }
    }

    // 地址輸入框邊框色
    private var addressBorderColor: Color {
        if viewModel.recipientAddress.isEmpty {
            return Color.clear
        } else if viewModel.isAddressValid {
            return Color.green.opacity(0.5)
        } else {
            return Color.orange.opacity(0.5)
        }
    }
    
    private var amountInputSection: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("發送金額")
                .font(.system(size: 11))
                .foregroundColor(.secondary)
            
            HStack {
                TextField("0.0", text: $amount)
                    .font(.system(size: 16, weight: .medium))
                    .focused($amountFieldFocused)
                    .accessibilityIdentifier("SendAmountInput")
                
                Text(viewModel.selectedToken?.symbol ?? "")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.blue)
            }
            .padding(.vertical, 8)
            .padding(.horizontal, 10)
            .background(Color.white.opacity(0.1))
            .cornerRadius(8)
        }
    }
    private var sendButton: some View {
        VStack(spacing: 8) {
            // Keystone 簽名選項
            if KeystoneService.shared.isInitialized && !KeystoneService.shared.connectedWallets.isEmpty {
                Button(action: { 
                    useKeystoneSignature = true
                    showingConfirmation = true 
                }) {
                    HStack {
                        Image(systemName: "cpu")
                            .font(.system(size: 14))
                        Text("使用 Keystone 簽名")
                            .font(.system(size: 14, weight: .medium))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                }
                .buttonStyle(.borderedProminent)
                .tint(.orange)
                .disabled(!canSend || viewModel.isLoading)
            }
            
            // 一般簽名按鈕
            Button(action: { 
                useKeystoneSignature = false
                showingConfirmation = true 
            }) {
                HStack {
                    Image(systemName: "paperplane.fill")
                        .font(.system(size: 14))
                    Text("發送")
                        .font(.system(size: 14, weight: .medium))
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!canSend || viewModel.isLoading)
            .accessibilityIdentifier("SendConfirmButton")
        }
        .padding(.top, 8)
    }
    
    private var confirmationSheet: some View {
        TransactionConfirmationView(
            recipient: viewModel.recipientAddress,
            amount: amount,
            token: viewModel.selectedToken?.symbol ?? "ETH",
            estimatedUsd: viewModel.getEstimatedUsdValue(amount),
            isKeystoneSignature: useKeystoneSignature,
            onConfirm: confirmTransaction,
            onCancel: { showingConfirmation = false }
        )
    }
    
    // MARK: - Actions
    
    private func qrScanAction() {
        if viewModel.isPhoneConnected {
            viewModel.requestQRCodeScan()
        } else {
            showingQRScanner = true
        }
    }
    
    private func showTextInputController() {
        // 檢查是否已經有視圖在呈現中，避免衝突
        guard let visibleController = WKApplication.shared().visibleInterfaceController else {
            print("⚠️ 無法獲取可見的介面控制器")
            return
        }
        
        // 使用 WatchKit 的文字輸入控制器，加入錯誤處理
        visibleController.presentTextInputController(
            withSuggestions: [
                "0x",  // 以太坊地址前綴提示
                "0x1234567890abcdef1234567890abcdef12345678", // 示例地址
            ],
            allowedInputMode: .plain
        ) { result in
            DispatchQueue.main.async {
                // 處理不同類型的返回結果
                if let resultArray = result as? [String],
                   let inputText = resultArray.first,
                   !inputText.isEmpty {
                    viewModel.recipientAddress = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
                    print("✅ 地址輸入成功: \(inputText.prefix(10))...")
                } else {
                    print("⚠️ 文字輸入被取消或為空")
                }
            }
        }
    }
    
    private func formatDisplayAddress(_ address: String) -> String {
        if address.count > 20 {
            let prefix = address.prefix(10)
            let suffix = address.suffix(8)
            return "\(prefix)...\(suffix)"
        }
        return address
    }
    
    private func confirmTransaction() {
        Task {
            if useKeystoneSignature {
                // 使用 Keystone 硬體錢包簽名
                await viewModel.sendTransactionWithKeystone(
                    to: viewModel.recipientAddress,
                    amount: amount
                )
            } else {
                // 使用一般軟體簽名
                await viewModel.sendTransaction(
                    to: viewModel.recipientAddress,
                    amount: amount
                )
            }
            
            if case .success = viewModel.transactionState {
                // 成功時觸發觸覺回饋 (Haptic Feedback)
                WKInterfaceDevice.current().play(.success)
                dismiss()
            } else if case .error = viewModel.transactionState {
                // 失敗時觸發觸覺回饋
                WKInterfaceDevice.current().play(.failure)
            }
        }
        showingConfirmation = false
    }
    
    private var canSend: Bool {
        viewModel.isAddressValid && !amount.isEmpty && Double(amount) ?? 0 > 0
    }
}

// Transaction Confirmation View
struct TransactionConfirmationView: View {
    let recipient: String
    let amount: String
    let token: String
    let estimatedUsd: String
    let isKeystoneSignature: Bool
    let onConfirm: () -> Void
    let onCancel: () -> Void
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollView {
                    VStack(spacing: 8) {
                        // Compact Header
                        HStack(spacing: 8) {
                            Image(systemName: "arrow.up.circle.fill")
                                .font(.system(size: 24))
                                .foregroundColor(.orange)
                            
                            VStack(alignment: .leading) {
                                Text("發送確認")
                                    .font(.headline)
                                Text(token)
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                        }
                        .padding(.bottom, 8)
                        
                        Divider()
                        
                        // Compact Amount
                        HStack {
                            Text("金額")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                            Spacer()
                            VStack(alignment: .trailing) {
                                Text("\(amount) \(token)")
                                    .font(.system(size: 16, weight: .semibold, design: .rounded))
                                Text("≈ $\(estimatedUsd)")
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                            }
                        }
                        
                        Divider()
                        
                        // Compact Recipient
                        HStack {
                            Text("收款")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                            Spacer()
                            Text(formatAddress(recipient))
                                .font(.system(size: 13, design: .monospaced))
                        }
                        
                        Divider()
                        
                        // Signing Method
                        HStack {
                            Image(systemName: isKeystoneSignature ? "cpu" : "key")
                                .font(.caption2)
                                .foregroundColor(isKeystoneSignature ? .orange : .blue)
                            Text(isKeystoneSignature ? "Keystone" : "Software Wallet")
                                .font(.caption2)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 4)
                        
                    }
                    .padding()
                }
                
                // Pinned Bottom Action
                VStack(spacing: 8) {
                    Button(action: {
                        WKInterfaceDevice.current().play(.click)
                        onConfirm()
                    }) {
                        Text("確認發送")
                            .font(.system(size: 16, weight: .semibold))
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.green)
                    
                    Button("取消", action: {
                        WKInterfaceDevice.current().play(.click)
                        onCancel()
                    })
                    .buttonStyle(.plain)
                    .font(.caption)
                    .foregroundColor(.secondary)
                }
                .padding(.horizontal)
                .padding(.bottom, 4)
            }
            .navigationTitle("確認")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
    
    private func formatAddress(_ address: String) -> String {
        guard address.count > 10 else { return address }
        let prefix = address.prefix(6)
        let suffix = address.suffix(4)
        return "\(prefix)...\(suffix)"
    }
    
    private func getFormattedRelativeTime(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}

// QR Scanner view for watchOS
struct QRScannerView: View {
    let onScan: (String) -> Void
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // Info section
                    VStack(spacing: 8) {
                        Image(systemName: "iphone.and.arrow.forward")
                            .font(.system(size: 40))
                            .foregroundColor(.blue)
                        
                        Text("使用 iPhone 掃描")
                            .font(.system(size: 16, weight: .semibold))
                        
                        Text("由於 watchOS 限制，請使用配對的 iPhone 開啟 WearWallet 應用程式進行 QR Code 掃描")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                    }
                    .padding()
                    .background(Color.blue.opacity(0.1))
                    .cornerRadius(12)
                    
                    // Instructions
                    VStack(alignment: .leading, spacing: 12) {
                        Text("使用步驟：")
                            .font(.system(size: 13, weight: .medium))
                        
                        ForEach([
                            "1. 在 iPhone 上開啟 WearWallet",
                            "2. 點擊掃描 QR Code 按鈕",
                            "3. 掃描結果會自動同步到手錶"
                        ], id: \.self) { step in
                            HStack(alignment: .top, spacing: 8) {
                                Text(step)
                                    .font(.system(size: 11))
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                    .padding()
                    .background(Color.white.opacity(0.05))
                    .cornerRadius(8)
                    
                    // Manual input option
                    Text("或者您可以手動輸入地址")
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                        .padding(.top, 8)
                    
                    Button("返回手動輸入") {
                        dismiss()
                    }
                    .font(.system(size: 12))
                    .buttonStyle(.bordered)
                }
                .padding()
            }
            .navigationTitle("QR Code 掃描")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("關閉") { dismiss() }
                }
            }
        }
    }
}

#Preview {
    SendView()
}