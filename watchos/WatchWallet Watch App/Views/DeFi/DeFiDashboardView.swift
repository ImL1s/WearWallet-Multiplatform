import SwiftUI
import WatchKit

/**
 * DeFi Dashboard View for watchOS
 *
 * 實現完整的 DeFi 功能，包括：
 * - Uniswap V3 交換
 * - Aave V3 借貸
 * - Compound V3 供應
 * - PancakeSwap 流動性
 * - 一鍵 DeFi 策略
 *
 * Created: 2025-08-07
 */
struct DeFiDashboardView: View {
    @StateObject private var viewModel = DeFiDashboardViewModel()
    @State private var selectedProtocol: DeFiProtocol = .uniswap
    @State private var showingStrategySheet = false
    @State private var crownValue: Double = 0.5
    
    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                // Header with total value
                totalValueHeader
                
                // Quick actions
                quickActionsSection
                
                // Protocol selector
                protocolSelector
                
                // Protocol specific content
                protocolContent
                
                // Active positions
                if !viewModel.activePositions.isEmpty {
                    activePositionsSection
                }
                
                // One-click strategies
                oneClickStrategiesSection
            }
            .padding(.horizontal, 8)
        }
        .navigationTitle("DeFi")
        .navigationBarTitleDisplayMode(.inline)
        .focusable()
        .digitalCrownRotation(
            $crownValue,
            from: 0,
            through: 1,
            by: 0.01,
            sensitivity: .low,
            isContinuous: false,
            isHapticFeedbackEnabled: true
        )
        .onChange(of: crownValue) { newValue in
            // Adjust values based on crown rotation
            viewModel.adjustAmount(by: newValue)
        }
        .sheet(isPresented: $showingStrategySheet) {
            OneClickStrategySheet(viewModel: viewModel)
        }
        .onAppear {
            viewModel.loadDeFiData()
        }
    }
    
    // MARK: - View Components
    
    private var totalValueHeader: some View {
        VStack(spacing: 4) {
            Text("Total DeFi Value")
                .font(.caption2)
                .foregroundColor(.secondary)
            
            Text(viewModel.formattedTotalValue)
                .font(.title3)
                .fontWeight(.bold)
                .foregroundColor(.green)
            
            HStack(spacing: 4) {
                Image(systemName: viewModel.totalValueChange >= 0 ? "arrow.up.right" : "arrow.down.right")
                    .font(.caption2)
                Text("\(abs(viewModel.totalValueChange), specifier: "%.2f")%")
                    .font(.caption2)
            }
            .foregroundColor(viewModel.totalValueChange >= 0 ? .green : .red)
        }
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity)
        .background(Color.gray.opacity(0.1))
        .cornerRadius(12)
    }
    
    private var quickActionsSection: some View {
        HStack(spacing: 8) {
            QuickActionButton(
                title: "Swap",
                icon: "arrow.left.arrow.right",
                color: .blue
            ) {
                viewModel.initiateSwap()
            }
            
            QuickActionButton(
                title: "Supply",
                icon: "plus.circle",
                color: .green
            ) {
                viewModel.initiateSupply()
            }
            
            QuickActionButton(
                title: "Borrow",
                icon: "minus.circle",
                color: .orange
            ) {
                viewModel.initiateBorrow()
            }
        }
    }
    
    private var protocolSelector: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(DeFiProtocol.allCases, id: \.self) { defiProtocol in
                    ProtocolChip(
                        protocol: defiProtocol,
                        isSelected: selectedProtocol == defiProtocol
                    ) {
                        selectedProtocol = defiProtocol
                        viewModel.selectProtocol(defiProtocol)
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }
    
    private var protocolContent: some View {
        Group {
            switch selectedProtocol {
            case .uniswap:
                UniswapView(viewModel: viewModel)
            case .aave:
                AaveView(viewModel: viewModel)
            case .compound:
                CompoundView(viewModel: viewModel)
            case .pancakeswap:
                PancakeSwapView(viewModel: viewModel)
            case .pendle:
                PendleView(viewModel: viewModel)
            }
        }
    }
    
    private var activePositionsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Active Positions")
                .font(.caption)
                .foregroundColor(.secondary)
            
            ForEach(viewModel.activePositions) { position in
                PositionCard(position: position) {
                    viewModel.managePosition(position)
                }
            }
        }
    }
    
    private var oneClickStrategiesSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("One-Click Strategies")
                    .font(.caption)
                    .foregroundColor(.secondary)
                
                Spacer()
                
                Button(action: { showingStrategySheet = true }) {
                    Image(systemName: "plus.circle")
                        .font(.caption)
                }
            }
            
            ForEach(viewModel.availableStrategies) { strategy in
                StrategyCard(strategy: strategy) {
                    viewModel.executeStrategy(strategy)
                }
            }
        }
    }
}

// MARK: - Supporting Views

struct QuickActionButton: View {
    let title: String
    let icon: String
    let color: Color
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Image(systemName: icon)
                    .font(.title3)
                Text(title)
                    .font(.caption2)
            }
            .frame(maxWidth: .infinity)
            .foregroundColor(color)
        }
        .buttonStyle(.plain)
        .padding(6)
        .background(color.opacity(0.15))
        .cornerRadius(8)
    }
}

struct ProtocolChip: View {
    let `protocol`: DeFiProtocol
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Image(systemName: `protocol`.icon)
                    .font(.caption2)
                Text(`protocol`.name)
                    .font(.caption2)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(isSelected ? Color.blue : Color.gray.opacity(0.2))
            .foregroundColor(isSelected ? .white : .primary)
            .cornerRadius(12)
        }
        .buttonStyle(.plain)
    }
}

struct PositionCard: View {
    let position: DeFiPosition
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(position.protocol)
                        .font(.caption2)
                        .fontWeight(.semibold)
                    Spacer()
                    Text(position.type.rawValue)
                        .font(.caption2)
                        .padding(.horizontal, 4)
                        .padding(.vertical, 1)
                        .background(position.type.color.opacity(0.2))
                        .foregroundColor(position.type.color)
                        .cornerRadius(4)
                }
                
                Text(position.description)
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
                
                HStack {
                    Text("Value: \(position.formattedValue)")
                        .font(.caption2)
                        .fontWeight(.medium)
                    Spacer()
                    Text("APY: \(position.apy, specifier: "%.2f")%")
                        .font(.caption2)
                        .foregroundColor(.green)
                }
            }
            .padding(8)
            .background(Color.gray.opacity(0.1))
            .cornerRadius(8)
        }
        .buttonStyle(.plain)
    }
}

struct StrategyCard: View {
    let strategy: DeFiStrategy
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Image(systemName: strategy.icon)
                        .font(.caption)
                        .foregroundColor(strategy.riskColor)
                    Text(strategy.name)
                        .font(.caption2)
                        .fontWeight(.semibold)
                    Spacer()
                    Text("\(strategy.estimatedAPY, specifier: "%.1f")% APY")
                        .font(.caption2)
                        .foregroundColor(.green)
                }
                
                Text(strategy.description)
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .lineLimit(2)
                
                HStack {
                    RiskIndicator(level: strategy.riskLevel)
                    Spacer()
                    Text("Min: \(strategy.minimumAmount)")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            }
            .padding(8)
            .background(Color.gray.opacity(0.1))
            .cornerRadius(8)
        }
        .buttonStyle(.plain)
    }
}

struct RiskIndicator: View {
    let level: RiskLevel
    
    var body: some View {
        HStack(spacing: 2) {
            ForEach(0..<3) { index in
                Circle()
                    .fill(index < level.rawValue ? level.color : Color.gray.opacity(0.3))
                    .frame(width: 4, height: 4)
            }
            Text(level.text)
                .font(.caption2)
                .foregroundColor(level.color)
        }
    }
}

// MARK: - Protocol Specific Views

struct UniswapView: View {
    @ObservedObject var viewModel: DeFiDashboardViewModel
    
    var body: some View {
        VStack(spacing: 8) {
            // Swap interface
            SwapInterface(
                fromToken: viewModel.swapFromToken,
                toToken: viewModel.swapToToken,
                amount: viewModel.swapAmount,
                estimatedOutput: viewModel.estimatedSwapOutput,
                onSwap: { viewModel.executeSwap() }
            )
            
            // Liquidity pools
            if !viewModel.liquidityPools.isEmpty {
                LiquidityPoolsList(pools: viewModel.liquidityPools)
            }
        }
    }
}

struct AaveView: View {
    @ObservedObject var viewModel: DeFiDashboardViewModel
    
    var body: some View {
        VStack(spacing: 8) {
            // Supply/Borrow stats
            HStack(spacing: 8) {
                StatCard(
                    title: "Supplied",
                    value: viewModel.totalSupplied,
                    icon: "arrow.up.circle",
                    color: .green
                )
                StatCard(
                    title: "Borrowed",
                    value: viewModel.totalBorrowed,
                    icon: "arrow.down.circle",
                    color: .orange
                )
            }
            
            // Health factor
            HealthFactorIndicator(healthFactor: viewModel.healthFactor)
            
            // Markets
            MarketsList(markets: viewModel.aaveMarkets)
        }
    }
}

struct CompoundView: View {
    @ObservedObject var viewModel: DeFiDashboardViewModel
    
    var body: some View {
        VStack(spacing: 8) {
            // Earn APY overview
            EarnAPYCard(
                asset: viewModel.selectedCompoundAsset,
                supplyAPY: viewModel.compoundSupplyAPY,
                borrowAPY: viewModel.compoundBorrowAPY
            )
            
            // Collateral factor
            CollateralFactorBar(factor: viewModel.collateralFactor)
            
            // Actions
            CompoundActions(viewModel: viewModel)
        }
    }
}

struct PancakeSwapView: View {
    @ObservedObject var viewModel: DeFiDashboardViewModel
    
    var body: some View {
        VStack(spacing: 8) {
            // Yield farming opportunities
            FarmingOpportunities(farms: viewModel.pancakeFarms)
            
            // Syrup pools
            SyrupPools(pools: viewModel.syrupPools)
            
            // CAKE staking
            CAKEStakingCard(
                stakedAmount: viewModel.stakedCAKE,
                apy: viewModel.cakeStakingAPY
            )
        }
    }
}

struct PendleView: View {
    @ObservedObject var viewModel: DeFiDashboardViewModel
    
    var body: some View {
        VStack(spacing: 8) {
            // PT/YT markets
            PendleMarkets(markets: viewModel.pendleMarkets)
            
            // vePENDLE position
            VePendleCard(
                lockedAmount: viewModel.lockedPENDLE,
                unlockDate: viewModel.pendleUnlockDate,
                boost: viewModel.pendleBoost
            )
        }
    }
}

// MARK: - One-Click Strategy Sheet

struct OneClickStrategySheet: View {
    @ObservedObject var viewModel: DeFiDashboardViewModel
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 12) {
                    ForEach(DeFiStrategy.presetStrategies) { strategy in
                        StrategyDetailCard(strategy: strategy) {
                            viewModel.executeStrategy(strategy)
                            dismiss()
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("DeFi Strategies")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }
}

// MARK: - Preview

struct DeFiDashboardView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationView {
            DeFiDashboardView()
        }
    }
}