//
//  ReceiveView.swift
//  WatchWallet Watch App
//
//  View for receiving cryptocurrency with QR code display
//

import SwiftUI

struct ReceiveView: View {
    @StateObject private var viewModel = ReceiveViewModel()
    @State private var showingShareSheet = false
    @State private var selectedAmount = ""
    @State private var showAmountInput = false
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                // Wallet info
                VStack(spacing: 6) {
                    HStack {
                        Image(systemName: "wallet.pass")
                            .font(.system(size: 16))
                            .foregroundColor(.blue)
                        Text(viewModel.walletName)
                            .font(.system(size: 14, weight: .medium))
                    }
                    
                    Text(viewModel.selectedToken?.symbol ?? "ETH")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                }
                .padding(.bottom, 8)
                
                // QR Code
                EnhancedQRCodeView(
                    data: showAmountInput && !selectedAmount.isEmpty ? 
                          viewModel.generatePaymentRequest(amount: selectedAmount) : 
                          viewModel.walletAddress,
                    size: 120
                )
                .background(Color.white)
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color.white.opacity(0.2), lineWidth: 1)
                )
                .accessibilityIdentifier("ReceiveQRCode")
                
                // Address display
                VStack(spacing: 6) {
                    Text("錢包地址")
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                    
                    Text(formatAddress(viewModel.walletAddress))
                        .font(.system(size: 12, design: .monospaced))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                        .accessibilityIdentifier("ReceiveAddressText")
                    
                    Button(action: copyAddress) {
                        HStack(spacing: 4) {
                            Image(systemName: "doc.on.doc")
                                .font(.system(size: 12))
                            Text("複製地址")
                                .font(.system(size: 12))
                        }
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                    .accessibilityIdentifier("CopyAddressButton")
                }
                
                // Amount section
                if showAmountInput {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("請求金額 (可選)")
                            .font(.system(size: 11))
                            .foregroundColor(.secondary)
                        
                        HStack {
                            TextField("0.0", text: $selectedAmount)
                                .font(.system(size: 14))
                                .textFieldStyle(.plain)
                            
                            Text(viewModel.selectedToken?.symbol ?? "ETH")
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                        }
                        .padding(8)
                        .background(Color.white.opacity(0.1))
                        .cornerRadius(6)
                    }
                    .padding(.top, 8)
                } else {
                    Button(action: { showAmountInput = true }) {
                        HStack {
                            Image(systemName: "plus.circle")
                                .font(.system(size: 12))
                            Text("添加金額")
                                .font(.system(size: 12))
                        }
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                    .padding(.top, 8)
                }
                
                // Share button
                Button(action: { showingShareSheet = true }) {
                    HStack {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 14))
                        Text("分享")
                            .font(.system(size: 14))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.small)
                .padding(.top, 8)
            }
            .padding()
        }
        .navigationTitle("收款")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("完成") { dismiss() }
            }
        }
        .sheet(isPresented: $showingShareSheet) {
            ShareOptionsView(text: generateShareText()) {
                showingShareSheet = false
            }
        }
    }
    
    private func formatAddress(_ address: String) -> String {
        guard address.count > 10 else { return address }
        let chunks = stride(from: 0, to: address.count, by: 8).map {
            let start = address.index(address.startIndex, offsetBy: $0)
            let end = address.index(start, offsetBy: min(8, address.count - $0))
            return String(address[start..<end])
        }
        return chunks.joined(separator: "\n")
    }
    
    // QR code generation is now handled by QRCodeGenerator utility
    
    private func copyAddress() {
        // UIPasteboard is not available on watchOS
        // In production, use WatchConnectivity to send to iPhone for copying
        // For now, we'll just show visual feedback in the UI
    }
    
    private func generateShareText() -> String {
        var text = "我的 \(viewModel.selectedToken?.symbol ?? "ETH") 錢包地址：\n\(viewModel.walletAddress)"
        if !selectedAmount.isEmpty {
            text += "\n請求金額：\(selectedAmount) \(viewModel.selectedToken?.symbol ?? "ETH")"
        }
        return text
    }
}

// Share functionality is limited on watchOS
// In production, use WatchConnectivity to send data to iPhone for sharing
struct ShareOptionsView: View {
    let text: String
    let onDismiss: () -> Void
    @State private var showCopied = false
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Image(systemName: "square.and.arrow.up")
                    .font(.system(size: 30))
                    .foregroundColor(.blue)
                
                Text("分享選項")
                    .font(.system(size: 16, weight: .medium))
                
                Text("在 watchOS 上，分享功能有限。請使用配對的 iPhone 進行完整分享。")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
                
                if showCopied {
                    Text("已準備發送到 iPhone")
                        .font(.system(size: 11))
                        .foregroundColor(.green)
                }
                
                Button(action: {
                    // In production, use WatchConnectivity to send to iPhone
                    showCopied = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                        onDismiss()
                    }
                }) {
                    HStack {
                        Image(systemName: "iphone")
                            .font(.system(size: 14))
                        Text("發送到 iPhone")
                            .font(.system(size: 14))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()
            .navigationTitle("分享")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { onDismiss() }
                }
            }
        }
    }
}

#Preview {
    ReceiveView()
}