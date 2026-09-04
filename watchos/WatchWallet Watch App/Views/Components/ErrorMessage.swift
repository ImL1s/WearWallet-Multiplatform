//
//  ErrorMessage.swift
//  WatchWallet Watch App
//
//  Error message display component
//

import SwiftUI

struct ErrorMessage: View {
    let message: String
    
    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 12))
                .foregroundColor(.orange)
            
            Text(message)
                .font(.system(size: 12))
                .foregroundColor(.white)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.orange.opacity(0.2))
        .cornerRadius(8)
    }
}

#Preview {
    VStack(spacing: 10) {
        ErrorMessage(message: "無法連接到網路")
        ErrorMessage(message: "交易失敗：餘額不足")
        ErrorMessage(message: "這是一個很長的錯誤訊息，可能會換行顯示在小螢幕上")
    }
    .padding()
}