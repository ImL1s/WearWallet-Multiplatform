import SwiftUI
import coreKmp

class PriceAlertsViewModel: ObservableObject {
    @Published var alerts: [PriceAlert] = []
    @Published var isLoading = false
    @Published var error: String?
    
    private let repository = DIContainer.shared.getPriceAlertRepository()
    
    func loadAlerts() {
        guard let repository = repository else {
            error = "Repository not available"
            return
        }
        
        isLoading = true
        Task {
            do {
                let result = try await repository.getAllAlerts()
                DispatchQueue.main.async {
                    // Handling KMP ResultWrapper
                     if let successResult = result as? ResultSuccess<NSArray>,
                        let list = successResult.data as? [PriceAlert] {
                         self.alerts = list
                     }
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

struct PriceAlertsView: View {
    @StateObject private var viewModel = PriceAlertsViewModel()
    
    var body: some View {
        Group {
            if viewModel.isLoading {
                ProgressView()
            } else if let error = viewModel.error {
                Text(error)
                    .foregroundColor(.red)
            } else if viewModel.alerts.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "bell.badge.fill")
                        .font(.system(size: 40))
                        .foregroundColor(.orange)
                    Text("No Alerts")
                        .font(.headline)
                    Text("Create an alert to get started")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            } else {
                List(viewModel.alerts, id: \.id) { alert in
                    HStack {
                        Image(systemName: "bell.fill")
                            .foregroundColor(alert.isEnabled ? .orange : .gray)
                        VStack(alignment: .leading) {
                            Text(alert.assetSymbol)
                                .font(.headline)
                            Text("\(alert.targetPrice)")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }
            }
        }
        .navigationTitle("Alerts")
        .onAppear {
            viewModel.loadAlerts()
        }
    }
}

#Preview {
    PriceAlertsView()
}
