import SwiftUI
import coreKmp

class NFTListViewModel: ObservableObject {
    @Published var nfts: [NftToken] = []
    @Published var isLoading = false
    @Published var error: String?
    
    private let repository = DIContainer.shared.getNftRepository()
    
    func loadNFTs() {
        guard let repository = repository else {
            error = "Repository not available"
            return
        }
        
        isLoading = true
        Task {
            do {
                let result = try await repository.getAllNfts()
                DispatchQueue.main.async {
                    if let successResult = result as? ResultSuccess<NSArray> {
                        if let list = successResult.data as? [NftToken] {
                            self.nfts = list
                        }
                    }
                    // Fallback for direct list if useCase returns list directly (check signature)
                    // Repository returns Result<List<NftToken>>
                    
                    self.isLoading = false
                }
            } catch {
                DispatchQueue.main.async {
                    self.error = error.localizedDescription
                    self.isLoading = false
                }
            }
        }
    }
}

struct NFTListView: View {
    @StateObject private var viewModel = NFTListViewModel()
    
    var body: some View {
        Group {
            if viewModel.isLoading {
                ProgressView()
            } else if let error = viewModel.error {
                Text(error)
                    .foregroundColor(.red)
            } else if viewModel.nfts.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "photo.stack")
                        .font(.system(size: 40))
                        .foregroundColor(.purple)
                    Text("No NFTs Found")
                        .font(.headline)
                    Text("Your collection is empty")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            } else {
                List(viewModel.nfts, id: \.id) { nft in
                    HStack {
                        // Placeholder image or AsyncImage if URL valid
                        Image(systemName: "photo")
                            .foregroundColor(.purple)
                        VStack(alignment: .leading) {
                            Text(nft.name ?? "Unknown NFT")
                                .font(.headline)
                            Text(nft.collectionName ?? "")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }
            }
        }
        .navigationTitle("NFTs")
        .onAppear {
            viewModel.loadNFTs()
        }
    }
}

#Preview {
    NFTListView()
}
