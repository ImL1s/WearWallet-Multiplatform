import SwiftUI
import AVFoundation
import Combine

/**
 * Keystone QR Code 掃描視圖
 * 
 * 功能：
 * 1. 掃描 Keystone 返回的簽名 QR Code
 * 2. 支援多片段 UR 格式解碼
 * 3. 顯示掃描進度
 * 4. 處理錯誤和重試
 */
struct KeystoneQRScannerView: View {
    @StateObject private var viewModel: KeystoneQRScannerViewModel
    @Environment(\.dismiss) private var dismiss
    
    init(requestId: String, expectedType: URType = .CRYPTO_SIGNATURE) {
        _viewModel = StateObject(wrappedValue: KeystoneQRScannerViewModel(
            requestId: requestId,
            expectedType: expectedType
        ))
    }
    
    var body: some View {
        NavigationView {
            ZStack {
                // 相機預覽層
                CameraPreviewView(session: viewModel.captureSession)
                    .ignoresSafeArea()
                
                // 掃描框架
                ScannerOverlayView()
                
                // UI 控制層
                VStack {
                    // 頂部狀態欄
                    VStack(spacing: 12) {
                        HStack {
                            Text(NSLocalizedString("keystone_scan_signature", comment: ""))
                                .font(.title2)
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                            
                            Spacer()
                            
                            Button(action: {
                                viewModel.cancelScanning()
                                dismiss()
                            }) {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.title2)
                                    .foregroundColor(.white)
                            }
                        }
                        .padding(.horizontal)
                        
                        // 進度指示器
                        if viewModel.isMultipart {
                            VStack(spacing: 8) {
                                ProgressView(value: viewModel.scanProgress)
                                    .progressViewStyle(LinearProgressViewStyle(tint: .green))
                                    .frame(height: 6)
                                    .background(Color.white.opacity(0.3))
                                    .cornerRadius(3)
                                
                                Text(String(format: NSLocalizedString("keystone_scanned_parts_format", comment: ""), viewModel.scannedParts, viewModel.totalParts))
                                    .font(.caption)
                                    .foregroundColor(.white)
                            }
                            .padding(.horizontal)
                        }
                    }
                    .padding(.top, 50)
                    .padding(.bottom, 20)
                    .background(
                        LinearGradient(
                            gradient: Gradient(colors: [.black.opacity(0.8), .clear]),
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    
                    Spacer()
                    
                    // 底部資訊和控制
                    VStack(spacing: 16) {
                        // 狀態訊息
                        if let statusMessage = viewModel.statusMessage {
                            Text(statusMessage)
                                .font(.body)
                                .foregroundColor(.white)
                                .padding(.horizontal, 20)
                                .padding(.vertical, 10)
                                .background(viewModel.isError ? Color.red : Color.blue)
                                .cornerRadius(10)
                        }
                        
                        // 說明文字
                        VStack(spacing: 8) {
                            Label(NSLocalizedString("qr_scan_guide", comment: ""), systemImage: "viewfinder")
                                .font(.footnote)
                                .foregroundColor(.white)
                            
                            if viewModel.isMultipart {
                                Label(NSLocalizedString("keystone_continue_scan_all", comment: ""), systemImage: "doc.on.doc")
                                    .font(.footnote)
                                    .foregroundColor(.white.opacity(0.8))
                            }
                        }
                        
                        // 手電筒開關
                        Button(action: viewModel.toggleTorch) {
                            Label(
                                viewModel.isTorchOn ? NSLocalizedString("turn_off_torch", comment: "") : NSLocalizedString("turn_on_torch", comment: ""),
                                systemImage: viewModel.isTorchOn ? "flashlight.on.fill" : "flashlight.off.fill"
                            )
                            .foregroundColor(.white)
                            .padding(.horizontal, 20)
                            .padding(.vertical, 12)
                            .background(Color.gray.opacity(0.5))
                            .cornerRadius(10)
                        }
                    }
                    .padding(.bottom, 30)
                    .background(
                        LinearGradient(
                            gradient: Gradient(colors: [.clear, .black.opacity(0.8)]),
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                }
            }
            .navigationBarHidden(true)
            .onAppear {
                viewModel.startScanning()
            }
            .onDisappear {
                viewModel.stopScanning()
            }
            .alert(NSLocalizedString("scan_complete", comment: ""), isPresented: $viewModel.showSuccessAlert) {
                Button(NSLocalizedString("ok", comment: "")) {
                    dismiss()
                }
            } message: {
                Text(NSLocalizedString("keystone_received_signature", comment: ""))
            }
            .alert(NSLocalizedString("scan_failed", comment: ""), isPresented: $viewModel.showErrorAlert) {
                Button(NSLocalizedString("retry", comment: "")) {
                    viewModel.resetScanning()
                }
                Button(NSLocalizedString("cancel", comment: "")) {
                    dismiss()
                }
            } message: {
                Text(viewModel.errorMessage ?? NSLocalizedString("error_generic", comment: ""))
            }
        }
    }
}

/**
 * 相機預覽視圖
 */
struct CameraPreviewView: UIViewRepresentable {
    let session: AVCaptureSession
    
    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        
        let previewLayer = AVCaptureVideoPreviewLayer(session: session)
        previewLayer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(previewLayer)
        
        DispatchQueue.main.async {
            previewLayer.frame = view.bounds
        }
        
        return view
    }
    
    func updateUIView(_ uiView: UIView, context: Context) {
        if let previewLayer = uiView.layer.sublayers?.first as? AVCaptureVideoPreviewLayer {
            DispatchQueue.main.async {
                previewLayer.frame = uiView.bounds
            }
        }
    }
}

/**
 * 掃描框架覆蓋層
 */
struct ScannerOverlayView: View {
    @State private var isAnimating = false
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // 半透明背景
                Color.black.opacity(0.5)
                    .ignoresSafeArea()
                
                // 掃描框架
                let scanSize = min(geometry.size.width, geometry.size.height) * 0.7
                
                // 透明掃描區域
                Rectangle()
                    .frame(width: scanSize, height: scanSize)
                    .position(x: geometry.size.width / 2, y: geometry.size.height / 2)
                    .blendMode(.destinationOut)
                
                // 框架邊角
                ScannerCorners()
                    .stroke(Color.green, lineWidth: 3)
                    .frame(width: scanSize, height: scanSize)
                    .position(x: geometry.size.width / 2, y: geometry.size.height / 2)
                
                // 掃描線動畫
                Rectangle()
                    .fill(
                        LinearGradient(
                            gradient: Gradient(colors: [.clear, .green, .clear]),
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .frame(width: scanSize - 10, height: 2)
                    .position(
                        x: geometry.size.width / 2,
                        y: geometry.size.height / 2 - scanSize / 2 + (isAnimating ? scanSize : 0)
                    )
                    .animation(
                        Animation.linear(duration: 2)
                            .repeatForever(autoreverses: false),
                        value: isAnimating
                    )
            }
            .compositingGroup()
            .onAppear {
                isAnimating = true
            }
        }
    }
}

/**
 * 掃描框架邊角形狀
 */
struct ScannerCorners: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let cornerLength: CGFloat = 30
        
        // 左上角
        path.move(to: CGPoint(x: 0, y: cornerLength))
        path.addLine(to: CGPoint(x: 0, y: 0))
        path.addLine(to: CGPoint(x: cornerLength, y: 0))
        
        // 右上角
        path.move(to: CGPoint(x: rect.width - cornerLength, y: 0))
        path.addLine(to: CGPoint(x: rect.width, y: 0))
        path.addLine(to: CGPoint(x: rect.width, y: cornerLength))
        
        // 右下角
        path.move(to: CGPoint(x: rect.width, y: rect.height - cornerLength))
        path.addLine(to: CGPoint(x: rect.width, y: rect.height))
        path.addLine(to: CGPoint(x: rect.width - cornerLength, y: rect.height))
        
        // 左下角
        path.move(to: CGPoint(x: cornerLength, y: rect.height))
        path.addLine(to: CGPoint(x: 0, y: rect.height))
        path.addLine(to: CGPoint(x: 0, y: rect.height - cornerLength))
        
        return path
    }
}

/**
 * QR Code 掃描視圖模型
 */
class KeystoneQRScannerViewModel: NSObject, ObservableObject {
    @Published var statusMessage: String?
    @Published var isError: Bool = false
    @Published var scanProgress: Double = 0.0
    @Published var scannedParts: Int = 0
    @Published var totalParts: Int = 0
    @Published var isMultipart: Bool = false
    @Published var isTorchOn: Bool = false
    @Published var showSuccessAlert: Bool = false
    @Published var showErrorAlert: Bool = false
    @Published var errorMessage: String?
    
    let requestId: String
    let expectedType: URType
    let captureSession = AVCaptureSession()
    
    private var urDecoder: KeystoneURDecoder = KeystoneURDecoder()
    private let metadataOutput = AVCaptureMetadataOutput()
    private let sessionQueue = DispatchQueue(label: "scanner.session.queue")
    private var captureDevice: AVCaptureDevice?
    
    init(requestId: String, expectedType: URType) {
        self.requestId = requestId
        self.expectedType = expectedType
        super.init()
        setupCaptureSession()
    }
    
    private func setupCaptureSession() {
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            
            // 配置相機
            guard let videoCaptureDevice = AVCaptureDevice.default(for: .video) else {
                self.handleError("無法訪問相機")
                return
            }
            
            self.captureDevice = videoCaptureDevice
            
            do {
                let videoInput = try AVCaptureDeviceInput(device: videoCaptureDevice)
                
                if self.captureSession.canAddInput(videoInput) {
                    self.captureSession.addInput(videoInput)
                }
                
                if self.captureSession.canAddOutput(self.metadataOutput) {
                    self.captureSession.addOutput(self.metadataOutput)
                    
                    self.metadataOutput.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
                    self.metadataOutput.metadataObjectTypes = [.qr]
                }
            } catch {
                self.handleError("相機初始化失敗: \(error.localizedDescription)")
            }
        }
    }
    
    func startScanning() {
        sessionQueue.async { [weak self] in
            self?.captureSession.startRunning()
        }
        
        DispatchQueue.main.async {
            self.statusMessage = NSLocalizedString("preparing_scanner", comment: "")
            self.isError = false
        }
    }
    
    func stopScanning() {
        sessionQueue.async { [weak self] in
            self?.captureSession.stopRunning()
        }
        
        if isTorchOn {
            toggleTorch()
        }
    }
    
    func resetScanning() {
        urDecoder.reset()
        scannedParts = 0
        totalParts = 0
        isMultipart = false
        scanProgress = 0.0
        statusMessage = NSLocalizedString("preparing_scanner", comment: "")
        isError = false
        startScanning()
    }
    
    func toggleTorch() {
        guard let device = captureDevice, device.hasTorch else { return }
        
        do {
            try device.lockForConfiguration()
            device.torchMode = isTorchOn ? .off : .on
            device.unlockForConfiguration()
            isTorchOn.toggle()
        } catch {
            print("Torch error: \(error)")
        }
    }
    
    func cancelScanning() {
        stopScanning()
        
        // 通知取消
        NotificationCenter.default.post(
            name: Notification.Name("KeystoneRequestCancelled"),
            object: nil,
            userInfo: ["requestId": requestId]
        )
    }
    
    private func handleError(_ message: String) {
        DispatchQueue.main.async {
            self.errorMessage = message
            self.statusMessage = message
            self.isError = true
            self.showErrorAlert = true
        }
    }
    
    private func handleSuccess(_ signature: String) {
        stopScanning()
        
        // 發送簽名結果到 Notification Center (供 App 內部使用)
        NotificationCenter.default.post(
            name: Notification.Name("KeystoneSignatureReceived"),
            object: nil,
            userInfo: [
                "requestId": requestId,
                "signature": signature
            ]
        )
        
        // ✅ 同時通過 WatchConnectivity 發送結果回手錶
        if expectedType == .CRYPTO_SIGNATURE || expectedType == .CRYPTO_ACCOUNT {
            WatchConnectivityManager.shared.sendKeystoneSignResult(urData: signature)
        }
        
        DispatchQueue.main.async {
            self.showSuccessAlert = true
        }
    }
}

// MARK: - AVCaptureMetadataOutputObjectsDelegate
extension KeystoneQRScannerViewModel: AVCaptureMetadataOutputObjectsDelegate {
    func metadataOutput(_ output: AVCaptureMetadataOutput, 
                       didOutput metadataObjects: [AVMetadataObject],
                       from connection: AVCaptureConnection) {
        
        guard let metadataObject = metadataObjects.first,
              let readableObject = metadataObject as? AVMetadataMachineReadableCodeObject,
              let qrString = readableObject.stringValue else {
            return
        }
        
        processQRCode(qrString)
    }
    
    private func processQRCode(_ qrString: String) {
        // 檢查是否為 UR 格式
        guard qrString.hasPrefix("ur:") || qrString.hasPrefix("UR:") else {
            statusMessage = NSLocalizedString("not_keystone_qr", comment: "")
            isError = true
            return
        }
        
        statusMessage = NSLocalizedString("decoding", comment: "")
        isError = false
        
        // 使用 KeystoneURDecoder 處理 QR Code
        if let decodedData = urDecoder.processQRCode(qrString) {
            // 解碼完成
            isMultipart = urDecoder.progress < 1.0 && urDecoder.progress > 0
            scanProgress = urDecoder.progress
            scannedParts = Int(urDecoder.progress * Double(max(totalParts, 1)))
            
            if urDecoder.isComplete {
                switch expectedType {
                case .CRYPTO_ACCOUNT:
                    if let hdKey = urDecoder.parseHDKey(decodedData) {
                        // Construct JSON with full details
                        let resultDict: [String: Any] = [
                            "xpub": hdKey.extendedPublicKey,
                            "xfp": hdKey.masterFingerprint,
                            "path": hdKey.derivationPath,
                            "accounts": hdKey.accounts.map { ["path": $0.path, "address": $0.address, "chainId": $0.chainId] }
                        ]
                        
                        if let jsonData = try? JSONSerialization.data(withJSONObject: resultDict, options: []),
                           let jsonString = String(data: jsonData, encoding: .utf8) {
                            handleSuccess(jsonString)
                        } else {
                            // Fallback to just xpub if JSON fails
                            handleSuccess(hdKey.extendedPublicKey)
                        }
                    } else {
                        handleError(NSLocalizedString("error_parse_hdkey", comment: ""))
                    }
                case .CRYPTO_SIGNATURE:
                    if let signature = urDecoder.parseEthSignature(decodedData) {
                        // For signature, we might just need the signature hex, but JSON is cleaner for consistency
                         let resultDict: [String: Any] = [
                            "signature": signature.signature,
                            "requestId": signature.requestId
                        ]
                        
                        if let jsonData = try? JSONSerialization.data(withJSONObject: resultDict, options: []),
                           let jsonString = String(data: jsonData, encoding: .utf8) {
                            handleSuccess(jsonString)
                        } else {
                            handleSuccess(signature.signature)
                        }
                    } else {
                        handleError(NSLocalizedString("error_parse_signature", comment: ""))
                    }
                default:
                    break
                }
            }
        } else {
            // 多片段模式，更新進度
            if urDecoder.error != nil {
                handleError(urDecoder.error ?? "解碼失敗")
            } else {
                // 更新 UI 狀態
                isMultipart = true
                scanProgress = urDecoder.progress
                
                // 從第一個掃描的 QR 碼提取總片段數
                if totalParts == 0 {
                    let components = qrString.uppercased().components(separatedBy: "/")
                    if components.count >= 2 {
                        let partInfo = components[1]
                        if partInfo.contains("-") {
                            totalParts = Int(partInfo.components(separatedBy: "-").last ?? "1") ?? 1
                        } else if partInfo.contains("OF") {
                            totalParts = Int(partInfo.components(separatedBy: "OF").last ?? "1") ?? 1
                        }
                    }
                }
                
                scannedParts = Int(urDecoder.progress * Double(max(totalParts, 1)))
                statusMessage = String(format: NSLocalizedString("keystone_continue_scan_format", comment: ""), scannedParts, totalParts)
            }
        }
    }
}

// 預覽
struct KeystoneQRScannerView_Previews: PreviewProvider {
    static var previews: some View {
        KeystoneQRScannerView(requestId: "test-request")
    }
}