//
//  TransactionButtons.swift
//  WatchWallet Watch App
//
//  Send and Receive transaction buttons
//
//

import SwiftUI

struct TransactionButtons: View {
    let onSendClick: () -> Void
    let onReceiveClick: () -> Void
    let onSwapClick: () -> Void
    let enabled: Bool
    
    var body: some View {
        HStack(spacing: 10) {
            // 發送按鈕
            Button(action: onSendClick) {
                VStack(spacing: 4) {
                    Image(systemName: "arrow.up.circle.fill")
                        .font(.system(size: 24))
                        .foregroundColor(enabled ? .blue : .gray)
                    Text("發送")
                        .font(.system(size: 12))
                        .foregroundColor(enabled ? .primary : .gray)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(enabled ? Color.blue.opacity(0.2) : Color.gray.opacity(0.1))
                .cornerRadius(10)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("Send")
            .disabled(!enabled)
            
            // 接收按鈕
            Button(action: onReceiveClick) {
                VStack(spacing: 4) {
                    Image(systemName: "arrow.down.circle.fill")
                        .font(.system(size: 24))
                        .foregroundColor(enabled ? .green : .gray)
                    Text("接收")
                        .font(.system(size: 12))
                        .foregroundColor(enabled ? .primary : .gray)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(enabled ? Color.green.opacity(0.2) : Color.gray.opacity(0.1))
                .cornerRadius(10)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("Receive")
            .disabled(!enabled)
            
            // Swap 按鈕
            Button(action: onSwapClick) {
                VStack(spacing: 4) {
                    Image(systemName: "arrow.triangle.2.circlepath.circle.fill")
                        .font(.system(size: 24))
                        .foregroundColor(enabled ? .orange : .gray)
                    Text("Swap")
                        .font(.system(size: 12))
                        .foregroundColor(enabled ? .primary : .gray)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(enabled ? Color.orange.opacity(0.2) : Color.gray.opacity(0.1))
                .cornerRadius(10)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("Swap")
            .disabled(!enabled)
        }
    }
}

#Preview {
    VStack(spacing: 20) {
        TransactionButtons(
            onSendClick: {},
            onReceiveClick: {},
            onSwapClick: {},
            enabled: true
        )
        
        TransactionButtons(
            onSendClick: {},
            onReceiveClick: {},
            onSwapClick: {},
            enabled: false
        )
    }
    .padding()
}