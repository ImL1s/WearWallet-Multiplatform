import SwiftUI
#if os(iOS)
import CoreImage
#endif

/**
 * Keystone 硬體錢包整合視圖
 * 
 * 提供 QR Code 掃描和顯示功能
 * 用於與 Keystone 硬體錢包交互
 */
struct KeystoneView: View {
    @State private var isShowingQR = false
    @State private var qrCodeData: [String] = []
    @State private var currentQRIndex = 0
    @State private var statusMessage = "準備連接 Keystone"
    @State private var walletInfo: String? = nil
    @State private var isScanning = false
    
    // QR Code 動畫計時器
    let timer = Timer.publish(every: 0.2, on: .main, in: .common).autoconnect()
    
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // 狀態訊息
                Text(statusMessage)
                    .font(.headline)
                    .multilineTextAlignment(.center)
                    .padding()
                
                // QR Code 顯示區域
                if isShowingQR && !qrCodeData.isEmpty {
                    QRCodeDisplayView(
                        qrData: qrCodeData[currentQRIndex],
                        index: currentQRIndex + 1,
                        total: qrCodeData.count
                    )
                    .frame(width: 150, height: 150)
                    .onReceive(timer) { _ in
                        if qrCodeData.count > 1 {
                            currentQRIndex = (currentQRIndex + 1) % qrCodeData.count
                        }
                    }
                }
                
                // 錢包資訊
                if let info = walletInfo {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("錢包資訊")
                            .font(.headline)
                        Text(info)
                            .font(.caption)
                            .lineLimit(nil)
                    }
                    .padding()
                    .background(Color.gray.opacity(0.2))
                    .cornerRadius(10)
                }
                
                // 操作按鈕
                VStack(spacing: 12) {
                    Button(action: importWallet) {
                        Label("導入錢包", systemImage: "qrcode.viewfinder")
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isScanning)
                    
                    Button(action: generateSignRequest) {
                        Label("生成簽名請求", systemImage: "signature")
                    }
                    .buttonStyle(.bordered)
                    
                    Button(action: testConnection) {
                        Label("測試連接", systemImage: "wifi")
                    }
                    .buttonStyle(.bordered)
                }
                .padding()
            }
        }
        .navigationTitle("Keystone")
        .navigationBarTitleDisplayMode(.inline)
    }
    
    // MARK: - Actions
    
    private func importWallet() {
        isScanning = true
        statusMessage = "請在 iPhone 上掃描 Keystone QR Code..."
        
        // 請求 iPhone 掃描
        KotlinNativeBridge.shared.requestQRScanFromPhone { qrData in
            DispatchQueue.main.async {
                self.isScanning = false
                
                if let qrData = qrData {
                    self.handleImportedWallet(qrData)
                } else {
                    self.statusMessage = "掃描失敗，請重試"
                }
            }
        }
    }
    
    private func handleImportedWallet(_ qrData: String) {
        guard let walletData = KotlinNativeBridge.shared.importWallet(qrData) else {
            statusMessage = "導入錢包失敗"
            return
        }
        
        statusMessage = "成功導入錢包"
        
        // 顯示錢包資訊
        if let name = walletData["name"] as? String,
           let fingerprint = walletData["masterFingerprint"] as? String,
           let addresses = walletData["addresses"] as? [[String: String]] {
            
            var info = "名稱: \(name)\n"
            info += "指紋: \(fingerprint)\n"
            info += "地址數: \(addresses.count)"
            
            walletInfo = info
        }
    }
    
    private func generateSignRequest() {
        statusMessage = "生成簽名請求中..."
        
        // 測試交易數據
        let testTx = "0x02f86f0182520894" + String(repeating: "00", count: 20) + "8502540be40082520894" + String(repeating: "ff", count: 20) + "880de0b6b3a764000080c0"
        
        guard let qrCodes = KotlinNativeBridge.shared.generateEthSignRequest(
            unsignedTxHex: testTx,
            derivationPath: "m/44'/60'/0'/0/0",
            masterFingerprint: "F23F9FD2",
            chainId: 1,
            requestId: UUID().uuidString,
            fromAddress: nil
        ) else {
            statusMessage = "生成簽名請求失敗"
            return
        }
        
        qrCodeData = qrCodes
        currentQRIndex = 0
        isShowingQR = true
        statusMessage = "請使用 Keystone 掃描以下 QR Code"
    }
    
    private func testConnection() {
        statusMessage = "測試連接中..."
        
        // 測試 Keystone 橋接是否正常
        let success = KeystoneSwiftBridge.setup()
        
        statusMessage = success ? "連接正常 ✅" : "連接失敗 ❌"
    }
}

// MARK: - QR Code Display View

struct QRCodeDisplayView: View {
    let qrData: String
    let index: Int
    let total: Int
    
    var body: some View {
        VStack(spacing: 8) {
            if let qrImage = generateQRCode(from: qrData) {
                Image(uiImage: qrImage)
                    .interpolation(.none)
                    .resizable()
                    .scaledToFit()
            } else {
                Rectangle()
                    .fill(Color.gray.opacity(0.3))
                    .overlay(
                        Text("QR Code")
                            .foregroundColor(.gray)
                    )
            }
            
            if total > 1 {
                Text("\(index) / \(total)")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
    }
    
    private func generateQRCode(from string: String) -> UIImage? {
        #if os(iOS)
        let context = CIContext()
        guard let filter = CIFilter(name: "CIQRCodeGenerator") else { return nil }
        
        filter.setValue(Data(string.utf8), forKey: "inputMessage")
        filter.setValue("M", forKey: "inputCorrectionLevel")
        
        guard let outputImage = filter.outputImage else { return nil }
        
        let scaledImage = outputImage.transformed(by: CGAffineTransform(scaleX: 3, y: 3))
        
        guard let cgImage = context.createCGImage(scaledImage, from: scaledImage.extent) else { return nil }
        
        return UIImage(cgImage: cgImage)
        #else
        // watchOS 不支援 CoreImage，返回占位圖像
        // 實際使用時，QR Code 會在 iPhone 上生成並透過 WatchConnectivity 傳輸
        return nil
        #endif
    }
}

// MARK: - Preview

struct KeystoneView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationView {
            KeystoneView()
        }
    }
}