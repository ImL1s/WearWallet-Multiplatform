import SwiftUI
import WatchKit
import PassKit

/**
 * NFC Payment View for watchOS
 * 
 * 實現加密貨幣 NFC 支付功能：
 * - Flexa 網絡整合 (40,000+ 商家)
 * - 即時 crypto-to-fiat 轉換
 * - Apple Pay 介面整合
 * - 離線簽名支援
 * 
 * Note: watchOS NFC 功能受限，主要透過 Apple Pay API
 * 和 iPhone 配套應用實現完整功能
 * 
 * Created: 2025-08-07
 */
struct NFCPaymentView: View {
    @StateObject private var viewModel = NFCPaymentViewModel()
    @State private var selectedPaymentMethod: PaymentMethod = .applePay
    @State private var showingQRCode = false
    @State private var showingTransactionSheet = false
    @State private var crownValue: Double = 0.5
    
    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                // Balance header
                balanceHeader
                
                // Payment method selector
                paymentMethodSelector
                
                // Quick payment amounts
                quickAmountSection
                
                // Recent merchants
                if !viewModel.recentMerchants.isEmpty {
                    recentMerchantsSection
                }
                
                // Payment button
                paymentButton
                
                // Transaction history
                if !viewModel.recentTransactions.isEmpty {
                    transactionHistorySection
                }
            }
            .padding(.horizontal, 8)
        }
        .navigationTitle("NFC 支付")
        .navigationBarTitleDisplayMode(.inline)
        .focusable()
        .digitalCrownRotation(
            $crownValue,
            from: 0,
            through: 100,
            by: 1,
            sensitivity: .low,
            isContinuous: false,
            isHapticFeedbackEnabled: true
        )
        .onChange(of: crownValue) { newValue in
            viewModel.paymentAmount = newValue
        }
        .sheet(isPresented: $showingQRCode) {
            PaymentQRCodeSheet(viewModel: viewModel)
        }
        .sheet(isPresented: $showingTransactionSheet) {
            TransactionDetailSheet(transaction: viewModel.lastTransaction)
        }
        .onAppear {
            viewModel.loadPaymentData()
        }
    }
    
    // MARK: - View Components
    
    private var balanceHeader: some View {
        VStack(spacing: 4) {
            Text("可用餘額")
                .font(.caption2)
                .foregroundColor(.secondary)
            
            HStack(spacing: 4) {
                Image(systemName: viewModel.selectedToken.icon)
                    .font(.title3)
                    .foregroundColor(viewModel.selectedToken.color)
                
                Text(viewModel.formattedBalance)
                    .font(.title3)
                    .fontWeight(.bold)
                
                Text(viewModel.selectedToken.symbol)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Text("≈ $\(viewModel.formattedFiatValue)")
                .font(.caption)
                .foregroundColor(.green)
        }
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity)
        .background(
            LinearGradient(
                colors: [Color.blue.opacity(0.2), Color.purple.opacity(0.1)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .cornerRadius(12)
    }
    
    private var paymentMethodSelector: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("支付方式")
                .font(.caption)
                .foregroundColor(.secondary)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(PaymentMethod.allCases, id: \.self) { method in
                        PaymentMethodChip(
                            method: method,
                            isSelected: selectedPaymentMethod == method
                        ) {
                            selectedPaymentMethod = method
                            viewModel.selectPaymentMethod(method)
                            WKInterfaceDevice.current().play(.click)
                        }
                    }
                }
            }
        }
    }
    
    private var quickAmountSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("快速金額")
                    .font(.caption)
                    .foregroundColor(.secondary)
                
                Spacer()
                
                Text("$\(viewModel.paymentAmount, specifier: "%.2f")")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.blue)
            }
            
            HStack(spacing: 8) {
                ForEach([5.0, 10.0, 20.0, 50.0], id: \.self) { amount in
                    QuickAmountButton(amount: amount) {
                        viewModel.paymentAmount = amount
                        WKInterfaceDevice.current().play(.click)
                    }
                }
            }
            
            // Custom amount slider
            Slider(value: $viewModel.paymentAmount, in: 0...100, step: 1)
                .accentColor(.blue)
        }
    }
    
    private var recentMerchantsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("最近商家")
                .font(.caption)
                .foregroundColor(.secondary)
            
            ForEach(viewModel.recentMerchants) { merchant in
                MerchantRow(merchant: merchant) {
                    viewModel.selectMerchant(merchant)
                }
            }
        }
    }
    
    private var paymentButton: some View {
        VStack(spacing: 8) {
            if selectedPaymentMethod == .applePay {
                // Apple Pay button
                ApplePayButton {
                    initiateApplePayPayment()
                }
                .frame(height: 44)
            } else if selectedPaymentMethod == .qrCode {
                // QR Code payment
                Button(action: {
                    showingQRCode = true
                }) {
                    HStack {
                        Image(systemName: "qrcode")
                        Text("顯示付款碼")
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
            } else {
                // NFC tap payment
                Button(action: {
                    initiateNFCPayment()
                }) {
                    HStack {
                        Image(systemName: "wave.3.right.circle")
                        Text("輕觸付款")
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(.green)
            }
            
            // Flexa network indicator
            if viewModel.isFlexaAvailable {
                HStack(spacing: 4) {
                    Image(systemName: "checkmark.shield.fill")
                        .font(.caption2)
                        .foregroundColor(.green)
                    Text("Flexa 網絡可用")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            }
        }
    }
    
    private var transactionHistorySection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("交易記錄")
                    .font(.caption)
                    .foregroundColor(.secondary)
                
                Spacer()
                
                Button("查看全部") {
                    // Navigate to full history
                }
                .font(.caption2)
            }
            
            ForEach(viewModel.recentTransactions.prefix(3)) { transaction in
                NFCTransactionRow(transaction: transaction) {
                    viewModel.lastTransaction = transaction
                    showingTransactionSheet = true
                }
            }
        }
    }
    
    // MARK: - Payment Methods
    
    private func initiateApplePayPayment() {
        guard PKPaymentAuthorizationController.canMakePayments() else {
            viewModel.showError("Apple Pay 不可用")
            return
        }
        
        Task {
            await viewModel.processApplePayPayment()
            WKInterfaceDevice.current().play(.success)
        }
    }
    
    private func initiateNFCPayment() {
        // Note: Direct NFC payment on watchOS is limited
        // This would trigger iPhone companion app for NFC
        Task {
            await viewModel.processNFCPayment()
            WKInterfaceDevice.current().play(.notification)
        }
    }
}

// MARK: - Supporting Views

struct PaymentMethodChip: View {
    let method: PaymentMethod
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Image(systemName: method.icon)
                    .font(.title3)
                Text(method.name)
                    .font(.caption2)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(isSelected ? method.color : Color.gray.opacity(0.2))
            .foregroundColor(isSelected ? .white : .primary)
            .cornerRadius(12)
        }
        .buttonStyle(.plain)
    }
}

struct QuickAmountButton: View {
    let amount: Double
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text("$\(Int(amount))")
                .font(.caption)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 4)
                .background(Color.blue.opacity(0.2))
                .cornerRadius(8)
        }
        .buttonStyle(.plain)
    }
}

struct MerchantRow: View {
    let merchant: Merchant
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack {
                Image(systemName: merchant.icon)
                    .font(.caption)
                    .foregroundColor(merchant.color)
                    .frame(width: 24, height: 24)
                    .background(merchant.color.opacity(0.2))
                    .cornerRadius(6)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(merchant.name)
                        .font(.caption2)
                        .fontWeight(.medium)
                    Text(merchant.category)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                if merchant.acceptsFlexa {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.caption2)
                        .foregroundColor(.green)
                }
            }
            .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
    }
}

struct NFCTransactionRow: View {
    let transaction: PaymentTransaction
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack {
                Image(systemName: transaction.type.icon)
                    .font(.caption)
                    .foregroundColor(transaction.type.color)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(transaction.merchant)
                        .font(.caption2)
                        .fontWeight(.medium)
                    Text(transaction.formattedDate)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                VStack(alignment: .trailing, spacing: 2) {
                    Text("-$\(transaction.amount, specifier: "%.2f")")
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(.red)
                    Text(transaction.token)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            }
            .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Apple Pay Button

// watchOS doesn't support PKPaymentButton directly, use a custom button instead
struct ApplePayButton: View {
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack {
                Image(systemName: "applelogo")
                    .font(.system(size: 14, weight: .medium))
                Text("Pay")
                    .font(.system(size: 14, weight: .medium))
            }
            .foregroundColor(.white)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(Color.black)
            .cornerRadius(4)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - QR Code Sheet

struct PaymentQRCodeSheet: View {
    @ObservedObject var viewModel: NFCPaymentViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var qrCodeImage: UIImage?
    
    var body: some View {
        VStack(spacing: 16) {
            Text("付款碼")
                .font(.headline)
            
            if let qrImage = qrCodeImage {
                Image(uiImage: qrImage)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 120, height: 120)
                    .cornerRadius(8)
            } else {
                ProgressView()
                    .frame(width: 120, height: 120)
            }
            
            VStack(spacing: 4) {
                Text("金額: $\(viewModel.paymentAmount, specifier: "%.2f")")
                    .font(.caption)
                    .fontWeight(.bold)
                
                Text("有效期: 5分鐘")
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            
            Button("完成") {
                dismiss()
            }
            .buttonStyle(.bordered)
        }
        .padding()
        .onAppear {
            generateQRCode()
        }
    }
    
    private func generateQRCode() {
        // Generate payment QR code
        let paymentData = viewModel.generatePaymentQRData()
        qrCodeImage = generateQRCodeImage(from: paymentData)
    }
    
    private func generateQRCodeImage(from string: String) -> UIImage? {
        // Use the custom QRCodeGenerator that doesn't rely on CoreImage
        return QRCodeGenerator.generateQRCode(
            from: string,
            size: CGSize(width: 120, height: 120)
        )
    }
}

// MARK: - Transaction Detail Sheet

struct TransactionDetailSheet: View {
    let transaction: PaymentTransaction?
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        if let transaction = transaction {
            VStack(spacing: 16) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.largeTitle)
                    .foregroundColor(.green)
                
                Text("交易詳情")
                    .font(.headline)
                
                VStack(alignment: .leading, spacing: 8) {
                    PaymentDetailRow(label: "商家", value: transaction.merchant)
                    PaymentDetailRow(label: "金額", value: String(format: "$%.2f", transaction.amount))
                    PaymentDetailRow(label: "代幣", value: transaction.token)
                    PaymentDetailRow(label: "手續費", value: String(format: "$%.2f", transaction.fee))
                    PaymentDetailRow(label: "時間", value: transaction.formattedDate)
                    PaymentDetailRow(label: "狀態", value: transaction.status, color: .green)
                }
                .font(.caption)
                
                Button("關閉") {
                    dismiss()
                }
                .buttonStyle(.bordered)
            }
            .padding()
        }
    }
}

struct PaymentDetailRow: View {
    let label: String
    let value: String
    var color: Color = .primary
    
    var body: some View {
        HStack {
            Text(label)
                .foregroundColor(.secondary)
            Spacer()
            Text(value)
                .fontWeight(.medium)
                .foregroundColor(color)
        }
    }
}

// MARK: - Preview

struct NFCPaymentView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationView {
            NFCPaymentView()
        }
    }
}