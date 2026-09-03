import SwiftUI
import WatchKit

/**
 * ULTRATHINK Phase 11: Voice Assistant View for watchOS
 *
 * 完整實現語音助手界面，包括：
 * - 語音波形動畫顯示
 * - 生物識別認證狀態
 * - 交易風險視覺化
 * - Digital Crown 控制
 * - 觸覺反饋整合
 *
 * Created: 2025-08-07
 */
struct UltrathinkVoiceAssistantView: View {
    @StateObject private var viewModel = UltrathinkVoiceAssistantViewModel()
    @State private var crownValue: Double = 0.5
    @State private var showingAuthenticationSheet = false
    @State private var waveformAnimation = false
    @State private var pulseAnimation = false
    
    // Risk level colors
    private let riskColors: [UltrathinkVoiceAssistantViewModel.RiskLevel: Color] = [
        .low: .green,
        .medium: .yellow,
        .high: .orange,
        .critical: .red
    ]
    
    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                // Header with risk indicator
                headerSection
                
                // Voice waveform visualization
                voiceWaveformSection
                
                // Transcribed text display
                if !viewModel.transcribedText.isEmpty {
                    transcriptionSection
                }
                
                // Assistant response
                if !viewModel.assistantResponse.isEmpty {
                    responseSection
                }
                
                // Action buttons
                actionButtonsSection
                
                // Verification result if available
                if let result = viewModel.verificationResult {
                    verificationResultSection(result)
                }
            }
            .padding(.horizontal, 8)
        }
        .navigationTitle(NSLocalizedString("ai_assistant_title", comment: ""))
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
            viewModel.handleCrownRotation(newValue)
        }
        .sheet(isPresented: $showingAuthenticationSheet) {
            authenticationSheet
        }
    }
    
    // MARK: - View Components
    
    private var headerSection: some View {
        HStack {
            Image(systemName: "mic.circle.fill")
                .font(.title2)
                .foregroundColor(viewModel.isListening ? .blue : .gray)
                .scaleEffect(pulseAnimation ? 1.2 : 1.0)
                .animation(
                    viewModel.isListening ?
                    Animation.easeInOut(duration: 0.6).repeatForever(autoreverses: true) :
                    .default,
                    value: pulseAnimation
                )
            
            Spacer()
            
            // Risk level indicator
            RiskLevelIndicator(level: viewModel.currentRiskLevel)
                .frame(width: 60)
        }
        .padding(.vertical, 8)
        .onAppear {
            pulseAnimation = true
        }
    }
    
    private var voiceWaveformSection: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.black.opacity(0.3))
                .frame(height: 80)
            
            if viewModel.isListening {
                WaveformView(isAnimating: $waveformAnimation)
                    .frame(height: 60)
                    .onAppear { waveformAnimation = true }
                    .onDisappear { waveformAnimation = false }
            } else {
                Text(NSLocalizedString("tap_to_listen", comment: ""))
                    .font(.caption)
                    .foregroundColor(.gray)
            }
        }
        .onTapGesture {
            handleVoiceToggle()
        }
    }
    
    private var transcriptionSection: some View {
        VStack(alignment: .leading, spacing: 4) {
            Label(NSLocalizedString("voice_input", comment: ""), systemImage: "text.bubble")
                .font(.caption2)
                .foregroundColor(.secondary)
            
            Text(viewModel.transcribedText)
                .font(.footnote)
                .padding(8)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.blue.opacity(0.1))
                .cornerRadius(8)
        }
    }
    
    private var responseSection: some View {
        VStack(alignment: .leading, spacing: 4) {
            Label(NSLocalizedString("ai_response", comment: ""), systemImage: "cpu")
                .font(.caption2)
                .foregroundColor(.secondary)
            
            Text(viewModel.assistantResponse)
                .font(.footnote)
                .padding(8)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.purple.opacity(0.1))
                .cornerRadius(8)
        }
    }
    
    private var actionButtonsSection: some View {
        VStack(spacing: 8) {
            // Primary actions
            HStack(spacing: 8) {
                ActionButton(
                    title: NSLocalizedString("check_balance", comment: ""),
                    icon: "creditcard.circle",
                    color: .blue
                ) {
                    executeVoiceCommand("查詢我的錢包餘額")
                }
                
                ActionButton(
                    title: NSLocalizedString("send_tx", comment: ""),
                    icon: "paperplane.circle",
                    color: .green
                ) {
                    executeVoiceCommand("我要發送交易")
                }
            }
            
            // Secondary actions
            HStack(spacing: 8) {
                ActionButton(
                    title: NSLocalizedString("verify_tx", comment: ""),
                    icon: "checkmark.shield",
                    color: .orange
                ) {
                    executeVoiceCommand("驗證最新交易")
                }
                
                ActionButton(
                    title: NSLocalizedString("auth", comment: ""),
                    icon: "person.badge.shield.checkmark",
                    color: .purple
                ) {
                    showingAuthenticationSheet = true
                }
            }
            
            // Advanced features
            HStack(spacing: 8) {
                ActionButton(
                    title: NSLocalizedString("audit", comment: ""),
                    icon: "doc.text.magnifyingglass",
                    color: .teal
                ) {
                    executeVoiceCommand("審計智能合約")
                }
                
                ActionButton(
                    title: NSLocalizedString("analyze_nft", comment: ""),
                    icon: "photo.artframe",
                    color: .indigo
                ) {
                    executeVoiceCommand("分析 NFT")
                }
            }
        }
        .padding(.vertical, 8)
    }
    
    private func verificationResultSection(_ result: UltrathinkVoiceAssistantViewModel.VerificationResult) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(NSLocalizedString("verification_result", comment: ""), systemImage: "checkmark.seal")
                .font(.caption)
                .foregroundColor(.secondary)
            
            HStack {
                Text(NSLocalizedString("status", comment: ""))
                    .font(.caption2)
                Text(result.status)
                    .font(.caption2)
                    .fontWeight(.bold)
                    .foregroundColor(result.status == "APPROVED" ? .green : .red)
            }
            
            HStack {
                Text(NSLocalizedString("risk_score", comment: ""))
                    .font(.caption2)
                ProgressView(value: Double(result.riskScore), total: 100)
                    .tint(riskScoreColor(result.riskScore))
                Text("\(Int(result.riskScore))")
                    .font(.caption2)
                    .fontWeight(.bold)
            }
            
            if !result.warnings.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    Text(NSLocalizedString("warning", comment: ""))
                        .font(.caption2)
                        .foregroundColor(.orange)
                    ForEach(result.warnings, id: \.self) { warning in
                        Text("• \(warning)")
                            .font(.caption2)
                            .foregroundColor(.orange)
                    }
                }
            }
        }
        .padding(8)
        .background(Color.gray.opacity(0.1))
        .cornerRadius(8)
    }
    
    private var authenticationSheet: some View {
        VStack(spacing: 16) {
            Image(systemName: "person.crop.circle.badge.checkmark")
                .font(.largeTitle)
                .foregroundColor(.blue)
            
            Text(NSLocalizedString("voice_biometric_auth", comment: ""))
                .font(.headline)
            
            Text(NSLocalizedString("speak_phrase_instruction", comment: ""))
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            
            switch viewModel.authenticationState {
            case .idle:
                Button(action: {
                    Task {
                        await viewModel.authenticateWithVoice()
                    }
                }) {
                    Label(NSLocalizedString("start_auth", comment: ""), systemImage: "mic.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                
            case .authenticating:
                ProgressView(NSLocalizedString("authenticating", comment: ""))
                    .progressViewStyle(.circular)
                
            case .authenticated:
                Label(NSLocalizedString("auth_success", comment: ""), systemImage: "checkmark.circle.fill")
                    .foregroundColor(.green)
                
            case .failed(let error):
                VStack {
                    Label(NSLocalizedString("auth_failed", comment: ""), systemImage: "xmark.circle.fill")
                        .foregroundColor(.red)
                    Text(error)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            }
            
            Button(NSLocalizedString("close", comment: "")) {
                showingAuthenticationSheet = false
            }
            .buttonStyle(.bordered)
        }
        .padding()
    }
    
    // MARK: - Helper Methods
    
    private func handleVoiceToggle() {
        if viewModel.isListening {
            viewModel.stopListening()
            viewModel.provideHapticFeedback(for: .commandRecognized)
        } else {
            viewModel.startListening()
            WKInterfaceDevice.current().play(.start)
        }
    }
    
    private func executeVoiceCommand(_ command: String) {
        viewModel.transcribedText = command
        viewModel.provideHapticFeedback(for: .commandRecognized)
        // Process command will be triggered automatically
    }
    
    private func riskScoreColor(_ score: Float) -> Color {
        switch score {
        case 0..<30: return .green
        case 30..<60: return .yellow
        case 60..<80: return .orange
        default: return .red
        }
    }
}

// MARK: - Supporting Views

struct WaveformView: View {
    @Binding var isAnimating: Bool
    @State private var phase: CGFloat = 0
    
    var body: some View {
        GeometryReader { geometry in
            Path { path in
                let width = geometry.size.width
                let height = geometry.size.height
                let midHeight = height / 2
                
                path.move(to: CGPoint(x: 0, y: midHeight))
                
                for x in stride(from: 0, through: width, by: 2) {
                    let relativeX = x / width
                    let sine = sin((relativeX + phase) * .pi * 4)
                    let y = midHeight + sine * (height / 3)
                    path.addLine(to: CGPoint(x: x, y: y))
                }
            }
            .stroke(
                LinearGradient(
                    colors: [.blue, .purple],
                    startPoint: .leading,
                    endPoint: .trailing
                ),
                lineWidth: 2
            )
        }
        .onAppear {
            withAnimation(
                Animation.linear(duration: 2)
                    .repeatForever(autoreverses: false)
            ) {
                phase = 1
            }
        }
    }
}

struct RiskLevelIndicator: View {
    let level: UltrathinkVoiceAssistantViewModel.RiskLevel
    
    private var color: Color {
        switch level {
        case .low: return .green
        case .medium: return .yellow
        case .high: return .orange
        case .critical: return .red
        }
    }
    
    private var icon: String {
        switch level {
        case .low: return "shield.checkmark"
        case .medium: return "shield"
        case .high: return "exclamationmark.shield"
        case .critical: return "xmark.shield"
        }
    }
    
    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: icon)
                .font(.caption)
            Text(level.rawValue)
                .font(.caption2)
                .fontWeight(.semibold)
        }
        .foregroundColor(color)
        .padding(.horizontal, 6)
        .padding(.vertical, 2)
        .background(color.opacity(0.2))
        .cornerRadius(8)
    }
}

struct ActionButton: View {
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

// MARK: - Preview
struct UltrathinkVoiceAssistantView_Previews: PreviewProvider {
    static var previews: some View {
        NavigationView {
            UltrathinkVoiceAssistantView()
        }
    }
}