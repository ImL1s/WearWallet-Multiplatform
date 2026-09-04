import SwiftUI
import Combine

/**
 * Keystone 橋接協調器視圖
 * 
 * 負責協調 watchOS 請求和 Keystone 硬體錢包之間的通訊流程
 * 整合 KeystoneBridgeHandler 與 UI 元件
 */
struct KeystoneBridgeCoordinator: View {
    @StateObject private var viewModel = KeystoneBridgeViewModel()
    @State private var showQRDisplay = false
    @State private var showQRScanner = false
    
    var body: some View {
        NavigationView {
            ZStack {
                // 主要內容
                VStack(spacing: 20) {
                    // 狀態指示器
                    StatusIndicator(state: viewModel.bridgeState)
                        .padding(.top, 40)
                    
                    // 請求列表
                    if !viewModel.pendingRequests.isEmpty {
                        RequestListView(requests: viewModel.pendingRequests) { request in
                            viewModel.processRequest(request)
                        }
                    } else {
                        EmptyStateView()
                    }
                    
                    Spacer()
                    
                    // 調試按鈕（開發用）
                    #if DEBUG
                    VStack(spacing: 12) {
                        Button("keystone_simulate_watch") {
                            viewModel.simulateWatchRequest()
                        }
                        .buttonStyle(PrimaryButtonStyle())
                        
                        Button("keystone_clear_requests") {
                            viewModel.clearAllRequests()
                        }
                        .buttonStyle(SecondaryButtonStyle())
                    }
                    .padding()
                    #endif
                }
                .padding()
            }
            .navigationTitle("Keystone Bridge")
            .navigationBarTitleDisplayMode(.large)
            .sheet(isPresented: $showQRDisplay) {
                if let qrCodes = viewModel.currentQRCodes,
                   let requestId = viewModel.currentRequestId {
                    KeystoneQRDisplayView(
                        requestId: requestId,
                        qrCodes: qrCodes
                    )
                    .onDisappear {
                        if !showQRScanner {
                            showQRScanner = true
                        }
                    }
                }
            }
            .sheet(isPresented: $showQRScanner) {
                if let requestId = viewModel.currentRequestId {
                    KeystoneQRScannerView(
                        requestId: requestId,
                        expectedType: .CRYPTO_SIGNATURE
                    )
                    .onDisappear {
                        viewModel.completeCurrentRequest()
                    }
                }
            }
            .onReceive(viewModel.$shouldShowQRDisplay) { shouldShow in
                showQRDisplay = shouldShow
            }
            .onReceive(viewModel.$shouldShowQRScanner) { shouldShow in
                showQRScanner = shouldShow
            }
            .alert("error", isPresented: $viewModel.showError) {
                Button("ok") {
                    viewModel.dismissError()
                }
            } message: {
                Text(viewModel.errorMessage ?? NSLocalizedString("unknown_error", comment: ""))
            }
        }
    }
}

/**
 * 狀態指示器元件
 */
struct StatusIndicator: View {
    let state: BridgeState
    
    var body: some View {
        VStack(spacing: 12) {
            // 圖標
            Image(systemName: state.iconName)
                .font(.system(size: 50))
                .foregroundColor(state.color)
                .animation(.easeInOut, value: state)
            
            // 狀態文字
            Text(state.title)
                .font(.title2)
                .fontWeight(.semibold)
            
            Text(state.description)
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(state.color.opacity(0.1))
        )
    }
}

/**
 * 請求列表視圖
 */
struct RequestListView: View {
    let requests: [BridgeRequest]
    let onProcess: (BridgeRequest) -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("keystone_pending_requests")
                .font(.headline)
                .padding(.horizontal)
            
            ScrollView {
                VStack(spacing: 10) {
                    ForEach(requests) { request in
                        RequestCard(request: request) {
                            onProcess(request)
                        }
                    }
                }
                .padding(.horizontal)
            }
        }
    }
}

/**
 * 請求卡片
 */
struct RequestCard: View {
    let request: BridgeRequest
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            HStack {
                // 圖標
                Image(systemName: request.type.iconName)
                    .font(.title2)
                    .foregroundColor(.blue)
                    .frame(width: 40)
                
                // 資訊
                VStack(alignment: .leading, spacing: 4) {
                    Text(request.type.title)
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundColor(.primary)
                    
                    Text("keystone_from_watch")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    
                    Text(request.timestamp, style: .time)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                // 箭頭
                Image(systemName: "chevron.right")
                    .foregroundColor(.secondary)
            }
            .padding()
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color(.systemBackground))
                    .shadow(color: .black.opacity(0.1), radius: 5, x: 0, y: 2)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

/**
 * 空狀態視圖
 */
struct EmptyStateView: View {
    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "applewatch.radiowaves.left.and.right")
                .font(.system(size: 60))
                .foregroundColor(.gray)
            
            Text("keystone_waiting_title")
                .font(.title3)
                .fontWeight(.medium)
            
            Text("keystone_waiting_desc")
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding()
    }
}

/**
 * 橋接視圖模型
 */
class KeystoneBridgeViewModel: ObservableObject {
    @Published var bridgeState: BridgeState = .idle
    @Published var pendingRequests: [BridgeRequest] = []
    @Published var currentRequestId: String?
    @Published var currentQRCodes: [String]?
    @Published var shouldShowQRDisplay = false
    @Published var shouldShowQRScanner = false
    @Published var showError = false
    @Published var errorMessage: String?
    
    private var cancellables = Set<AnyCancellable>()
    private let bridgeHandler = KeystoneBridgeHandler.shared
    
    init() {
        setupNotificationObservers()
        checkForPendingRequests()
        setupWatchObservers()
    }
    
    private func setupNotificationObservers() {
        // 監聽 QR Code 顯示請求
        NotificationCenter.default.publisher(for: Notification.Name("ShowKeystoneQRCodes"))
            .sink { [weak self] notification in
                guard let self = self,
                      let userInfo = notification.userInfo,
                      let qrCodes = userInfo["qrCodes"] as? [String],
                      let requestId = userInfo["requestId"] as? String else {
                    return
                }
                
                DispatchQueue.main.async {
                    self.currentRequestId = requestId
                    self.currentQRCodes = qrCodes
                    self.shouldShowQRDisplay = true
                    self.bridgeState = .displayingQR
                }
            }
            .store(in: &cancellables)
        
        // 監聽簽名接收
        NotificationCenter.default.publisher(for: Notification.Name("KeystoneSignatureReceived"))
            .sink { [weak self] notification in
                guard let self = self else { return }
                
                DispatchQueue.main.async {
                    self.bridgeState = .completed
                    self.shouldShowQRScanner = false
                    
                    // 重置 WatchConnectivityManager 狀態
                    WatchConnectivityManager.shared.showKeystoneScanner = false
                    
                    self.completeCurrentRequest()
                }
            }
            .store(in: &cancellables)
        
        // 監聽取消請求
        NotificationCenter.default.publisher(for: Notification.Name("KeystoneRequestCancelled"))
            .sink { [weak self] notification in
                guard let self = self else { return }
                
                DispatchQueue.main.async {
                    self.bridgeState = .idle
                    self.shouldShowQRDisplay = false
                    self.shouldShowQRScanner = false
                    self.currentRequestId = nil
                    self.currentQRCodes = nil
                    
                    // 重置 WatchConnectivityManager 狀態
                    WatchConnectivityManager.shared.showKeystoneScanner = false
                }
            }
            .store(in: &cancellables)
    }
    
    private func setupWatchObservers() {
        // 監聽 WatchConnectivityManager 的掃描請求
        WatchConnectivityManager.shared.$showKeystoneScanner
            .receive(on: DispatchQueue.main)
            .sink { [weak self] show in
                guard let self = self else { return }
                
                if show, let requestId = WatchConnectivityManager.shared.currentRequestId {
                    // 根據請求類型設置狀態
                    self.currentRequestId = requestId
                    self.shouldShowQRScanner = true
                    self.bridgeState = .scanning
                    
                    // 將請求添加到列表中
                    if !self.pendingRequests.contains(where: { $0.id == requestId }) {
                        let requestType: BridgeRequest.RequestType = 
                            WatchConnectivityManager.shared.scannerExpectedType == .CRYPTO_ACCOUNT 
                                ? .getAccounts 
                                : .signTransaction
                        
                        let request = BridgeRequest(
                            id: requestId,
                            type: requestType,
                            timestamp: Date(),
                            data: NSLocalizedString("keystone_from_watch", comment: "")
                        )
                        self.pendingRequests.append(request)
                    }
                }
            }
            .store(in: &cancellables)
    }
    
    private func checkForPendingRequests() {
        // 檢查是否有待處理的請求
        // 這裡應該從 KeystoneBridgeHandler 獲取
        bridgeHandler.cleanupTimeoutRequests()
        
        // 模擬一些待處理請求（實際應從 bridgeHandler 獲取）
        #if DEBUG
        if pendingRequests.isEmpty {
            // 添加測試數據
        }
        #endif
    }
    
    func processRequest(_ request: BridgeRequest) {
        bridgeState = .processing
        currentRequestId = request.id
        
        // 生成 QR Codes
        Task {
            do {
                // 這裡應該調用實際的 bridgeHandler 方法
                let qrCodes = generateMockQRCodes(for: request)
                
                await MainActor.run {
                    self.currentQRCodes = qrCodes
                    self.shouldShowQRDisplay = true
                }
            } catch {
                await MainActor.run {
                    self.showError(error.localizedDescription)
                }
            }
        }
    }
    
    func completeCurrentRequest() {
        if let requestId = currentRequestId,
           let index = pendingRequests.firstIndex(where: { $0.id == requestId }) {
            pendingRequests.remove(at: index)
        }
        
        currentRequestId = nil
        currentQRCodes = nil
        bridgeState = pendingRequests.isEmpty ? .idle : .waiting
    }
    
    func clearAllRequests() {
        pendingRequests.removeAll()
        currentRequestId = nil
        currentQRCodes = nil
        bridgeState = .idle
    }
    
    func simulateWatchRequest() {
        let request = BridgeRequest(
            id: UUID().uuidString,
            type: .signTransaction,
            timestamp: Date(),
            data: "Mock transaction data"
        )
        pendingRequests.append(request)
        bridgeState = .waiting
    }
    
    func showError(_ message: String) {
        errorMessage = message
        showError = true
        bridgeState = .error
    }
    
    func dismissError() {
        showError = false
        errorMessage = nil
        bridgeState = pendingRequests.isEmpty ? .idle : .waiting
    }
    
    private func generateMockQRCodes(for request: BridgeRequest) -> [String] {
        // 實際生成 UR 格式的 QR Codes
        // 這應該調用 KeystoneBridgeHandler 來生成真實的 UR 編碼
        
        // 根據請求類型準備數據
        let urData: String
        switch request.type {
        case .signTransaction:
            // 準備交易數據的 UR 編碼
            urData = "ur:crypto-psbt/\(request.data)"
        case .getAccounts:
            // 準備賬戶請求的 UR 編碼
            urData = "ur:crypto-account/\(request.data)"
        case .verifyAddress:
            // 準備地址驗證的 UR 編碼
            urData = "ur:crypto-hdkey/\(request.data)"
        }
        
        // 如果數據太大，需要分片（Fountain Code）
        let maxFragmentSize = 200
        if urData.count > maxFragmentSize {
            // 分片處理
            var fragments: [String] = []
            let totalParts = (urData.count + maxFragmentSize - 1) / maxFragmentSize
            
            for i in 0..<totalParts {
                let start = i * maxFragmentSize
                let end = min(start + maxFragmentSize, urData.count)
                let fragment = String(urData[urData.index(urData.startIndex, offsetBy: start)..<urData.index(urData.startIndex, offsetBy: end)])
                fragments.append("ur:crypto-psbt/\(i+1)-\(totalParts)/\(fragment)")
            }
            
            return fragments
        } else {
            // 單個 QR Code
            return [urData]
        }
    }
}

// MARK: - 模型定義

/**
 * 橋接狀態
 */
enum BridgeState {
    case idle
    case waiting
    case processing
    case displayingQR
    case scanning
    case completed
    case error
    
    var iconName: String {
        switch self {
        case .idle: return "circle.dashed"
        case .waiting: return "clock.arrow.circlepath"
        case .processing: return "gearshape.2.fill"
        case .displayingQR: return "qrcode"
        case .scanning: return "qrcode.viewfinder"
        case .completed: return "checkmark.circle.fill"
        case .error: return "exclamationmark.triangle.fill"
        }
    }
    
    var color: Color {
        switch self {
        case .idle: return .gray
        case .waiting: return .orange
        case .processing: return .blue
        case .displayingQR: return .purple
        case .scanning: return .green
        case .completed: return .green
        case .error: return .red
        }
    }
    
    var title: String {
        switch self {
        case .idle: return NSLocalizedString("keystone_status_idle", comment: "")
        case .waiting: return NSLocalizedString("keystone_status_waiting", comment: "")
        case .processing: return NSLocalizedString("keystone_status_processing", comment: "")
        case .displayingQR: return NSLocalizedString("keystone_status_displaying_qr", comment: "")
        case .scanning: return NSLocalizedString("keystone_status_scanning", comment: "")
        case .completed: return NSLocalizedString("keystone_status_completed", comment: "")
        case .error: return NSLocalizedString("keystone_status_error", comment: "")
        }
    }
    
    var description: String {
        switch self {
        case .idle: return NSLocalizedString("keystone_status_idle_desc", comment: "Waiting for new requests")
        case .waiting: return NSLocalizedString("keystone_status_waiting_desc", comment: "Pending signature requests")
        case .processing: return NSLocalizedString("keystone_status_processing_desc", comment: "Preparing QR Code")
        case .displayingQR: return NSLocalizedString("keystone_status_displaying_qr_desc", comment: "Scan with Keystone")
        case .scanning: return NSLocalizedString("keystone_status_scanning_desc", comment: "Scanning signature")
        case .completed: return NSLocalizedString("keystone_status_completed_desc", comment: "Signature completed")
        case .error: return NSLocalizedString("keystone_status_error_desc", comment: "Error occurred")
        }
    }
}

/**
 * 橋接請求
 */
struct BridgeRequest: Identifiable {
    let id: String
    let type: RequestType
    let timestamp: Date
    let data: String
    
    enum RequestType {
        case signTransaction
        case getAccounts
        case verifyAddress
        
        var iconName: String {
            switch self {
            case .signTransaction: return "signature"
            case .getAccounts: return "person.2"
            case .verifyAddress: return "checkmark.shield"
            }
        }
        
        var title: String {
            switch self {
            case .signTransaction: return NSLocalizedString("request_type_sign_tx", comment: "")
            case .getAccounts: return NSLocalizedString("request_type_get_accounts", comment: "")
            case .verifyAddress: return NSLocalizedString("request_type_verify_addr", comment: "")
            }
        }
    }
}

// MARK: - 按鈕樣式

struct PrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundColor(.white)
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(Color.blue)
            .cornerRadius(10)
            .scaleEffect(configuration.isPressed ? 0.95 : 1.0)
    }
}

struct SecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundColor(.red)
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(Color.red.opacity(0.1))
            .cornerRadius(10)
            .scaleEffect(configuration.isPressed ? 0.95 : 1.0)
    }
}

// 預覽
struct KeystoneBridgeCoordinator_Previews: PreviewProvider {
    static var previews: some View {
        KeystoneBridgeCoordinator()
    }
}