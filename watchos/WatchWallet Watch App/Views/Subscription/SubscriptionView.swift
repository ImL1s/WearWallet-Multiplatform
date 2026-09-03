import SwiftUI
import WatchKit
import StoreKit

/**
 * Subscription View for watchOS
 * 
 * 實現訂閱管理功能：
 * - Pro 功能訂閱
 * - 功能對比展示
 * - 訂閱狀態管理
 * - In-App Purchase 整合
 * 
 * Created: 2025-08-07
 */
struct SubscriptionView: View {
    @StateObject private var viewModel = SubscriptionViewModel()
    @State private var selectedPlan: SubscriptionPlan = .monthly
    @State private var showingPurchaseSheet = false
    @State private var showingRestoreSheet = false
    
    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                // Current subscription status
                subscriptionStatusHeader
                
                // Pro features
                proFeaturesSection
                
                // Subscription plans
                subscriptionPlansSection
                
                // Purchase button
                purchaseButton
                
                // Restore purchases
                restoreButton
                
                // Terms and conditions
                termsSection
            }
            .padding(.horizontal, 8)
        }
        .navigationTitle("WearWallet Pro")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showingPurchaseSheet) {
            PurchaseConfirmationSheet(
                viewModel: viewModel,
                plan: selectedPlan
            )
        }
        .sheet(isPresented: $showingRestoreSheet) {
            RestorePurchasesSheet(viewModel: viewModel)
        }
        .onAppear {
            viewModel.loadSubscriptionStatus()
        }
    }
    
    // MARK: - View Components
    
    private var subscriptionStatusHeader: some View {
        VStack(spacing: 4) {
            if viewModel.isSubscribed {
                HStack {
                    Image(systemName: "crown.fill")
                        .font(.title3)
                        .foregroundColor(.yellow)
                    Text("Pro 會員")
                        .font(.caption)
                        .fontWeight(.bold)
                }
                
                Text("有效期至: \(viewModel.expirationDate)")
                    .font(.caption2)
                    .foregroundColor(.secondary)
                
                if viewModel.willAutoRenew {
                    Label("自動續訂", systemImage: "arrow.clockwise")
                        .font(.caption2)
                        .foregroundColor(.green)
                } else {
                    Label("不會自動續訂", systemImage: "xmark.circle")
                        .font(.caption2)
                        .foregroundColor(.orange)
                }
            } else {
                VStack(spacing: 4) {
                    Image(systemName: "lock.circle")
                        .font(.largeTitle)
                        .foregroundColor(.gray)
                    
                    Text("升級到 Pro")
                        .font(.caption)
                        .fontWeight(.bold)
                    
                    Text("解鎖所有高級功能")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            }
        }
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity)
        .background(
            LinearGradient(
                colors: viewModel.isSubscribed ?
                    [Color.yellow.opacity(0.3), Color.orange.opacity(0.2)] :
                    [Color.gray.opacity(0.2), Color.gray.opacity(0.1)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .cornerRadius(12)
    }
    
    private var proFeaturesSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Pro 功能")
                .font(.caption)
                .foregroundColor(.secondary)
            
            ForEach(ProFeature.allFeatures, id: \.id) { feature in
                FeatureRow(
                    feature: feature,
                    isUnlocked: viewModel.isSubscribed || feature.isFree
                )
            }
        }
    }
    
    private var subscriptionPlansSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("選擇方案")
                    .font(.caption)
                    .foregroundColor(.secondary)
                
                Spacer()
                
                if let savings = viewModel.calculateSavings(for: selectedPlan) {
                    Text("節省 \(savings)")
                        .font(.caption2)
                        .foregroundColor(.green)
                }
            }
            
            ForEach(SubscriptionPlan.allCases, id: \.self) { plan in
                PlanCard(
                    plan: plan,
                    isSelected: selectedPlan == plan,
                    price: viewModel.getPrice(for: plan)
                ) {
                    selectedPlan = plan
                    WKInterfaceDevice.current().play(.click)
                }
            }
        }
    }
    
    private var purchaseButton: some View {
        Button(action: {
            if viewModel.isSubscribed {
                // Manage subscription
                viewModel.manageSubscription()
            } else {
                showingPurchaseSheet = true
            }
        }) {
            HStack {
                if viewModel.isProcessing {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .scaleEffect(0.8)
                } else {
                    Image(systemName: viewModel.isSubscribed ? "crown.fill" : "creditcard")
                    Text(viewModel.isSubscribed ? "管理訂閱" : "訂閱 Pro")
                }
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .tint(viewModel.isSubscribed ? .orange : .blue)
        .disabled(viewModel.isProcessing)
    }
    
    private var restoreButton: some View {
        Button(action: {
            showingRestoreSheet = true
        }) {
            Text("恢復購買")
                .font(.caption)
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
    }
    
    private var termsSection: some View {
        VStack(spacing: 4) {
            Text("訂閱將自動續訂，除非在當前期間結束前至少24小時取消。")
                .font(.caption2)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            
            HStack(spacing: 8) {
                Button("服務條款") {
                    viewModel.openTermsOfService()
                }
                .font(.caption2)
                
                Text("•")
                    .font(.caption2)
                    .foregroundColor(.secondary)
                
                Button("隱私政策") {
                    viewModel.openPrivacyPolicy()
                }
                .font(.caption2)
            }
        }
        .padding(.vertical, 8)
    }
}

// MARK: - Supporting Views

struct FeatureRow: View {
    let feature: ProFeature
    let isUnlocked: Bool
    
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: isUnlocked ? "checkmark.circle.fill" : "lock.circle")
                .font(.caption)
                .foregroundColor(isUnlocked ? .green : .gray)
            
            VStack(alignment: .leading, spacing: 2) {
                Text(feature.name)
                    .font(.caption2)
                    .fontWeight(.medium)
                    .foregroundColor(isUnlocked ? .primary : .secondary)
                
                if let description = feature.description {
                    Text(description)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
            }
            
            Spacer()
            
            if feature.isNew {
                Text("NEW")
                    .font(.system(size: 8))
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .padding(.horizontal, 4)
                    .padding(.vertical, 1)
                    .background(Color.red)
                    .cornerRadius(4)
            }
        }
        .opacity(isUnlocked ? 1.0 : 0.6)
    }
}

struct PlanCard: View {
    let plan: SubscriptionPlan
    let isSelected: Bool
    let price: String
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(plan.name)
                        .font(.caption)
                        .fontWeight(.semibold)
                    
                    if plan.isBestValue {
                        Text("最優惠")
                            .font(.system(size: 8))
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                            .padding(.horizontal, 4)
                            .padding(.vertical, 1)
                            .background(Color.green)
                            .cornerRadius(4)
                    }
                    
                    Spacer()
                    
                    Text(price)
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(isSelected ? .white : .primary)
                }
                
                Text(plan.description)
                    .font(.caption2)
                    .foregroundColor(isSelected ? .white.opacity(0.9) : .secondary)
                
                if let unitPrice = plan.unitPrice {
                    Text(unitPrice)
                        .font(.caption2)
                        .foregroundColor(isSelected ? .white.opacity(0.8) : .secondary)
                }
            }
            .padding(8)
            .background(isSelected ? plan.color : Color.gray.opacity(0.1))
            .foregroundColor(isSelected ? .white : .primary)
            .cornerRadius(8)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(isSelected ? plan.color : Color.clear, lineWidth: 2)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Purchase Confirmation Sheet

struct PurchaseConfirmationSheet: View {
    @ObservedObject var viewModel: SubscriptionViewModel
    let plan: SubscriptionPlan
    @Environment(\.dismiss) private var dismiss
    @State private var isProcessing = false
    
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "crown.fill")
                .font(.largeTitle)
                .foregroundColor(.yellow)
            
            Text("確認訂閱")
                .font(.headline)
            
            VStack(spacing: 8) {
                Text(plan.name)
                    .font(.caption)
                    .fontWeight(.bold)
                
                Text(viewModel.getPrice(for: plan))
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(.blue)
                
                Text(plan.description)
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            
            if isProcessing {
                ProgressView("處理中...")
                    .progressViewStyle(.circular)
            } else {
                VStack(spacing: 8) {
                    Button(action: {
                        purchaseSubscription()
                    }) {
                        Label("確認購買", systemImage: "checkmark.circle")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    
                    Button("取消") {
                        dismiss()
                    }
                    .buttonStyle(.bordered)
                }
            }
        }
        .padding()
    }
    
    private func purchaseSubscription() {
        isProcessing = true
        
        Task {
            do {
                try await viewModel.purchase(plan)
                WKInterfaceDevice.current().play(.success)
                dismiss()
            } catch {
                WKInterfaceDevice.current().play(.failure)
                // Show error
            }
            isProcessing = false
        }
    }
}

// MARK: - Restore Purchases Sheet

struct RestorePurchasesSheet: View {
    @ObservedObject var viewModel: SubscriptionViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var isRestoring = false
    @State private var restoreResult: RestoreResult?
    
    var body: some View {
        VStack(spacing: 16) {
            if let result = restoreResult {
                // Show result
                Image(systemName: result.success ? "checkmark.circle.fill" : "xmark.circle.fill")
                    .font(.largeTitle)
                    .foregroundColor(result.success ? .green : .red)
                
                Text(result.message)
                    .font(.caption)
                    .multilineTextAlignment(.center)
                
                Button("完成") {
                    dismiss()
                }
                .buttonStyle(.bordered)
            } else if isRestoring {
                ProgressView("恢復購買中...")
                    .progressViewStyle(.circular)
            } else {
                Image(systemName: "arrow.clockwise.circle")
                    .font(.largeTitle)
                    .foregroundColor(.blue)
                
                Text("恢復購買")
                    .font(.headline)
                
                Text("恢復您之前購買的訂閱")
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                
                VStack(spacing: 8) {
                    Button(action: {
                        restorePurchases()
                    }) {
                        Label("開始恢復", systemImage: "arrow.clockwise")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    
                    Button("取消") {
                        dismiss()
                    }
                    .buttonStyle(.bordered)
                }
            }
        }
        .padding()
    }
    
    private func restorePurchases() {
        isRestoring = true
        
        Task {
            do {
                let restored = try await viewModel.restorePurchases()
                restoreResult = RestoreResult(
                    success: restored,
                    message: restored ? "成功恢復您的訂閱" : "沒有找到可恢復的購買"
                )
                
                if restored {
                    WKInterfaceDevice.current().play(.success)
                }
            } catch {
                restoreResult = RestoreResult(
                    success: false,
                    message: "恢復失敗: \(error.localizedDescription)"
                )
                WKInterfaceDevice.current().play(.failure)
            }
            isRestoring = false
        }
    }
    
    struct RestoreResult {
        let success: Bool
        let message: String
    }
}

// MARK: - Preview

struct SubscriptionView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationView {
            SubscriptionView()
        }
    }
}