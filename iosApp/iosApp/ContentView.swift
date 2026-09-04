import SwiftUI

struct ContentView: View {
    @EnvironmentObject var connectivityManager: WatchConnectivityManager
    @State private var showingScanner = false
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Image(systemName: "applewatch.watchface")
                    .font(.system(size: 60))
                    .foregroundColor(connectivityManager.isReachable ? .green : .gray)
                
                Text(connectivityManager.isReachable ? "watch_connected" : "watch_waiting")
                    .font(.headline)
                
                if let lastMsg = connectivityManager.lastReceivedMessage["type"] as? String {
                    Text(String(format: NSLocalizedString("latest_message", comment: ""), lastMsg))
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                Divider()
                
                Button(action: {
                    showingScanner = true
                }) {
                    Label("scan_keystone_qr", systemImage: "qrcode.viewfinder")
                        .font(.headline)
                        .foregroundColor(.white)
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(Color.blue)
                        .cornerRadius(10)
                }
                
                NavigationLink(destination: AddressBookView()) {
                    Label("address_book_management", systemImage: "person.crop.circle")
                        .font(.headline)
                        .foregroundColor(.blue)
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(Color.blue.opacity(0.1))
                        .cornerRadius(10)
                }
                
                Spacer()
            }
            .padding()
            .navigationTitle("WearWallet Companion")
            .sheet(isPresented: Binding(
                get: { connectivityManager.showKeystoneScanner || showingScanner },
                set: { val in
                    if !val {
                        connectivityManager.showKeystoneScanner = false
                        showingScanner = false
                    }
                }
            )) {
                KeystoneScannerView()
            }
        }
    }
}
