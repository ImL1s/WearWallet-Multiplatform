import SwiftUI
import HealthKit
import WatchKit

/**
 * WearFi Dashboard View for watchOS
 * 
 * 實現健康數據挖礦功能：
 * - 步數、心率、卡路里換取代幣
 * - 健康挑戰和成就系統
 * - 即時健康數據追蹤
 * - 獎勵分配和提領
 * 
 * Created: 2025-08-07
 */
struct WearFiDashboardView: View {
    @StateObject private var viewModel = WearFiDashboardViewModel()
    @State private var selectedMetric: HealthMetric = .steps
    @State private var showingRewardSheet = false
    @State private var crownValue: Double = 0.5
    
    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                // Header with total earnings
                totalEarningsHeader
                
                // Today's progress
                todayProgressSection
                
                // Health metrics selector
                healthMetricsSelector
                
                // Metric detail view
                metricDetailView
                
                // Active challenges
                if !viewModel.activeChallenges.isEmpty {
                    activeChallengesSection
                }
                
                // Achievements
                achievementsSection
                
                // Claim rewards button
                claimRewardsButton
            }
            .padding(.horizontal, 8)
        }
        .navigationTitle("WearFi")
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
            // Adjust metric selection with crown
            adjustMetricSelection(newValue)
        }
        .sheet(isPresented: $showingRewardSheet) {
            RewardClaimSheet(viewModel: viewModel)
        }
        .onAppear {
            viewModel.startHealthTracking()
        }
        .onDisappear {
            viewModel.stopHealthTracking()
        }
    }
    
    // MARK: - View Components
    
    private var totalEarningsHeader: some View {
        VStack(spacing: 4) {
            Text("今日收益")
                .font(.caption2)
                .foregroundColor(.secondary)
            
            HStack(spacing: 4) {
                Image(systemName: "bitcoinsign.circle.fill")
                    .font(.title3)
                    .foregroundColor(.orange)
                
                Text(viewModel.formattedTodayEarnings)
                    .font(.title3)
                    .fontWeight(.bold)
                
                Text("HEALTH")
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            
            Text("總收益: \(viewModel.formattedTotalEarnings)")
                .font(.caption)
                .foregroundColor(.green)
        }
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity)
        .background(
            LinearGradient(
                colors: [Color.orange.opacity(0.2), Color.green.opacity(0.1)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .cornerRadius(12)
    }
    
    private var todayProgressSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("今日進度")
                .font(.caption)
                .foregroundColor(.secondary)
            
            HStack(spacing: 12) {
                ProgressRing(
                    value: viewModel.stepsProgress,
                    icon: "figure.walk",
                    color: .blue,
                    label: "\(viewModel.todaySteps)"
                )
                
                ProgressRing(
                    value: viewModel.caloriesProgress,
                    icon: "flame",
                    color: .orange,
                    label: "\(viewModel.todayCalories)"
                )
                
                ProgressRing(
                    value: viewModel.exerciseProgress,
                    icon: "heart.fill",
                    color: .red,
                    label: "\(viewModel.todayExerciseMinutes)m"
                )
            }
        }
    }
    
    private var healthMetricsSelector: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(HealthMetric.allCases, id: \.self) { metric in
                    MetricChip(
                        metric: metric,
                        isSelected: selectedMetric == metric,
                        value: viewModel.getMetricValue(for: metric),
                        earnings: viewModel.getMetricEarnings(for: metric)
                    ) {
                        selectedMetric = metric
                        WKInterfaceDevice.current().play(.click)
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }
    
    private var metricDetailView: some View {
        VStack(spacing: 8) {
            // Chart view
            MetricChartView(
                metric: selectedMetric,
                data: viewModel.getChartData(for: selectedMetric)
            )
            .frame(height: 100)
            
            // Stats
            HStack {
                StatItem(
                    title: "平均",
                    value: viewModel.getAverageValue(for: selectedMetric),
                    icon: "chart.line.uptrend.xyaxis"
                )
                
                StatItem(
                    title: "最高",
                    value: viewModel.getMaxValue(for: selectedMetric),
                    icon: "arrow.up.to.line"
                )
                
                StatItem(
                    title: "收益率",
                    value: viewModel.getEarningRate(for: selectedMetric),
                    icon: "bitcoinsign.circle"
                )
            }
        }
        .padding(8)
        .background(Color.gray.opacity(0.1))
        .cornerRadius(12)
    }
    
    private var activeChallengesSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("活動挑戰")
                    .font(.caption)
                    .foregroundColor(.secondary)
                
                Spacer()
                
                Text("\(viewModel.activeChallenges.count) 個進行中")
                    .font(.caption2)
                    .foregroundColor(.blue)
            }
            
            ForEach(viewModel.activeChallenges) { challenge in
                ChallengeCard(challenge: challenge) {
                    viewModel.participateInChallenge(challenge)
                }
            }
        }
    }
    
    private var achievementsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("成就徽章")
                .font(.caption)
                .foregroundColor(.secondary)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(viewModel.achievements) { achievement in
                        AchievementBadge(achievement: achievement)
                    }
                }
            }
        }
    }
    
    private var claimRewardsButton: some View {
        Button(action: {
            showingRewardSheet = true
            WKInterfaceDevice.current().play(.success)
        }) {
            HStack {
                Image(systemName: "gift.fill")
                Text("領取獎勵")
                Text("(\(viewModel.pendingRewards) HEALTH)")
                    .fontWeight(.bold)
            }
            .font(.caption)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .background(
                viewModel.pendingRewards > 0 ?
                LinearGradient(
                    colors: [Color.green, Color.blue],
                    startPoint: .leading,
                    endPoint: .trailing
                ) : LinearGradient(
                    colors: [Color.gray],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .foregroundColor(.white)
            .cornerRadius(20)
        }
        .disabled(viewModel.pendingRewards <= 0)
    }
    
    // MARK: - Helper Methods
    
    private func adjustMetricSelection(_ value: Double) {
        let metrics = HealthMetric.allCases
        let index = Int(value * Double(metrics.count - 1))
        selectedMetric = metrics[min(index, metrics.count - 1)]
    }
}

// MARK: - Supporting Views

struct ProgressRing: View {
    let value: Double
    let icon: String
    let color: Color
    let label: String
    
    var body: some View {
        VStack(spacing: 2) {
            ZStack {
                Circle()
                    .stroke(color.opacity(0.2), lineWidth: 3)
                    .frame(width: 40, height: 40)
                
                Circle()
                    .trim(from: 0, to: value)
                    .stroke(color, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                    .frame(width: 40, height: 40)
                    .rotationEffect(.degrees(-90))
                
                Image(systemName: icon)
                    .font(.caption)
                    .foregroundColor(color)
            }
            
            Text(label)
                .font(.caption2)
                .fontWeight(.medium)
        }
    }
}

struct MetricChip: View {
    let metric: HealthMetric
    let isSelected: Bool
    let value: String
    let earnings: String
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Image(systemName: metric.icon)
                    .font(.caption)
                Text(metric.name)
                    .font(.caption2)
                Text(value)
                    .font(.caption2)
                    .fontWeight(.bold)
                Text("+\(earnings)")
                    .font(.caption2)
                    .foregroundColor(.green)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(isSelected ? metric.color : Color.gray.opacity(0.2))
            .foregroundColor(isSelected ? .white : .primary)
            .cornerRadius(12)
        }
        .buttonStyle(.plain)
    }
}

struct ChallengeCard: View {
    let challenge: HealthChallenge
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Image(systemName: challenge.icon)
                        .font(.caption)
                        .foregroundColor(challenge.color)
                    
                    Text(challenge.name)
                        .font(.caption2)
                        .fontWeight(.semibold)
                    
                    Spacer()
                    
                    Text("\(challenge.reward) HEALTH")
                        .font(.caption2)
                        .foregroundColor(.green)
                }
                
                Text(challenge.description)
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
                
                // Progress bar
                GeometryReader { geometry in
                    ZStack(alignment: .leading) {
                        Rectangle()
                            .fill(Color.gray.opacity(0.2))
                            .frame(height: 4)
                        
                        Rectangle()
                            .fill(challenge.color)
                            .frame(width: geometry.size.width * challenge.progress, height: 4)
                    }
                }
                .frame(height: 4)
                
                HStack {
                    Text("\(Int(challenge.progress * 100))% 完成")
                        .font(.caption2)
                    
                    Spacer()
                    
                    Text("剩餘 \(challenge.daysRemaining) 天")
                        .font(.caption2)
                        .foregroundColor(.orange)
                }
            }
            .padding(8)
            .background(Color.gray.opacity(0.1))
            .cornerRadius(8)
        }
        .buttonStyle(.plain)
    }
}

struct AchievementBadge: View {
    let achievement: Achievement
    
    var body: some View {
        VStack(spacing: 2) {
            ZStack {
                Circle()
                    .fill(
                        achievement.isUnlocked ?
                        LinearGradient(
                            colors: [achievement.color, achievement.color.opacity(0.6)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ) : LinearGradient(
                            colors: [Color.gray],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 44, height: 44)
                
                Image(systemName: achievement.icon)
                    .font(.title3)
                    .foregroundColor(.white)
            }
            
            Text(achievement.name)
                .font(.caption2)
                .lineLimit(1)
        }
        .opacity(achievement.isUnlocked ? 1.0 : 0.5)
    }
}

struct StatItem: View {
    let title: String
    let value: String
    let icon: String
    
    var body: some View {
        VStack(spacing: 2) {
            Image(systemName: icon)
                .font(.caption)
                .foregroundColor(.secondary)
            Text(value)
                .font(.caption2)
                .fontWeight(.bold)
            Text(title)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}

struct MetricChartView: View {
    let metric: HealthMetric
    let data: [Double]
    
    var body: some View {
        GeometryReader { geometry in
            Path { path in
                guard !data.isEmpty else { return }
                
                let width = geometry.size.width
                let height = geometry.size.height
                let maxValue = data.max() ?? 1
                let xStep = width / CGFloat(data.count - 1)
                
                path.move(to: CGPoint(
                    x: 0,
                    y: height - (height * CGFloat(data[0] / maxValue))
                ))
                
                for (index, value) in data.enumerated() {
                    let x = CGFloat(index) * xStep
                    let y = height - (height * CGFloat(value / maxValue))
                    path.addLine(to: CGPoint(x: x, y: y))
                }
            }
            .stroke(metric.color, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
            
            // Add gradient fill
            Path { path in
                guard !data.isEmpty else { return }
                
                let width = geometry.size.width
                let height = geometry.size.height
                let maxValue = data.max() ?? 1
                let xStep = width / CGFloat(data.count - 1)
                
                path.move(to: CGPoint(x: 0, y: height))
                path.addLine(to: CGPoint(
                    x: 0,
                    y: height - (height * CGFloat(data[0] / maxValue))
                ))
                
                for (index, value) in data.enumerated() {
                    let x = CGFloat(index) * xStep
                    let y = height - (height * CGFloat(value / maxValue))
                    path.addLine(to: CGPoint(x: x, y: y))
                }
                
                path.addLine(to: CGPoint(x: width, y: height))
                path.closeSubpath()
            }
            .fill(
                LinearGradient(
                    colors: [metric.color.opacity(0.3), metric.color.opacity(0.1)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
        }
    }
}

// MARK: - Reward Claim Sheet

struct RewardClaimSheet: View {
    @ObservedObject var viewModel: WearFiDashboardViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var isProcessing = false
    
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "gift.circle.fill")
                .font(.largeTitle)
                .foregroundColor(.green)
            
            Text("領取健康獎勵")
                .font(.headline)
            
            Text("\(viewModel.pendingRewards) HEALTH")
                .font(.title3)
                .fontWeight(.bold)
                .foregroundColor(.green)
            
            Text("約等於 $\(viewModel.formattedRewardValue)")
                .font(.caption)
                .foregroundColor(.secondary)
            
            if isProcessing {
                ProgressView("處理中...")
                    .progressViewStyle(.circular)
            } else {
                VStack(spacing: 8) {
                    Button(action: {
                        claimRewards()
                    }) {
                        Label("領取到錢包", systemImage: "wallet.pass")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    
                    Button(action: {
                        stakRewards()
                    }) {
                        Label("質押獲得更多", systemImage: "chart.line.uptrend.xyaxis")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
            }
            
            Button("稍後領取") {
                dismiss()
            }
            .buttonStyle(.plain)
            .foregroundColor(.secondary)
        }
        .padding()
    }
    
    private func claimRewards() {
        isProcessing = true
        Task {
            await viewModel.claimRewards()
            WKInterfaceDevice.current().play(.success)
            dismiss()
        }
    }
    
    private func stakRewards() {
        isProcessing = true
        Task {
            await viewModel.stakeRewards()
            WKInterfaceDevice.current().play(.success)
            dismiss()
        }
    }
}

// MARK: - Preview

struct WearFiDashboardView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationView {
            WearFiDashboardView()
        }
    }
}