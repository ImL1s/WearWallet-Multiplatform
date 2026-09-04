import SwiftUI
import CoreImage.CIFilterBuiltins
import Combine

/**
 * Keystone QR Code 顯示視圖
 * 
 * 功能：
 * 1. 顯示動態 QR Code 序列給 Keystone 掃描
 * 2. 支援 Fountain Code 動畫播放
 * 3. 提供進度指示器
 * 4. 處理超時和取消操作
 */
struct KeystoneQRDisplayView: View {
    @StateObject private var viewModel: KeystoneQRDisplayViewModel
    @Environment(\.dismiss) private var dismiss
    
    init(requestId: String, qrCodes: [String]) {
        _viewModel = StateObject(wrappedValue: KeystoneQRDisplayViewModel(
            requestId: requestId,
            qrCodes: qrCodes
        ))
    }
    
    var body: some View {
        NavigationView {
            ZStack {
                // 背景
                Color.black.ignoresSafeArea()
                
                VStack(spacing: 20) {
                    // 標題
                    Text(NSLocalizedString("keystone_scan_to_sign", comment: ""))
                        .font(.title2)
                        .foregroundColor(.white)
                        .padding(.top, 20)
                    
                    // QR Code 顯示區域
                    if let currentQRCode = viewModel.currentQRCode {
                        QRCodeView(data: currentQRCode)
                            .frame(width: 280, height: 280)
                            .background(Color.white)
                            .cornerRadius(20)
                            .overlay(
                                RoundedRectangle(cornerRadius: 20)
                                    .stroke(Color.blue, lineWidth: 3)
                            )
                            .shadow(color: .blue.opacity(0.3), radius: 10)
                    } else {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .frame(width: 280, height: 280)
                    }
                    
                    // 進度條
                    VStack(spacing: 8) {
                        ProgressView(value: viewModel.progress)
                            .progressViewStyle(LinearProgressViewStyle(tint: .blue))
                            .frame(height: 8)
                            .background(Color.gray.opacity(0.3))
                            .cornerRadius(4)
                        
                        HStack {
                            Text(String(format: NSLocalizedString("keystone_part_format", comment: ""), viewModel.currentIndex + 1, viewModel.totalParts))
                                .font(.caption)
                                .foregroundColor(.gray)
                            
                            Spacer()
                            
                            if viewModel.isAnimating {
                                Text(NSLocalizedString("keystone_animation_playing", comment: ""))
                                    .font(.caption)
                                    .foregroundColor(.green)
                            }
                        }
                    }
                    .padding(.horizontal, 40)
                    
                    // 說明文字
                    VStack(spacing: 12) {
                        Label(NSLocalizedString("keystone_status_displaying_qr_desc", comment: ""), systemImage: "qrcode.viewfinder")
                            .font(.footnote)
                            .foregroundColor(.white.opacity(0.8))
                        
                        if viewModel.totalParts > 1 {
                            Label(NSLocalizedString("keystone_qr_automatic_switch", comment: "QR Code will switch automatically"), systemImage: "arrow.triangle.2.circlepath")
                                .font(.footnote)
                                .foregroundColor(.white.opacity(0.6))
                        }
                    }
                    .padding(.horizontal, 30)
                    
                    Spacer()
                    
                    // 控制按鈕
                    HStack(spacing: 20) {
                        // 暫停/繼續按鈕
                        if viewModel.totalParts > 1 {
                            Button(action: viewModel.toggleAnimation) {
                                Label(
                                    viewModel.isAnimating ? NSLocalizedString("pause", comment: "") : NSLocalizedString("resume", comment: ""),
                                    systemImage: viewModel.isAnimating ? "pause.fill" : "play.fill"
                                )
                                .foregroundColor(.white)
                                .padding(.horizontal, 20)
                                .padding(.vertical, 12)
                                .background(Color.blue)
                                .cornerRadius(10)
                            }
                        }
                        
                        // 取消按鈕
                        Button(action: {
                            viewModel.cancelRequest()
                            dismiss()
                        }) {
                            Label(NSLocalizedString("cancel", comment: ""), systemImage: "xmark.circle.fill")
                                .foregroundColor(.white)
                                .padding(.horizontal, 20)
                                .padding(.vertical, 12)
                                .background(Color.red)
                                .cornerRadius(10)
                        }
                    }
                    .padding(.bottom, 30)
                }
            }
            .navigationBarHidden(true)
            .onAppear {
                viewModel.startAnimation()
            }
            .onDisappear {
                viewModel.stopAnimation()
            }
            .alert(NSLocalizedString("timeout", comment: ""), isPresented: $viewModel.showTimeoutAlert) {
                Button(NSLocalizedString("ok", comment: "")) {
                    dismiss()
                }
            } message: {
                Text(NSLocalizedString("keystone_qr_timeout", comment: ""))
            }
        }
    }
}

/**
 * QR Code 生成視圖
 */
struct QRCodeView: View {
    let data: String
    @State private var qrImage: UIImage?
    
    var body: some View {
        Group {
            if let qrImage = qrImage {
                Image(uiImage: qrImage)
                    .interpolation(.none)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
            } else {
                ProgressView()
            }
        }
        .onAppear {
            generateQRCode()
        }
        .onChange(of: data) { _ in
            generateQRCode()
        }
    }
    
    private func generateQRCode() {
        let context = CIContext()
        let filter = CIFilter.qrCodeGenerator()
        
        filter.message = Data(data.utf8)
        filter.correctionLevel = "H"
        
        guard let outputImage = filter.outputImage else { return }
        
        let scaledImage = outputImage.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
        
        if let cgImage = context.createCGImage(scaledImage, from: scaledImage.extent) {
            qrImage = UIImage(cgImage: cgImage)
        }
    }
}

/**
 * QR Code 顯示視圖模型
 */
class KeystoneQRDisplayViewModel: ObservableObject {
    @Published var currentQRCode: String?
    @Published var currentIndex: Int = 0
    @Published var progress: Double = 0.0
    @Published var isAnimating: Bool = false
    @Published var showTimeoutAlert: Bool = false
    
    let requestId: String
    let qrCodes: [String]
    var totalParts: Int { qrCodes.count }
    
    private var animationTimer: Timer?
    private var timeoutTimer: Timer?
    private let animationInterval: TimeInterval = 0.2 // 200ms per QR
    private let timeout: TimeInterval = 60.0 // 60 seconds
    
    init(requestId: String, qrCodes: [String]) {
        self.requestId = requestId
        self.qrCodes = qrCodes
        
        if !qrCodes.isEmpty {
            self.currentQRCode = qrCodes[0]
        }
    }
    
    func startAnimation() {
        guard totalParts > 1 else { return }
        
        isAnimating = true
        currentIndex = 0
        updateProgress()
        
        // 開始動畫循環
        animationTimer = Timer.scheduledTimer(withTimeInterval: animationInterval, repeats: true) { _ in
            self.nextQRCode()
        }
        
        // 設定超時
        timeoutTimer = Timer.scheduledTimer(withTimeInterval: timeout, repeats: false) { _ in
            self.handleTimeout()
        }
    }
    
    func stopAnimation() {
        isAnimating = false
        animationTimer?.invalidate()
        animationTimer = nil
        timeoutTimer?.invalidate()
        timeoutTimer = nil
    }
    
    func toggleAnimation() {
        if isAnimating {
            stopAnimation()
        } else {
            startAnimation()
        }
    }
    
    private func nextQRCode() {
        currentIndex = (currentIndex + 1) % totalParts
        currentQRCode = qrCodes[currentIndex]
        updateProgress()
    }
    
    private func updateProgress() {
        progress = Double(currentIndex + 1) / Double(totalParts)
    }
    
    private func handleTimeout() {
        stopAnimation()
        showTimeoutAlert = true
        cancelRequest()
    }
    
    func cancelRequest() {
        stopAnimation()
        
        // 通知 KeystoneBridgeHandler 取消請求
        NotificationCenter.default.post(
            name: Notification.Name("KeystoneRequestCancelled"),
            object: nil,
            userInfo: ["requestId": requestId]
        )
    }
    
    deinit {
        stopAnimation()
    }
}

// 預覽
struct KeystoneQRDisplayView_Previews: PreviewProvider {
    static var previews: some View {
        KeystoneQRDisplayView(
            requestId: "test-request",
            qrCodes: [
                "ur:crypto-psbt/1-3/lpadaxcfaxiacyvwlgdmhyoeaeaeaeaeaeaeadadctjokotpiecffdhncmvt",
                "ur:crypto-psbt/2-3/lpaoaxcfaxiacyvwlgdmhyoeaeaeaeaeaeaeadadctjokotpiecffdhncmvt",
                "ur:crypto-psbt/3-3/lpayaycfaxiacyvwlgdmhyoeaeaeaeaeaeaeadadctjokotpiecffdhncmvt"
            ]
        )
    }
}