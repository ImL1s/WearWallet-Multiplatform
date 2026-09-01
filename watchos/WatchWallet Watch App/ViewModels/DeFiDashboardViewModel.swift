import Foundation
import SwiftUI
import Combine

@MainActor
class DeFiDashboardViewModel: ObservableObject {
    @Published var totalValue: Double = 12500.50
    @Published var totalValueChange: Double = 2.45
    @Published var activePositions: [DeFiPosition] = []
    @Published var availableStrategies: [DeFiStrategy] = DeFiStrategy.presetStrategies
    
    // Uniswap Mock Data
    @Published var swapFromToken: String = "ETH"
    @Published var swapToToken: String = "USDC"
    @Published var swapAmount: String = "1.0"
    @Published var estimatedSwapOutput: String = "2500.0"
    @Published var liquidityPools: [String] = ["ETH/USDC", "WBTC/ETH"]
    
    // Aave Mock Data
    @Published var totalSupplied: String = "$10,000"
    @Published var totalBorrowed: String = "$2,000"
    @Published var healthFactor: Double = 1.85
    @Published var aaveMarkets: [String] = ["USDC", "ETH", "DAI"]
    
    // Compound Mock Data
    @Published var selectedCompoundAsset: String = "USDC"
    @Published var compoundSupplyAPY: Double = 5.2
    @Published var compoundBorrowAPY: Double = 7.1
    @Published var collateralFactor: Double = 0.8
    
    // PancakeSwap Mock Data
    @Published var pancakeFarms: [String] = ["CAKE-BNB", "USDT-BUSD"]
    @Published var syrupPools: [String] = ["Stake CAKE earn CAKE"]
    @Published var stakedCAKE: String = "100.0"
    @Published var cakeStakingAPY: Double = 15.5
    
    // Pendle Mock Data
    @Published var pendleMarkets: [String] = ["stETH", "rETH"]
    @Published var lockedPENDLE: String = "500"
    @Published var pendleUnlockDate: Date = Date().addingTimeInterval(86400 * 365)
    @Published var pendleBoost: Double = 1.5
    
    var formattedTotalValue: String {
        return String(format: "$%.2f", totalValue)
    }
    
    func loadDeFiData() {
        activePositions = [
            DeFiPosition(id: "1", protocol: "Uniswap", type: .liquidity, description: "ETH/USDC LPV3", amount: 1.5, symbol: "LP", usdValue: 5400.0, apy: 12.5),
            DeFiPosition(id: "2", protocol: "Aave", type: .supply, description: "Supplied USDC", amount: 5000.0, symbol: "USDC", usdValue: 5000.0, apy: 5.2)
        ]
    }
    
    func adjustAmount(by value: Double) {
        // Mock crown adjustment
    }
    
    func initiateSwap() {}
    func initiateSupply() {}
    func initiateBorrow() {}
    func selectProtocol(_ proto: DeFiProtocol) {}
    func managePosition(_ position: DeFiPosition) {}
    func executeStrategy(_ strategy: DeFiStrategy) {}
    func executeSwap() {}
    
    // Static helpers for subviews
}

// Marker for missing views in DeFiDashboardView.swift
struct SwapInterface: View { var fromToken: String; var toToken: String; var amount: String; var estimatedOutput: String; var onSwap: () -> Void; var body: some View { Text("Swap Interface") } }
struct LiquidityPoolsList: View { var pools: [String]; var body: some View { Text("Pools List") } }

struct HealthFactorIndicator: View { var healthFactor: Double; var body: some View { Text("Health factor: \(healthFactor)") } }
struct MarketsList: View { var markets: [String]; var body: some View { Text("Markets") } }
struct EarnAPYCard: View { var asset: String; var supplyAPY: Double; var borrowAPY: Double; var body: some View { Text("\(asset) APY: \(supplyAPY)%") } }
struct CollateralFactorBar: View { var factor: Double; var body: some View { Text("Collateral Factor") } }
struct CompoundActions: View { var viewModel: DeFiDashboardViewModel; var body: some View { Text("Compound Actions") } }
struct FarmingOpportunities: View { var farms: [String]; var body: some View { Text("Farms") } }
struct SyrupPools: View { var pools: [String]; var body: some View { Text("Syrup Pools") } }
struct CAKEStakingCard: View { var stakedAmount: String; var apy: Double; var body: some View { Text("Staked CAKE") } }
struct PendleMarkets: View { var markets: [String]; var body: some View { Text("Pendle Markets") } }
struct VePendleCard: View { var lockedAmount: String; var unlockDate: Date; var boost: Double; var body: some View { Text("vePENDLE") } }
struct StrategyDetailCard: View { var strategy: DeFiStrategy; var action: () -> Void; var body: some View { Text(strategy.name) } }
