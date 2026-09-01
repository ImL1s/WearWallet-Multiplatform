import SwiftUI

enum DeFiProtocol: String, CaseIterable, Identifiable {
    case uniswap = "Uniswap"
    case aave = "Aave"
    case compound = "Compound"
    case pancakeswap = "PancakeSwap"
    case pendle = "Pendle"
    
    var id: String { rawValue }
    
    var name: String { rawValue }
    
    var icon: String {
        switch self {
        case .uniswap: return "arrow.left.arrow.right.circle"
        case .aave: return "bitcoinsign.circle"
        case .compound: return "c.circle"
        case .pancakeswap: return "p.circle"
        case .pendle: return "p.circle.fill"
        }
    }
}

struct DeFiPosition: Identifiable {
    let id: String
    let `protocol`: String
    let type: PositionType
    let description: String
    let amount: Double
    let symbol: String
    let usdValue: Double
    let apy: Double
    
    var formattedValue: String {
        return String(format: "$%.2f", usdValue)
    }
    
    enum PositionType: String {
        case liquidity = "LP"
        case supply = "LENDING"
        case borrow = "DEBT"
        case staking = "STAKE"
        
        var color: Color {
            switch self {
            case .liquidity: return .blue
            case .supply: return .green
            case .borrow: return .orange
            case .staking: return .purple
            }
        }
    }
}

enum RiskLevel: Int {
    case low = 1
    case medium = 2
    case high = 3
    
    var text: String {
        switch self {
        case .low: return "低"
        case .medium: return "中"
        case .high: return "高"
        }
    }
    
    var color: Color {
        switch self {
        case .low: return .green
        case .medium: return .yellow
        case .high: return .red
        }
    }
}

struct DeFiStrategy: Identifiable {
    let id: String
    let name: String
    let description: String
    let icon: String
    let estimatedAPY: Double
    let riskLevel: RiskLevel
    let minimumAmount: String
    
    var riskColor: Color { riskLevel.color }
    
    static let presetStrategies: [DeFiStrategy] = [
        DeFiStrategy(id: "1", name: "穩定幣農耕", description: "低風險穩定幣收益回報", icon: "shield.fill", estimatedAPY: 8.5, riskLevel: .low, minimumAmount: "100 USDC"),
        DeFiStrategy(id: "2", name: "以太坊質押", description: "Lido/Rocket Pool 收益平衡", icon: "diamond.fill", estimatedAPY: 4.2, riskLevel: .low, minimumAmount: "0.1 ETH"),
        DeFiStrategy(id: "3", name: "高收益策略", description: "槓桿借貸池進階策略", icon: "bolt.fill", estimatedAPY: 24.5, riskLevel: .high, minimumAmount: "500 USDC")
    ]
}
