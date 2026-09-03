//
//  ToastView.swift
//  WatchWallet Watch App
//
//  Unified Toast handler for watchOS
//

import SwiftUI

struct ToastView: View {
    let message: String
    let type: ToastType
    
    enum ToastType {
        case success
        case error
        case info
        
        var color: Color {
            switch self {
            case .success: return .green
            case .error: return .red
            case .info: return .blue
            }
        }
        
        var icon: String {
            switch self {
            case .success: return "checkmark.circle.fill"
            case .error: return "exclamationmark.circle.fill"
            case .info: return "info.circle.fill"
            }
        }
    }
    
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: type.icon)
                .foregroundColor(type.color)
            
            Text(message)
                .font(.caption2)
                .foregroundStyle(.white)
                .multilineTextAlignment(.leading)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(
            Capsule()
                .fill(Color.gray.opacity(0.2))
                .background(.ultraThinMaterial)
                .shadow(radius: 4)
        )
        .clipShape(Capsule())
    }
}

struct ToastModifier: ViewModifier {
    @Binding var isPresented: Bool
    let message: String
    let type: ToastView.ToastType
    let duration: TimeInterval
    
    func body(content: Content) -> some View {
        ZStack {
            content
            
            if isPresented {
                VStack {
                    Spacer()
                    ToastView(message: message, type: type)
                        .padding(.bottom, 20)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                        .onAppear {
                            DispatchQueue.main.asyncAfter(deadline: .now() + duration) {
                                withAnimation {
                                    isPresented = false
                                }
                            }
                        }
                }
                .zIndex(1)
            }
        }
    }
}

extension View {
    func toast(isPresented: Binding<Bool>, message: String, type: ToastView.ToastType = .info, duration: TimeInterval = 2.0) -> some View {
        self.modifier(ToastModifier(isPresented: isPresented, message: message, type: type, duration: duration))
    }
}
