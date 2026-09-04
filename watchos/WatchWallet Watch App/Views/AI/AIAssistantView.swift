//
//  AIAssistantView.swift
//  WatchWallet Watch App
//
//  AI 助手界面 - WearWallet 智能錢包助手
//  集成 Gemini 2.0/2.5 API，提供自然語言錢包操作
//

import SwiftUI
import coreKmp

struct AIAssistantView: View {
    @StateObject private var viewModel = AIAssistantViewModel()
    @Environment(\.dismiss) private var dismiss
    @State private var userInput = ""
    @State private var isListening = false

    var body: some View {
        ScrollView {
            VStack(spacing: 8) {
                // 標題區域
                headerSection

                // 使用統計
                usageStatsSection

                // 對話歷史
                conversationSection

                // 輸入區域
                inputSection

                // 快捷指令
                quickCommandsSection

                Spacer()
                    .frame(height: 20)
            }
            .padding(.horizontal, 8)
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            viewModel.loadUsageStats()
        }
        // 導航綁定 - 交易歷史
        .navigationDestination(isPresented: $viewModel.navigateToTransactionHistory) {
            TransactionHistoryView()
        }
        // 導航綁定 - 投資組合
        .navigationDestination(isPresented: $viewModel.navigateToPortfolio) {
            WalletMainView()
        }
        // QR 碼顯示 - 導航到收款頁面
        .navigationDestination(isPresented: $viewModel.showQRCode) {
            ReceiveView()
        }
    }
    
    // MARK: - 組件
    
    private var headerSection: some View {
        VStack(spacing: 4) {
            HStack {
                Image(systemName: "brain.head.profile")
                    .foregroundColor(.blue)
                    .font(.title2)
                
                Text("AI 助手")
                    .font(.headline)
                    .fontWeight(.bold)
            }
            
            Text("智能錢包操作助手")
                .font(.caption2)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(.top, 4)
    }
    
    private var usageStatsSection: some View {
        VStack(spacing: 6) {
            HStack {
                Image(systemName: "chart.bar.fill")
                    .foregroundColor(.green)
                    .font(.caption)
                
                Text("今日使用")
                    .font(.caption)
                    .fontWeight(.medium)
                
                Spacer()
                
                Text("\(viewModel.usageStats.todayRequests)/\(viewModel.usageStats.remainingRequests + viewModel.usageStats.todayRequests)")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            ProgressView(value: Double(viewModel.usageStats.todayRequests), 
                        total: Double(viewModel.usageStats.remainingRequests + viewModel.usageStats.todayRequests))
                .progressViewStyle(LinearProgressViewStyle(tint: .blue))
                .scaleEffect(y: 0.5)
            
            if viewModel.usageStats.remainingRequests <= 3 {
                Text("剩餘 \(viewModel.usageStats.remainingRequests) 次 AI 查詢")
                    .font(.caption2)
                    .foregroundColor(.orange)
            }
        }
        .padding(8)
        .background(Color.white.opacity(0.05))
        .cornerRadius(6)
    }
    
    private var conversationSection: some View {
        VStack(alignment: .leading, spacing: 4) {
            if !viewModel.conversationHistory.isEmpty {
                HStack {
                    Text("對話記錄")
                        .font(.caption)
                        .fontWeight(.medium)
                    
                    Spacer()
                    
                    Button("清除") {
                        viewModel.clearHistory()
                    }
                    .font(.caption2)
                    .foregroundColor(.secondary)
                }
                .padding(.horizontal, 4)
                
                ForEach(viewModel.conversationHistory, id: \.id) { message in
                    ConversationBubble(message: message)
                }
            } else {
                VStack(spacing: 4) {
                    Image(systemName: "bubble.left.and.bubble.right")
                        .foregroundColor(.secondary)
                        .font(.title3)
                    
                    Text("開始對話")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    
                    Text("嘗試說：「查看餘額」")
                        .font(.caption2)
                        .foregroundColor(.secondary.opacity(0.8))
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
            }
        }
    }
    
    private var inputSection: some View {
        VStack(spacing: 6) {
            // 語音輸入按鈕
            Button(action: {
                if isListening {
                    stopListening()
                } else {
                    startListening()
                }
            }) {
                HStack {
                    Image(systemName: isListening ? "mic.fill" : "mic")
                        .foregroundColor(isListening ? .red : .white)
                    
                    Text(isListening ? "聆聽中..." : "語音輸入")
                        .font(.caption)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.roundedRectangle(radius: 8))
            .disabled(viewModel.isProcessing)
            
            // 文字輸入 (watchOS 受限，主要依賴語音)
            Button(action: {
                showTextInput()
            }) {
                HStack {
                    Image(systemName: "keyboard")
                    Text("文字輸入")
                        .font(.caption)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 6)
            }
            .buttonStyle(.bordered)
            .buttonBorderShape(.roundedRectangle(radius: 6))
            .disabled(viewModel.isProcessing)
            
            if viewModel.isProcessing {
                HStack {
                    ProgressView()
                        .scaleEffect(0.8)
                    
                    Text("AI 處理中...")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
                .padding(.top, 4)
            }
        }
    }
    
    private var quickCommandsSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("快捷指令")
                .font(.caption)
                .fontWeight(.medium)
                .padding(.horizontal, 4)
            
            LazyVGrid(columns: [
                GridItem(.flexible()),
                GridItem(.flexible())
            ], spacing: 6) {
                QuickCommandButton(title: "查看餘額", icon: "dollarsign.circle") {
                    processCommand("查看餘額")
                }
                
                QuickCommandButton(title: "交易記錄", icon: "list.bullet") {
                    processCommand("顯示交易歷史")
                }
                
                QuickCommandButton(title: "投資組合", icon: "chart.pie") {
                    processCommand("顯示投資組合")
                }
                
                QuickCommandButton(title: "生成QR碼", icon: "qrcode") {
                    processCommand("生成收款QR碼")
                }
            }
        }
    }
    
    // MARK: - 方法
    
    private func startListening() {
        guard !viewModel.isProcessing else { return }
        
        isListening = true
        
        // 使用 WatchKit 語音輸入
        guard let controller = WKApplication.shared().visibleInterfaceController else {
            isListening = false
            return
        }
        
        controller.presentTextInputController(
            withSuggestions: [
                "查看餘額",
                "顯示交易歷史", 
                "發送代幣",
                "生成QR碼"
            ],
            allowedInputMode: .plain
        ) { result in
            DispatchQueue.main.async {
                self.isListening = false
                
                if let textResult = result as? [String], let command = textResult.first {
                    self.processCommand(command)
                } else if let stringResult = result as? String {
                    self.processCommand(stringResult)
                }
            }
        }
    }
    
    private func stopListening() {
        isListening = false
    }
    
    private func showTextInput() {
        guard let controller = WKApplication.shared().visibleInterfaceController else { return }
        
        controller.presentTextInputController(
            withSuggestions: [
                "查看ETH餘額",
                "顯示最近交易",
                "發送0.1 ETH",
                "生成收款地址"
            ],
            allowedInputMode: .plain
        ) { result in
            DispatchQueue.main.async {
                if let textResult = result as? [String], let command = textResult.first {
                    self.processCommand(command)
                } else if let stringResult = result as? String {
                    self.processCommand(stringResult)
                }
            }
        }
    }
    
    private func processCommand(_ command: String) {
        Task {
            await viewModel.processCommand(command)
        }
    }
}

// MARK: - 對話氣泡組件

struct ConversationBubble: View {
    let message: ConversationMessage
    
    var body: some View {
        HStack {
            if message.isUser {
                Spacer()
                
                VStack(alignment: .trailing, spacing: 2) {
                    Text(message.content)
                        .font(.caption)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.blue.opacity(0.8))
                        .foregroundColor(.white)
                        .cornerRadius(8)
                    
                    Text(message.timestamp, format: .dateTime.hour().minute())
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            } else {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 4) {
                        Image(systemName: message.source.icon)
                            .font(.caption2)
                            .foregroundColor(message.source.color)
                        
                        Text(message.content)
                            .font(.caption)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.white.opacity(0.1))
                            .cornerRadius(8)
                    }
                    
                    HStack {
                        Text(message.source.displayName)
                            .font(.caption2)
                            .foregroundColor(.secondary)
                        
                        Text(message.timestamp, format: .dateTime.hour().minute())
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                }
                
                Spacer()
            }
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 2)
    }
}

// MARK: - 快捷指令按鈕

struct QuickCommandButton: View {
    let title: String
    let icon: String
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.caption)
                
                Text(title)
                    .font(.caption2)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
        }
        .buttonStyle(.bordered)
        .buttonBorderShape(.roundedRectangle(radius: 6))
    }
}

// MARK: - 預覽

#Preview {
    NavigationView {
        AIAssistantView()
    }
}