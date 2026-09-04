import SwiftUI
import Combine
import coreKmp

// Mock Models for watchOS (Ideally should come from KMP via Shared Code, 
// but for pure SwiftUI preview and speed, defining local state wrapper or using KMP models if accessible)

// Assuming we have access to shared KMP code, but for this specific file generation,
// I will create a View that mimics the Wear OS structure: One Action Per Screen.

struct SwapView: View {
    @StateObject private var viewModel = SwapViewModel()
    @Binding var isPresented: Bool
    
    // Navigation Path for iOS 16+ / watchOS 9+
    @State private var path: [SwapRoute] = []
    
    enum SwapRoute: Hashable {
        case selectToToken
        case amountInput
        case quoteConfirm
        case success
    }
    
    var body: some View {
        ZStack {
            NavigationStack(path: $path) {
                SelectTokenView(title: "From", tokens: viewModel.supportedTokens) { token in
                    viewModel.fromToken = token
                    path.append(.selectToToken)
                }
                .navigationTitle("Swap")
                .navigationDestination(for: SwapRoute.self) { route in
                    switch route {
                    case .selectToToken:
                        SelectTokenView(title: "To", tokens: viewModel.supportedTokens) { token in
                            viewModel.toToken = token
                            path.append(.amountInput)
                        }
                    case .amountInput:
                        SwapAmountInputView(viewModel: viewModel) {
                            viewModel.fetchQuote()
                            path.append(.quoteConfirm)
                        }
                    case .quoteConfirm:
                        QuoteConfirmView(viewModel: viewModel) {
                            Task {
                                await viewModel.executeSwapAsync()
                                if viewModel.error == nil {
                                    path.append(.success)
                                }
                            }
                        }
                    case .success:
                        SwapSuccessView(onDone: {
                            isPresented = false
                        })
                    }
                }
            }
            
            // 全域載入動畫
            if viewModel.isLoading {
                LoadingView(message: "正在處理 Swap...")
            }
        }
        .toast(isPresented: $showError, message: viewModel.error ?? "", type: .error)
        .onChange(of: viewModel.error) { oldValue, newValue in
            if newValue != nil {
                showError = true
            }
        }
    }
    
    @State private var showError = false
}

// MARK: - Sub Views

struct SelectTokenView: View {
    let title: String
    let tokens: [SwiftSwapToken]
    let onSelect: (SwiftSwapToken) -> Void
    
    var body: some View {
        List(tokens) { token in
            Button(action: { onSelect(token) }) {
                HStack {
                    if let urlStr = token.logoUrl, let url = URL(string: urlStr) {
                        AsyncImage(url: url) { image in
                            image.resizable()
                        } placeholder: {
                            Color.gray.opacity(0.3)
                        }
                        .frame(width: 32, height: 32)
                        .clipShape(Circle())
                    } else {
                        Circle()
                            .fill(Color.gray.opacity(0.3))
                            .frame(width: 32, height: 32)
                            .overlay(Text(String(token.symbol.prefix(1))).font(.caption))
                    }
                    
                    VStack(alignment: .leading) {
                        Text(token.symbol).font(.headline)
                        Text(token.name).font(.caption2).foregroundColor(.secondary)
                    }
                    Spacer()
                    if token.price > 0 {
                        Text("$\(token.price, specifier: "%.2f")")
                             .font(.caption2).foregroundColor(.gray)
                    }
                }
                .padding(.vertical, 4)
            }
        }
        .navigationTitle("Swap \(title)")
    }
}

struct SwapAmountInputView: View {
    @ObservedObject var viewModel: SwapViewModel
    let onConfirm: () -> Void
    
    // Digital Crown state
    @State private var scrollAmount = 0.0
    
    var body: some View {
        VStack {
            Text("Amount (\(viewModel.fromToken?.symbol ?? ""))")
                .font(.caption)
            
            Text(String(format: "%.4f", scrollAmount))
                .font(.system(size: 24, weight: .bold, design: .monospaced))
                .foregroundColor(.accentColor)
                .focusable(true)
                .digitalCrownRotation($scrollAmount, from: 0, through: 100, by: 0.01, sensitivity: .low, isContinuous: false, isHapticFeedbackEnabled: true)
            
            Spacer()
            
            HStack {
                Button("25%") { scrollAmount = 0.25 }
                    .font(.caption2)
                    .buttonStyle(.bordered)
                Button("MAX") { scrollAmount = 1.0 }
                    .font(.caption2)
                    .buttonStyle(.bordered)
            }
            
            Button(action: {
                viewModel.amount = String(format: "%.4f", scrollAmount)
                onConfirm()
            }) {
                Text("Review")
            }
            .padding(.top)
        }
    }
}

struct QuoteConfirmView: View {
    @ObservedObject var viewModel: SwapViewModel
    let onConfirm: () -> Void
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollView {
                    VStack(spacing: 12) {
                        if viewModel.isLoading {
                            ProgressView()
                                .padding()
                        } else if let quote = viewModel.quote {
                            // Compact Quote Card
                            VStack(spacing: 8) {
                                // From Row
                                HStack {
                                    Text("支付")
                                        .font(.caption2)
                                        .foregroundColor(.secondary)
                                    Spacer()
                                    Text("\(viewModel.amount) \(viewModel.fromToken?.symbol ?? "")")
                                        .font(.system(size: 14, weight: .medium, design: .rounded))
                                }
                                
                                Divider()
                                
                                // To Row (Highlighted)
                                HStack {
                                    Text("獲得")
                                        .font(.caption2)
                                        .foregroundColor(.secondary)
                                    Spacer()
                                    Text("~\(quote.toAmount) \(viewModel.toToken?.symbol ?? "")")
                                        .font(.system(size: 16, weight: .bold, design: .rounded))
                                        .foregroundColor(.green)
                                }
                                
                                Divider()
                                
                                // Fee Row
                                HStack {
                                    Text("手續費")
                                        .font(.caption2)
                                        .foregroundColor(.secondary)
                                    Spacer()
                                    Text("$\(quote.fee)")
                                        .font(.caption2)
                                        .foregroundColor(.secondary)
                                }
                            }
                            .padding()
                            .background(Color.white.opacity(0.1))
                            .cornerRadius(12)
                        }
                    }
                    .padding()
                }
                
                // Pinned Bottom Action
                VStack(spacing: 8) {
                    Button(action: {
                        WKInterfaceDevice.current().play(.click)
                        onConfirm()
                    }) {
                        Text("確認兌換")
                            .font(.system(size: 16, weight: .semibold))
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.blue)
                    .disabled(viewModel.quote == nil)
                }
                .padding(.horizontal)
                .padding(.bottom, 4)
            }
            .navigationTitle("確認")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

struct SwapSuccessView: View {
    let onDone: () -> Void
    
    var body: some View {
        VStack {
            Image(systemName: "checkmark.circle.fill")
                .resizable()
                .frame(width: 50, height: 50)
                .foregroundColor(.green)
            Text("Success!")
                .font(.title3)
            Text("Tx Sent")
                .font(.caption)
            
            Button("Done", action: onDone)
                .padding(.top)
        }
    }
}

// SwipeToConfirmButton removed as we switched to standard Pinned Button.

// MARK: - View Model & Models

class SwapViewModel: ObservableObject {
    @Published var fromToken: SwiftSwapToken?
    @Published var toToken: SwiftSwapToken?
    @Published var amount: String = "0"
    @Published var quote: SwiftSwapQuote?
    @Published var isLoading = false
    @Published var error: String?
    @Published var userAddress: String = ""
    @Published var activeChainTypeRaw: String = "ethereum"
    
    @Published var supportedTokens: [SwiftSwapToken] = []

    init() {
        fetchActiveWallet()
    }

    func fetchActiveWallet() {
        Task {
            do {
                let wallets = try await KMPUseCaseDirect.shared.getAllWallets()
                
                // 使用保存的 activeWalletId 選擇正確的錢包
                let savedWalletId = UserDefaults.standard.string(forKey: "activeWalletId")
                let activeWallet = wallets.first(where: { $0.id == savedWalletId }) ?? wallets.first
                
                if let active = activeWallet {
                    await MainActor.run {
                        self.userAddress = active.address
                        self.activeChainTypeRaw = active.chainTypeRaw
                        // 錢包加載後，加載該鏈的 Token 列表
                        self.loadTokens()
                    }
                }
            } catch {
                print("Failed to fetch active wallet: \(error)")
            }
        }
    }
    
    func loadTokens() {
        isLoading = true
        Task {
            do {
                let chainType = getKMPChainType(activeChainTypeRaw)
                let tokens = try await KMPUseCaseDirect.shared.getSupportedTokens(chainType: chainType)
                
                await MainActor.run {
                    self.supportedTokens = tokens
                    self.isLoading = false
                    
                    // 預設選擇 (優先選原生幣或第一個)
                    if self.fromToken == nil {
                        // 嘗試找原生幣 (通常是列表第一個或沒有地址的)
                        self.fromToken = tokens.first(where: { $0.address.isEmpty }) ?? tokens.first
                    }
                }
            } catch {
                print("Failed to load tokens: \(error)")
                await MainActor.run { self.isLoading = false }
            }
        }
    }
    
    func fetchQuote() {
        guard let from = fromToken, let to = toToken else { return }
        
        isLoading = true
        error = nil
        
        Task {
            do {
                let chainType = self.getKMPChainType(activeChainTypeRaw)
                let result = try await KMPUseCaseDirect.shared.getSwapQuote(
                    fromAddress: userAddress,
                    fromToken: from.symbol,
                    toToken: to.symbol,
                    amount: amount,
                    chainType: chainType
                )
                
                await MainActor.run {
                    self.isLoading = false
                    self.quote = result
                }
            } catch {
                await MainActor.run {
                    self.isLoading = false
                    self.error = error.localizedDescription
                }
            }
        }
    }
    
    func executeSwapAsync() async {
        guard let quote = quote else { return }
        
        isLoading = true
        error = nil
        
        do {
            let txHash = try await KMPUseCaseDirect.shared.executeSwap(
                quote: quote,
                fromAddress: userAddress
            )
            
            print("Swap executed! Hash: \(txHash)")
            
            await MainActor.run {
                self.isLoading = false
            }
        } catch {
            await MainActor.run {
                self.isLoading = false
                self.error = error.localizedDescription
            }
        }
    }
    
    // Mapping from chainTypeRaw or id to ChainType
    private func getKMPChainType(_ chainTypeRaw: String) -> coreKmp.ChainType {
        let normalized = chainTypeRaw.lowercased()
        switch normalized {
        case "ethereum", "1": return .ethereum
        case "bsc", "56": return .bsc
        case "polygon", "137": return .polygon
        case "bitcoin": return .bitcoin
        case "litecoin": return .litecoin
        case "dogecoin": return .dogecoin
        case "solana": return .solana
        case "avalanche": return .avalanche
        case "fantom": return .fantom
        case "arbitrum": return .arbitrum
        case "optimism": return .optimism
        case "base": return .base
        default: return .ethereum
        }
    }
}
