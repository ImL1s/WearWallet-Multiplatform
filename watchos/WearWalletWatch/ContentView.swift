import SwiftUI
import coreKmp

struct ContentView: View {
    @State private var wallets: [Wallet] = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    
    let cryptoProvider = CryptoProvider()
    let secureStorage = SecureStorage()
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 10) {
                    // Header
                    Text("WearWallet")
                        .font(.headline)
                        .padding(.top)
                    
                    // Platform info
                    Text("Running on: \(SharedPlatformKt.platform())")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .padding(.horizontal)
                    
                    // Keystone Hardware Wallet Button
                    NavigationLink(destination: KeystoneView()) {
                        HStack {
                            Image(systemName: "lock.shield")
                                .foregroundColor(.orange)
                            Text("Keystone Wallet")
                                .font(.caption)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption)
                        }
                        .padding()
                        .background(Color.orange.opacity(0.1))
                        .cornerRadius(10)
                    }
                    .buttonStyle(PlainButtonStyle())
                    .padding(.horizontal)
                    
                    // Wallet List
                    if wallets.isEmpty {
                        EmptyWalletView {
                            createNewWallet()
                        }
                    } else {
                        ForEach(wallets, id: \.id) { wallet in
                            WalletCardView(wallet: wallet)
                        }
                    }
                    
                    // Error message
                    if let error = errorMessage {
                        Text(error)
                            .font(.caption)
                            .foregroundColor(.red)
                            .padding()
                    }
                }
            }
            .overlay {
                if isLoading {
                    ProgressView()
                        .background(Color.black.opacity(0.8))
                        .cornerRadius(10)
                }
            }
        }
    }
    
    private func createNewWallet() {
        isLoading = true
        errorMessage = nil
        
        Task {
            do {
                // Generate mnemonic
                let mnemonic = cryptoProvider.generateMnemonic(strength: 128)
                
                // Derive private key
                let privateKey = cryptoProvider.derivePrivateKey(
                    mnemonic: mnemonic,
                    path: "m/44'/60'/0'/0/0"
                )
                
                // Get address
                let address = cryptoProvider.getAddress(
                    privateKey: privateKey,
                    coinType: 60 // Ethereum
                )
                
                // Create wallet object
                let wallet = Wallet(
                    id: UUID().uuidString,
                    name: "Wallet 1",
                    address: address,
                    chainId: 1,
                    createdAt: KotlinLong(value: Int64(Date().timeIntervalSince1970 * 1000))
                )
                
                // Save to secure storage
                await secureStorage.savePrivateKey(
                    walletId: wallet.id,
                    privateKey: privateKey
                )
                
                await MainActor.run {
                    wallets.append(wallet)
                    isLoading = false
                }
            } catch {
                await MainActor.run {
                    errorMessage = "Failed to create wallet: \(error.localizedDescription)"
                    isLoading = false
                }
            }
        }
    }
}

struct EmptyWalletView: View {
    let onCreateWallet: () -> Void
    
    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "wallet.pass")
                .font(.largeTitle)
                .foregroundColor(.blue)
            
            Text("No Wallets")
                .font(.headline)
            
            Text("Create your first wallet")
                .font(.caption)
                .foregroundColor(.secondary)
            
            Button(action: onCreateWallet) {
                Label("Create Wallet", systemImage: "plus.circle.fill")
            }
            .buttonStyle(.borderedProminent)
        }
        .padding()
    }
}

struct WalletCardView: View {
    let wallet: Wallet
    
    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(wallet.name)
                .font(.headline)
            
            Text(formatAddress(wallet.address))
                .font(.caption)
                .foregroundColor(.secondary)
            
            HStack {
                Text("Chain ID: \(wallet.chainId)")
                    .font(.caption2)
                Spacer()
                Text("0.0 ETH")
                    .font(.caption)
                    .bold()
            }
        }
        .padding()
        .background(Color.blue.opacity(0.1))
        .cornerRadius(10)
        .padding(.horizontal)
    }
    
    private func formatAddress(_ address: String) -> String {
        guard address.count > 10 else { return address }
        let start = address.prefix(6)
        let end = address.suffix(4)
        return "\(start)...\(end)"
    }
}

#Preview {
    ContentView()
}