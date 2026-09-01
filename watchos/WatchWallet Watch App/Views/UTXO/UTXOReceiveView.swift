//
//  UTXOReceiveView.swift
//  WatchWallet Watch App
//
//  UTXO 鏈接收地址視圖
//

import SwiftUI
// CoreImage not available on watchOS - will use placeholder for QR code
import coreKmp

struct UTXOReceiveView: View {
    let chain: UTXOChainType
    @StateObject private var utxoService = UTXOService.shared // Changed from walletRepository
    @Environment(\.dismiss) private var dismiss
    
    @State private var walletAddress = ""
    // Removed @State private var selectedAddressType
    @State private var qrCodeImage: UIImage?
    @State private var isCopied = false
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var showToast = false
    @State private var toastMessage = ""
    @State private var toastType: ToastView.ToastType = .info
    
    var body: some View {
        ZStack {
            NavigationStack {
                ScrollView {
                    VStack(spacing: 16) {
                        // 鏈資訊
                        HStack {
                            Image(systemName: chain.icon)
                                .foregroundColor(chain.color)
                            Text(chain.displayName)
                                .font(.caption)
                            Spacer()
                        }
                        .padding(.horizontal)
                        
                        // QR Code
                        if let qrCodeImage = qrCodeImage {
                            Image(uiImage: qrCodeImage)
                                .interpolation(.none)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 120, height: 120)
                                .background(Color.white)
                                .cornerRadius(10)
                                .padding()
                        } else {
                            // Loading address placeholder
                            Rectangle()
                                .fill(Color.gray.opacity(0.1))
                                .frame(width: 120, height: 120)
                                .cornerRadius(10)
                                .padding()
                                .overlay(
                                    ProgressView()
                                        .scaleEffect(0.8)
                                )
                        }
                        
                        // 地址顯示
                        VStack(spacing: 8) {
                            Text("錢包地址")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                            
                            Text(walletAddress.isEmpty ? "正在載入..." : walletAddress)
                                .font(.system(size: 10, design: .monospaced))
                                .foregroundColor(.primary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal)
                                .fixedSize(horizontal: false, vertical: true)
                            
                            // 複製按鈕 (發送到 iPhone 剪貼簿)
                            Button(action: copyAddress) {
                                HStack {
                                    Image(systemName: isCopied ? "checkmark" : "iphone.gen3")
                                        .font(.caption)
                                    Text(isCopied ? "已發送" : "複製到 iPhone")
                                        .font(.caption)
                                }
                                .foregroundColor(isCopied ? .green : .blue)
                            }
                            .buttonStyle(.bordered)
                            .controlSize(.small)
                            .disabled(walletAddress.isEmpty)
                        }
                        
                        // 提示資訊
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Image(systemName: "info.circle")
                                    .font(.caption2)
                                Text("注意事項")
                                    .font(.caption2)
                                    .fontWeight(.medium)
                            }
                            .foregroundColor(.orange)
                            
                            Text("• 請確認發送方支援 \(chain.displayName)")
                                .font(.system(size: 9))
                            Text("• 發送到錯誤的鏈將導致資產永久丟失")
                                .font(.system(size: 9))
                            Text("• 最低接收金額: \(formatMinDust()) \(chain.symbol)")
                                .font(.system(size: 9))
                        }
                        .padding()
                        .background(Color.orange.opacity(0.1))
                        .cornerRadius(8)
                        .padding(.horizontal)
                    }
                    .padding(.vertical)
                }
                .navigationTitle("接收 \(chain.symbol)")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("完成") {
                            dismiss()
                        }
                    }
                }
            }
            
            // 全域載入動畫
            if isLoading {
                LoadingView(message: "正在獲取地址...")
            }
        }
        .toast(isPresented: $showToast, message: toastMessage, type: toastType)
        .onAppear {
            loadAddress()
        }
    }
    
    private func loadAddress() {
        isLoading = true
        Task {
            // 獲取當前錢包地址
            if let address = await utxoService.getAddress(for: chain) {
                await MainActor.run {
                    self.walletAddress = address
                    self.isLoading = false
                    generateQRCode()
                }
            } else {
                await MainActor.run {
                    self.isLoading = false
                    self.toastMessage = "無法獲取地址"
                    self.toastType = .error
                    self.showToast = true
                }
            }
        }
    }
    
    private func generateQRCode() {
        // Use SimpleQRCodeGenerator for watchOS compatibility
        self.qrCodeImage = SimpleQRCodeGenerator.generateQRCode(
            from: walletAddress,
            size: CGSize(width: 200, height: 200)
        )
    }
    
    private func copyAddress() {
        // 發送地址到 iPhone 剪貼簿
        WatchConnectivityManager.shared.copyToClipboard(walletAddress)
        isCopied = true
        
        // 顯示成功 Toast
        self.toastMessage = "已將地址發送到 iPhone 剪貼簿"
        self.toastType = .success
        self.showToast = true
        
        // 重置複製狀態
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            isCopied = false
        }
    }
    
    private func formatMinDust() -> String {
        let minDust = Double(chain.minDustAmount) / 100_000_000
        return String(format: "%.8f", minDust)
    }
}

#Preview {
    UTXOReceiveView(chain: .bitcoin)
}