//
//  LoadingView.swift
//  WatchWallet Watch App
//
//  Unified loading indicator for watchOS
//

import SwiftUI

struct LoadingView: View {
    var message: String? = nil
    
    var body: some View {
        ZStack {
            Color.black.opacity(0.4)
                .edgesIgnoringSafeArea(.all)
            
            VStack(spacing: 12) {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    .scaleEffect(1.5)
                
                if let message = message {
                    Text(message)
                        .font(.caption2)
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.center)
                }
            }
            .padding()
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.gray.opacity(0.3))
                    .background(.ultraThinMaterial)
            )
            .cornerRadius(12)
        }
    }
}

struct LoadingView_Previews: PreviewProvider {
    static var previews: some View {
        ZStack {
            Color.blue
            LoadingView(message: "Loading...")
        }
    }
}
