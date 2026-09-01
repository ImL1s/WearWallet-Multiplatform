//
//  QRCodeService.swift
//  WatchWallet Watch App
//
//  Service for handling QR Code generation with iPhone support
//

import Foundation
import SwiftUI
import WatchConnectivity

class QRCodeService: NSObject, ObservableObject {
    static let shared = QRCodeService()
    
    @Published var cachedQRCodes: [String: UIImage] = [:]
    @Published var isGenerating = false
    
    private override init() {
        super.init()
        setupWatchConnectivity()
    }
    
    private func setupWatchConnectivity() {
        if WCSession.isSupported() {
            WCSession.default.delegate = self
            WCSession.default.activate()
        }
    }
    
    // Generate QR Code - try iPhone first, fallback to local
    func generateQRCode(for data: String, completion: @escaping (UIImage?) -> Void) {
        // Check cache first
        if let cached = cachedQRCodes[data] {
            completion(cached)
            return
        }
        
        isGenerating = true
        
        // Try to get from iPhone if connected
        if WCSession.default.isReachable {
            requestQRCodeFromPhone(data: data) { [weak self] image in
                DispatchQueue.main.async {
                    self?.isGenerating = false
                    if let image = image {
                        self?.cachedQRCodes[data] = image
                        completion(image)
                    } else {
                        // Fallback to local generation
                        let localImage = SimpleQRCodeGenerator.generateQRCode(from: data)
                        if let localImage = localImage {
                            self?.cachedQRCodes[data] = localImage
                        }
                        completion(localImage)
                    }
                }
            }
        } else {
            // Use local generation
            DispatchQueue.main.async { [weak self] in
                self?.isGenerating = false
                let localImage = SimpleQRCodeGenerator.generateQRCode(from: data)
                if let localImage = localImage {
                    self?.cachedQRCodes[data] = localImage
                }
                completion(localImage)
            }
        }
    }
    
    private func requestQRCodeFromPhone(data: String, completion: @escaping (UIImage?) -> Void) {
        let message = ["action": "generateQRCode", "data": data]
        
        WCSession.default.sendMessage(message, replyHandler: { response in
            if let imageData = response["qrCodeImage"] as? Data,
               let image = UIImage(data: imageData) {
                completion(image)
            } else {
                completion(nil)
            }
        }) { error in
            print("Error requesting QR code from phone: \(error.localizedDescription)")
            completion(nil)
        }
    }
}

// MARK: - WCSessionDelegate
extension QRCodeService: WCSessionDelegate {
    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        if let error = error {
            print("WatchConnectivity activation error: \(error.localizedDescription)")
        }
    }
    
    func session(_ session: WCSession, didReceiveMessage message: [String : Any]) {
        // Handle incoming QR codes from iPhone
        if let action = message["action"] as? String,
           action == "qrCodeGenerated",
           let data = message["data"] as? String,
           let imageData = message["qrCodeImage"] as? Data,
           let image = UIImage(data: imageData) {
            DispatchQueue.main.async { [weak self] in
                self?.cachedQRCodes[data] = image
            }
        }
    }
}

// Enhanced QR Code View with iPhone support
struct EnhancedQRCodeView: View {
    let data: String
    let size: CGFloat
    
    @StateObject private var qrService = QRCodeService.shared
    @State private var qrImage: UIImage?
    @State private var showInfo = false
    
    var body: some View {
        VStack(spacing: 8) {
            if let image = qrImage {
                Image(uiImage: image)
                    .interpolation(.none)
                    .resizable()
                    .scaledToFit()
                    .frame(width: size, height: size)
                    .overlay(
                        // Add loading indicator if generating
                        qrService.isGenerating ?
                        Color.black.opacity(0.3)
                            .overlay(
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    .scaleEffect(0.8)
                            )
                        : nil
                    )
                    .onTapGesture {
                        showInfo.toggle()
                    }
            } else {
                // Placeholder while loading
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Color.white)
                        .frame(width: size, height: size)
                    
                    if qrService.isGenerating {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .black))
                            .scaleEffect(0.8)
                    } else {
                        VStack(spacing: 4) {
                            Image(systemName: "qrcode")
                                .font(.system(size: size / 3))
                                .foregroundColor(.black.opacity(0.3))
                            
                            Text("生成中...")
                                .font(.system(size: 10))
                                .foregroundColor(.black.opacity(0.5))
                        }
                    }
                }
            }
            
            if showInfo {
                VStack(spacing: 4) {
                    if WCSession.default.isReachable {
                        HStack(spacing: 4) {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.system(size: 10))
                                .foregroundColor(.green)
                            Text("iPhone 已連接")
                                .font(.system(size: 10))
                                .foregroundColor(.green)
                        }
                    } else {
                        HStack(spacing: 4) {
                            Image(systemName: "exclamationmark.circle.fill")
                                .font(.system(size: 10))
                                .foregroundColor(.orange)
                            Text("使用本地 QR")
                                .font(.system(size: 10))
                                .foregroundColor(.orange)
                        }
                    }
                }
                .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.3), value: showInfo)
        .onAppear {
            loadQRCode()
        }
        .onChange(of: data) {
            loadQRCode()
        }
    }
    
    private func loadQRCode() {
        qrService.generateQRCode(for: data) { image in
            DispatchQueue.main.async {
                self.qrImage = image
            }
        }
    }
}