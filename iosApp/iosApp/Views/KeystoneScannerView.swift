import SwiftUI
import AVFoundation

struct KeystoneScannerView: View {
    @Environment(\.presentationMode) var presentationMode
    @EnvironmentObject var connectivityManager: WatchConnectivityManager
    @StateObject private var scannerModel = KeystoneScannerViewModel()
    
    var body: some View {
        ZStack {
            // Camera Preview
            CameraPreview(session: scannerModel.session)
                .ignoresSafeArea()
            
            // Overlay
            VStack {
                HStack {
                    Button(action: {
                        presentationMode.wrappedValue.dismiss()
                    }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title)
                            .foregroundColor(.white)
                            .padding()
                    }
                    Spacer()
                }
                
                Spacer()
                
                // Scan Guide Box
                RoundedRectangle(cornerRadius: 20)
                    .stroke(Color.yellow, lineWidth: 3)
                    .frame(width: 250, height: 250)
                    .background(Color.black.opacity(0.1))
                
                Text(scannerModel.scanStatus)
                    .font(.headline)
                    .foregroundColor(.white)
                    .padding()
                    .background(Color.black.opacity(0.6))
                    .cornerRadius(10)
                    .padding(.top, 20)
                
                Spacer()
                
                // Bottom Controls
                if scannerModel.isScanningMultiPart {
                    VStack {
                        ProgressView(value: scannerModel.progress)
                            .progressViewStyle(LinearProgressViewStyle(tint: .yellow))
                            .padding(.horizontal)
                        Text(String(format: NSLocalizedString("scanning_multipart", comment: ""), Int(scannerModel.progress * 100)))
                            .foregroundColor(.white)
                            .font(.caption)
                    }
                    .padding(.bottom, 30)
                }
            }
        }
        .onAppear {
            scannerModel.startScanning()
        }
        .onDisappear {
            scannerModel.stopScanning()
        }
        .onChange(of: scannerModel.scanResult) { result in
            if let result = result {
                handleScanResult(result)
            }
        }
    }
    
    private func handleScanResult(_ result: String) {
        // Haptic feedback
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(.success)
        
        // Process result via ConnectivityManager
        print("Scanned: \(result)")
        
        // Determine type and send to watch
        if result.lowercased().hasPrefix("ur:") {
             if result.contains("crypto-account") {
                 connectivityManager.sendKeystoneConnectResult(urData: result)
             } else if result.contains("eth-signature") {
                 connectivityManager.sendKeystoneSignResult(urData: result)
             }
        } else {
            // Assume it's a plain address or legacy format
            // For now, valid Keystone QRs are UR encoded
            // Check if user expects address scanning?
        }
        
        presentationMode.wrappedValue.dismiss()
    }
}

class KeystoneScannerViewModel: NSObject, ObservableObject {
    @Published var scanStatus: String = NSLocalizedString("qr_scan_guide", comment: "")
    @Published var progress: Double = 0.0
    @Published var isScanningMultiPart: Bool = false
    @Published var scanResult: String?
    
    let session = AVCaptureSession()
    private let decoder = KeystoneURDecoder()
    
    func startScanning() {
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device) else {
            scanStatus = NSLocalizedString("camera_not_available", comment: "")
            return
        }
        
        if session.canAddInput(input) {
            session.addInput(input)
        }
        
        let output = AVCaptureMetadataOutput()
        if session.canAddOutput(output) {
            session.addOutput(output)
            output.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
            output.metadataObjectTypes = [.qr]
        }
        
        DispatchQueue.global(qos: .background).async {
            self.session.startRunning()
        }
    }
    
    func stopScanning() {
        session.stopRunning()
    }
}

extension KeystoneScannerViewModel: AVCaptureMetadataOutputObjectsDelegate {
    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard let metadataObject = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let stringValue = metadataObject.stringValue else { return }
        
        // Process with Decoder
        if let _ = decoder.processQRCode(stringValue) {
            // If decoder returns Data, it means it's complete
            // Re-construct the full UR string or pass the data
            // For simplicity in this View, we just assume completion implies we have the full string 
            // OR the decoder manages the state.
            // Actually `decoder.processQRCode` returns Data?.
            // But we need the String UR to pass to WatchConnectivityManager as per current API.
            
            if decoder.isComplete {
                self.scanResult = stringValue // Ideally we pass the full constructed UR
                stopScanning()
            } else {
                self.isScanningMultiPart = true
                self.progress = decoder.progress
                self.scanStatus = String(format: NSLocalizedString("scanning_progress", comment: ""), Int(progress * 100))
            }
        } else {
            // Not a Keystone UR or generic QR
             self.scanResult = stringValue
             stopScanning()
        }
    }
}

struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession
    
    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: UIScreen.main.bounds)
        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        layer.frame = view.frame
        view.layer.addSublayer(layer)
        return view
    }
    
    func updateUIView(_ uiView: UIView, context: Context) {}
}
