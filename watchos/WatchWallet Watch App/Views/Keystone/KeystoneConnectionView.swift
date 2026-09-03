//
//  KeystoneConnectionView.swift
//  WatchWallet Watch App
//
//  Keystone 3 Pro 硬體錢包連接界面
//  對應 WearOS 的 ConnectKeystoneWalletScreen
//

import SwiftUI
import coreKmp

struct KeystoneConnectionView: View {
    @StateObject private var viewModel = KeystoneConnectionViewModel()
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                // 標題
                HStack {
                    Image(systemName: "hardware.desktop")
                        .foregroundColor(.blue)
                        .font(.title2)
                    
                    Text("連接 Keystone")
                        .font(.headline)
                        .fontWeight(.bold)
                }
                .padding(.top, 8)
                
                // 狀態內容
                switch viewModel.connectionState {
                case .idle:
                    IdleStateView()
                    
                case .waitingForScan:
                    WaitingForScanView(viewModel: viewModel)
                    
                case .scanning:
                    ScanningStateView()
                    
                case .success:
                    SuccessStateView {
                        dismiss()
                    }
                    
                case .error:
                    ErrorStateView(
                        message: viewModel.errorMessage ?? "未知錯誤",
                        onRetry: {
                            viewModel.retryConnection()
                        },
                        onCancel: {
                            dismiss()
                        }
                    )
                }
                
                if viewModel.isLoading {
                    ProgressView("處理中...")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            }
            .padding(.horizontal, 8)
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            viewModel.startKeystoneConnection()
        }
    }
}

// MARK: - 狀態子視圖

struct IdleStateView: View {
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "qrcode.viewfinder")
                .font(.largeTitle)
                .foregroundColor(.secondary)
            
            Text("準備連接到 Keystone 3 Pro...")
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding()
    }
}

struct WaitingForScanView: View {
    @ObservedObject var viewModel: KeystoneConnectionViewModel
    
    var body: some View {
        VStack(spacing: 12) {
            // 操作指南卡片
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Image(systemName: "iphone")
                        .foregroundColor(.blue)
                    Text("操作步驟")
                        .font(.caption)
                        .fontWeight(.semibold)
                }
                
                VStack(alignment: .leading, spacing: 4) {
                    StepText("1. 在 Keystone 3 Pro 上選擇「連接軟體錢包」")
                    StepText("2. Keystone 會顯示 QR 碼")
                    StepText("3. 點擊下方「開始掃描」按鈕")
                    StepText("4. 使用 iPhone 掃描 Keystone 的 QR 碼")
                }
            }
            .padding(12)
            .background(Color.white.opacity(0.05))
            .cornerRadius(8)
            
            // 開始掃描按鈕
            Button(action: {
                viewModel.startScanning()
                viewModel.requestiPhoneScan()
            }) {
                HStack {
                    Image(systemName: "qrcode.viewfinder")
                    Text("開始掃描")
                }
                .font(.caption)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.roundedRectangle(radius: 8))
            
            Text("將使用 iPhone 相機掃描 Keystone QR 碼")
                .font(.caption2)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
    }
}

struct StepText: View {
    let text: String
    
    init(_ text: String) {
        self.text = text
    }
    
    var body: some View {
        Text(text)
            .font(.caption2)
            .foregroundColor(.primary.opacity(0.8))
    }
}

struct ScanningStateView: View {
    var body: some View {
        VStack(spacing: 12) {
            ProgressView()
                .scaleEffect(1.2)
            
            VStack(spacing: 4) {
                Text("掃描中...")
                    .font(.caption)
                    .fontWeight(.medium)
                
                Text("請使用 iPhone 掃描 Keystone 顯示的 QR 碼")
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }
            
            // 動畫指示器
            HStack(spacing: 4) {
                ForEach(0..<3) { index in
                    Circle()
                        .fill(Color.blue)
                        .frame(width: 6, height: 6)
                        .scaleEffect(animatingDots[index] ? 1.2 : 0.8)
                        .animation(
                            Animation.easeInOut(duration: 0.6)
                                .repeatForever()
                                .delay(Double(index) * 0.2),
                            value: animatingDots[index]
                        )
                }
            }
            .onAppear {
                for i in 0..<3 {
                    animatingDots[i] = true
                }
            }
        }
        .padding()
    }
    
    @State private var animatingDots = [false, false, false]
}

struct SuccessStateView: View {
    let onDismiss: () -> Void
    
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 40))
                .foregroundColor(.green)
            
            VStack(spacing: 4) {
                Text("連接成功！")
                    .font(.headline)
                    .fontWeight(.semibold)
                
                Text("Keystone 3 Pro 已成功連接")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }
            
            Button("完成") {
                onDismiss()
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.roundedRectangle(radius: 8))
        }
        .padding()
    }
}

struct ErrorStateView: View {
    let message: String
    let onRetry: () -> Void
    let onCancel: () -> Void
    
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 32))
                .foregroundColor(.orange)
            
            VStack(spacing: 4) {
                Text("連接失敗")
                    .font(.headline)
                    .fontWeight(.semibold)
                
                Text(message)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }
            
            HStack(spacing: 8) {
                Button("取消") {
                    onCancel()
                }
                .buttonStyle(.bordered)
                .buttonBorderShape(.roundedRectangle(radius: 6))
                
                Button("重試") {
                    onRetry()
                }
                .buttonStyle(.borderedProminent)
                .buttonBorderShape(.roundedRectangle(radius: 6))
            }
        }
        .padding()
    }
}

// MARK: - Preview

#Preview {
    NavigationView {
        KeystoneConnectionView()
    }
}