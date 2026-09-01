import Foundation
import SwiftUI
import HealthKit
import WatchKit
import Combine
import coreKmp

/**
 * WearFi Dashboard ViewModel for watchOS
 * 
 * 管理健康數據挖礦邏輯：
 * - HealthKit 數據同步
 * - 代幣獎勵計算
 * - 挑戰進度追蹤
 * - 成就系統管理
 * 
 * Created: 2025-08-07
 */
@MainActor
class WearFiDashboardViewModel: ObservableObject {
    
    // MARK: - Published Properties
    @Published var todaySteps: Int = 0
    @Published var todayCalories: Int = 0
    @Published var todayExerciseMinutes: Int = 0
    @Published var todayHeartRate: Int = 0
    
    @Published var todayEarnings: Double = 0
    @Published var totalEarnings: Double = 0
    @Published var pendingRewards: Double = 0
    
    @Published var stepsProgress: Double = 0
    @Published var caloriesProgress: Double = 0
    @Published var exerciseProgress: Double = 0
    
    @Published var activeChallenges: [HealthChallenge] = []
    @Published var achievements: [Achievement] = []
    
    // MARK: - Private Properties
    private let healthStore = HKHealthStore()
    private var healthUpdateTimer: Timer?
    private var cancellables = Set<AnyCancellable>()
    
    // KMP Bridge
    private let kmpBridge = KMPUseCaseDirect.shared
    private let walletRepository = WalletRepositoryManager.shared
    
    // Constants
    private let stepsGoal = 10000
    private let caloriesGoal = 500
    private let exerciseGoal = 30 // minutes
    
    // Earning rates (tokens per unit)
    private let stepsRate = 0.001 // 1 token per 1000 steps
    private let caloriesRate = 0.002 // 1 token per 500 calories
    private let exerciseRate = 0.1 // 1 token per 10 minutes
    
    // MARK: - Computed Properties
    
    var formattedTodayEarnings: String {
        String(format: "%.2f", todayEarnings)
    }
    
    var formattedTotalEarnings: String {
        String(format: "%.2f", totalEarnings)
    }
    
    var formattedRewardValue: String {
        // Assume 1 HEALTH = $0.10
        String(format: "%.2f", pendingRewards * 0.10)
    }
    
    // MARK: - Initialization
    
    init() {
        requestHealthKitAuthorization()
        loadStoredData()
        loadChallenges()
        loadAchievements()
    }
    
    // MARK: - Health Tracking
    
    func startHealthTracking() {
        // Update immediately
        updateHealthData()
        
        // Set up periodic updates
        healthUpdateTimer = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { _ in
            Task { @MainActor in
                self.updateHealthData()
            }
        }
    }
    
    func stopHealthTracking() {
        healthUpdateTimer?.invalidate()
        healthUpdateTimer = nil
    }
    
    private func requestHealthKitAuthorization() {
        let typesToRead: Set<HKObjectType> = [
            HKObjectType.quantityType(forIdentifier: .stepCount)!,
            HKObjectType.quantityType(forIdentifier: .activeEnergyBurned)!,
            HKObjectType.quantityType(forIdentifier: .appleExerciseTime)!,
            HKObjectType.quantityType(forIdentifier: .heartRate)!,
            HKObjectType.quantityType(forIdentifier: .distanceWalkingRunning)!
        ]
        
        healthStore.requestAuthorization(toShare: nil, read: typesToRead) { success, error in
            if success {
                print("HealthKit authorization granted")
                Task { @MainActor in
                    self.updateHealthData()
                }
            } else {
                print("HealthKit authorization failed: \(error?.localizedDescription ?? "Unknown error")")
            }
        }
    }
    
    private func updateHealthData() {
        fetchSteps()
        fetchCalories()
        fetchExerciseMinutes()
        fetchHeartRate()
        calculateEarnings()
        updateProgress()
    }
    
    private func fetchSteps() {
        let stepsType = HKQuantityType.quantityType(forIdentifier: .stepCount)!
        let startOfDay = Calendar.current.startOfDay(for: Date())
        let predicate = HKQuery.predicateForSamples(withStart: startOfDay, end: Date(), options: .strictStartDate)
        
        let query = HKStatisticsQuery(quantityType: stepsType, quantitySamplePredicate: predicate, options: .cumulativeSum) { _, result, _ in
            guard let result = result, let sum = result.sumQuantity() else { return }
            
            Task { @MainActor in
                self.todaySteps = Int(sum.doubleValue(for: HKUnit.count()))
            }
        }
        
        healthStore.execute(query)
    }
    
    private func fetchCalories() {
        let caloriesType = HKQuantityType.quantityType(forIdentifier: .activeEnergyBurned)!
        let startOfDay = Calendar.current.startOfDay(for: Date())
        let predicate = HKQuery.predicateForSamples(withStart: startOfDay, end: Date(), options: .strictStartDate)
        
        let query = HKStatisticsQuery(quantityType: caloriesType, quantitySamplePredicate: predicate, options: .cumulativeSum) { _, result, _ in
            guard let result = result, let sum = result.sumQuantity() else { return }
            
            Task { @MainActor in
                self.todayCalories = Int(sum.doubleValue(for: HKUnit.kilocalorie()))
            }
        }
        
        healthStore.execute(query)
    }
    
    private func fetchExerciseMinutes() {
        let exerciseType = HKQuantityType.quantityType(forIdentifier: .appleExerciseTime)!
        let startOfDay = Calendar.current.startOfDay(for: Date())
        let predicate = HKQuery.predicateForSamples(withStart: startOfDay, end: Date(), options: .strictStartDate)
        
        let query = HKStatisticsQuery(quantityType: exerciseType, quantitySamplePredicate: predicate, options: .cumulativeSum) { _, result, _ in
            guard let result = result, let sum = result.sumQuantity() else { return }
            
            Task { @MainActor in
                self.todayExerciseMinutes = Int(sum.doubleValue(for: HKUnit.minute()))
            }
        }
        
        healthStore.execute(query)
    }
    
    private func fetchHeartRate() {
        let heartRateType = HKQuantityType.quantityType(forIdentifier: .heartRate)!
        let sortDescriptor = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: false)
        
        let query = HKSampleQuery(sampleType: heartRateType, predicate: nil, limit: 1, sortDescriptors: [sortDescriptor]) { _, samples, _ in
            guard let sample = samples?.first as? HKQuantitySample else { return }
            
            Task { @MainActor in
                self.todayHeartRate = Int(sample.quantity.doubleValue(for: HKUnit(from: "count/min")))
            }
        }
        
        healthStore.execute(query)
    }
    
    // MARK: - Earnings Calculation
    
    private func calculateEarnings() {
        let stepsEarning = Double(todaySteps) * stepsRate
        let caloriesEarning = Double(todayCalories) * caloriesRate
        let exerciseEarning = Double(todayExerciseMinutes) * exerciseRate
        
        todayEarnings = stepsEarning + caloriesEarning + exerciseEarning
        
        // Apply bonus multipliers for achievements
        let multiplier = calculateBonusMultiplier()
        todayEarnings *= multiplier
        
        // Update pending rewards
        pendingRewards = todayEarnings
    }
    
    private func calculateBonusMultiplier() -> Double {
        var multiplier = 1.0
        
        // Add bonuses for active challenges
        for challenge in activeChallenges where challenge.isActive {
            multiplier += challenge.bonusMultiplier
        }
        
        // Add bonuses for achievements
        for achievement in achievements where achievement.isUnlocked {
            multiplier += achievement.bonusMultiplier
        }
        
        return min(multiplier, 3.0) // Cap at 3x
    }
    
    private func updateProgress() {
        stepsProgress = min(Double(todaySteps) / Double(stepsGoal), 1.0)
        caloriesProgress = min(Double(todayCalories) / Double(caloriesGoal), 1.0)
        exerciseProgress = min(Double(todayExerciseMinutes) / Double(exerciseGoal), 1.0)
    }
    
    // MARK: - Data Access Methods
    
    func getMetricValue(for metric: HealthMetric) -> String {
        switch metric {
        case .steps:
            return "\(todaySteps)"
        case .calories:
            return "\(todayCalories)"
        case .heartRate:
            return "\(todayHeartRate)"
        case .exercise:
            return "\(todayExerciseMinutes)m"
        case .distance:
            return "0km" // Placeholder
        }
    }
    
    func getMetricEarnings(for metric: HealthMetric) -> String {
        switch metric {
        case .steps:
            return String(format: "%.2f", Double(todaySteps) * stepsRate)
        case .calories:
            return String(format: "%.2f", Double(todayCalories) * caloriesRate)
        case .exercise:
            return String(format: "%.2f", Double(todayExerciseMinutes) * exerciseRate)
        default:
            return "0"
        }
    }
    
    func getChartData(for metric: HealthMetric) -> [Double] {
        // Return mock data for chart
        // In production, this would fetch historical data from HealthKit
        switch metric {
        case .steps:
            return [3000, 5000, 7000, 8000, 6000, 9000, Double(todaySteps)]
        case .calories:
            return [200, 350, 400, 300, 450, 380, Double(todayCalories)]
        case .heartRate:
            return [65, 70, 75, 80, 72, 68, Double(todayHeartRate)]
        case .exercise:
            return [15, 20, 30, 25, 35, 40, Double(todayExerciseMinutes)]
        case .distance:
            return [1.5, 2.0, 3.5, 2.8, 4.0, 3.2, 2.5]
        }
    }
    
    func getAverageValue(for metric: HealthMetric) -> String {
        let data = getChartData(for: metric)
        let average = data.reduce(0, +) / Double(data.count)
        
        switch metric {
        case .steps, .calories:
            return "\(Int(average))"
        case .heartRate:
            return "\(Int(average)) bpm"
        case .exercise:
            return "\(Int(average)) min"
        case .distance:
            return String(format: "%.1f km", average)
        }
    }
    
    func getMaxValue(for metric: HealthMetric) -> String {
        let data = getChartData(for: metric)
        let max = data.max() ?? 0
        
        switch metric {
        case .steps, .calories:
            return "\(Int(max))"
        case .heartRate:
            return "\(Int(max)) bpm"
        case .exercise:
            return "\(Int(max)) min"
        case .distance:
            return String(format: "%.1f km", max)
        }
    }
    
    func getEarningRate(for metric: HealthMetric) -> String {
        switch metric {
        case .steps:
            return "0.001/步"
        case .calories:
            return "0.002/卡"
        case .exercise:
            return "0.1/分鐘"
        default:
            return "N/A"
        }
    }
    
    // MARK: - Challenges & Achievements
    
    private func loadChallenges() {
        // Load active challenges from storage or API
        activeChallenges = [
            HealthChallenge(
                id: "weekly_10k",
                name: "週末萬步",
                description: "週末兩天每天達到10,000步",
                icon: "figure.walk",
                color: .blue,
                reward: 50,
                progress: 0.6,
                daysRemaining: 2,
                isActive: true,
                bonusMultiplier: 0.2
            ),
            HealthChallenge(
                id: "burn_500",
                name: "燃燒挑戰",
                description: "今日燃燒500卡路里",
                icon: "flame",
                color: .orange,
                reward: 30,
                progress: Double(todayCalories) / 500,
                daysRemaining: 1,
                isActive: true,
                bonusMultiplier: 0.15
            ),
            HealthChallenge(
                id: "heart_zone",
                name: "心率區間",
                description: "維持目標心率區間30分鐘",
                icon: "heart.fill",
                color: .red,
                reward: 40,
                progress: 0.3,
                daysRemaining: 1,
                isActive: true,
                bonusMultiplier: 0.1
            )
        ]
    }
    
    private func loadAchievements() {
        // Load achievements from storage
        achievements = [
            Achievement(
                id: "first_10k",
                name: "萬步達人",
                icon: "figure.walk.circle.fill",
                color: .blue,
                isUnlocked: todaySteps >= 10000,
                bonusMultiplier: 0.05
            ),
            Achievement(
                id: "calorie_master",
                name: "燃脂大師",
                icon: "flame.circle.fill",
                color: .orange,
                isUnlocked: todayCalories >= 500,
                bonusMultiplier: 0.05
            ),
            Achievement(
                id: "exercise_pro",
                name: "運動專家",
                icon: "heart.circle.fill",
                color: .red,
                isUnlocked: todayExerciseMinutes >= 60,
                bonusMultiplier: 0.1
            ),
            Achievement(
                id: "early_bird",
                name: "早起鳥兒",
                icon: "sunrise.circle.fill",
                color: .yellow,
                isUnlocked: false,
                bonusMultiplier: 0.05
            ),
            Achievement(
                id: "streak_7",
                name: "連續七天",
                icon: "calendar.circle.fill",
                color: .green,
                isUnlocked: false,
                bonusMultiplier: 0.15
            )
        ]
    }
    
    func participateInChallenge(_ challenge: HealthChallenge) {
        // Mark challenge as participated
        // Update UI and tracking
        print("Participating in challenge: \(challenge.name)")
    }
    
    // MARK: - Rewards Management
    
    func claimRewards() async {
        do {
            let userAddress = await getUserWalletAddress()
            
            // Send rewards to user's wallet through KMP
            let txHash = try await kmpBridge.sendTransaction(
                from: "0xHealthWallet",
                to: userAddress,
                amount: String(pendingRewards),
                chainType: coreKmp.ChainType.ethereum,
                password: ""
            )
            
            print("Rewards claimed: \(txHash)")
            
            // Update balances
            totalEarnings += pendingRewards
            pendingRewards = 0
            
            // Save to storage
            saveData()
        } catch {
            print("Failed to claim rewards: \(error)")
        }
    }
    
    func stakeRewards() async {
        // Stake rewards for higher APY
        // This would interact with DeFi protocols through KMP
        print("Staking \(pendingRewards) HEALTH tokens")
        
        totalEarnings += pendingRewards
        pendingRewards = 0
        saveData()
    }
    
    // MARK: - Storage
    
    private func loadStoredData() {
        // Load from UserDefaults or Keychain
        totalEarnings = UserDefaults.standard.double(forKey: "wearfi_total_earnings")
    }
    
    private func saveData() {
        UserDefaults.standard.set(totalEarnings, forKey: "wearfi_total_earnings")
    }
    
    private func getUserWalletAddress() async -> String {
        // Get user's wallet address from KMP
        do {
            let wallets = try await walletRepository.getAllWalletsAsync()
            let savedWalletId = UserDefaults.standard.string(forKey: "activeWalletId")
            let activeWallet = wallets.first(where: { $0.id == savedWalletId }) ?? wallets.first
            return activeWallet?.address ?? "0x0000000000000000000000000000000000000000"
        } catch {
            print("[WearFiDashboardViewModel] Failed to fetch wallets: \(error)")
            return "0x0000000000000000000000000000000000000000"
        }
    }
}

// MARK: - Supporting Types

enum HealthMetric: String, CaseIterable {
    case steps = "步數"
    case calories = "卡路里"
    case heartRate = "心率"
    case exercise = "運動"
    case distance = "距離"
    
    var name: String { rawValue }
    
    var icon: String {
        switch self {
        case .steps: return "figure.walk"
        case .calories: return "flame"
        case .heartRate: return "heart.fill"
        case .exercise: return "figure.run"
        case .distance: return "location"
        }
    }
    
    var color: Color {
        switch self {
        case .steps: return .blue
        case .calories: return .orange
        case .heartRate: return .red
        case .exercise: return .green
        case .distance: return .purple
        }
    }
}

struct HealthChallenge: Identifiable {
    let id: String
    let name: String
    let description: String
    let icon: String
    let color: Color
    let reward: Double
    let progress: Double
    let daysRemaining: Int
    let isActive: Bool
    let bonusMultiplier: Double
}

struct Achievement: Identifiable {
    let id: String
    let name: String
    let icon: String
    let color: Color
    let isUnlocked: Bool
    let bonusMultiplier: Double
}