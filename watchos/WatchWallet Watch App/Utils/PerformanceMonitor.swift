//
//  PerformanceMonitor.swift
//  WatchWallet Watch App
//
//  watchOS 效能和電池使用監控工具
//

import Foundation
import WatchKit
import os.log

class PerformanceMonitor {
    static let shared = PerformanceMonitor()
    
    private let logger = Logger(subsystem: "com.cbstudio.wearwallet", category: "Performance")
    private var startTime: CFTimeInterval = 0
    private var memoryBaseline: UInt64 = 0
    
    private init() {
        recordMemoryBaseline()
    }
    
    // MARK: - Keystone 效能監控
    
    /**
     * 開始監控 Keystone 連接效能
     */
    func startKeystoneConnectionMonitoring() {
        startTime = Date().timeIntervalSince1970
        logger.info("🔍 開始監控 Keystone 連接效能")
        
        // 記錄初始記憶體使用
        let currentMemory = getCurrentMemoryUsage()
        logger.info("📊 初始記憶體使用: \(currentMemory / 1024 / 1024) MB")
    }
    
    /**
     * 結束監控並記錄結果
     */
    func endKeystoneConnectionMonitoring(success: Bool) {
        let endTime = Date().timeIntervalSince1970
        let duration = endTime - startTime
        let finalMemory = getCurrentMemoryUsage()
        let memoryIncrease = finalMemory - memoryBaseline
        
        logger.info("⏱️ Keystone 連接耗時: \(String(format: "%.2f", duration * 1000))ms")
        logger.info("📊 記憶體增量: \(memoryIncrease / 1024 / 1024) MB")
        logger.info("✅ 連接結果: \(success ? "成功" : "失敗")")
        
        // 效能建議
        if duration > 5.0 {
            logger.warning("⚠️ 連接時間過長，建議優化")
        }
        
        if memoryIncrease > 10 * 1024 * 1024 { // 10MB
            logger.warning("⚠️ 記憶體使用量較高，建議優化")
        }
    }
    
    /**
     * 監控 WatchConnectivity 消息效能
     */
    func monitorWatchConnectivityMessage(type: String, completion: @escaping (TimeInterval) -> Void) {
        let startTime = Date().timeIntervalSince1970
        
        // 使用 DispatchQueue 來模擬異步監控
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            let duration = Date().timeIntervalSince1970 - startTime
            self.logger.info("📡 WatchConnectivity 消息 '\(type)' 處理時間: \(String(format: "%.2f", duration * 1000))ms")
            completion(duration)
        }
    }
    
    // MARK: - 電池使用監控
    
    /**
     * 獲取當前電池狀態
     */
    func getCurrentBatteryInfo() -> (level: Float, state: WKInterfaceDeviceBatteryState) {
        let device = WKInterfaceDevice.current()
        device.isBatteryMonitoringEnabled = true
        
        let level = device.batteryLevel
        let state = device.batteryState
        
        logger.info("🔋 當前電池電量: \(Int(level * 100))%")
        logger.info("🔌 電池狀態: \(self.batteryStateDescription(state))")
        
        return (level, state)
    }
    
    /**
     * 估算 Keystone 操作的電池影響
     */
    func estimateKeystoneBatteryImpact(duration: TimeInterval) -> String {
        // 基於經驗數據的估算
        let basePowerConsumption = 0.001 // 1% per second of active use
        let estimatedImpact = basePowerConsumption * duration * 100
        
        logger.info("🔋 估算電池影響: \(String(format: "%.3f", estimatedImpact))%")
        
        if estimatedImpact < 0.1 {
            return "極低影響 (< 0.1%)"
        } else if estimatedImpact < 0.5 {
            return "低影響 (\(String(format: "%.2f", estimatedImpact))%)"
        } else if estimatedImpact < 1.0 {
            return "中等影響 (\(String(format: "%.2f", estimatedImpact))%)"
        } else {
            return "較高影響 (\(String(format: "%.2f", estimatedImpact))%)"
        }
    }
    
    // MARK: - 記憶體監控
    
    /**
     * 記錄記憶體基準線
     */
    private func recordMemoryBaseline() {
        memoryBaseline = getCurrentMemoryUsage()
        logger.info("📊 記憶體基準線: \(self.memoryBaseline / 1024 / 1024) MB")
    }
    
    /**
     * 獲取當前記憶體使用量
     */
    private func getCurrentMemoryUsage() -> UInt64 {
        var info = mach_task_basic_info()
        var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size) / 4
        
        let kerr: kern_return_t = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: 1) {
                task_info(mach_task_self_,
                         task_flavor_t(MACH_TASK_BASIC_INFO),
                         $0,
                         &count)
            }
        }
        
        if kerr == KERN_SUCCESS {
            return info.resident_size
        } else {
            logger.error("❌ 無法獲取記憶體使用量")
            return 0
        }
    }
    
    // MARK: - 網絡效能監控
    
    /**
     * 監控網絡請求效能
     */
    func monitorNetworkRequest<T>(
        operation: @escaping () async throws -> T,
        description: String
    ) async -> (result: Result<T, Error>, duration: TimeInterval) {
        let startTime = Date().timeIntervalSince1970
        
        do {
            let result = try await operation()
            let duration = Date().timeIntervalSince1970 - startTime
            
            logger.info("🌐 網絡請求 '\(description)' 成功，耗時: \(String(format: "%.2f", duration * 1000))ms")
            
            return (.success(result), duration)
        } catch {
            let duration = Date().timeIntervalSince1970 - startTime
            
            logger.error("🌐 網絡請求 '\(description)' 失敗，耗時: \(String(format: "%.2f", duration * 1000))ms, 錯誤: \(error.localizedDescription)")
            
            return (.failure(error), duration)
        }
    }
    
    // MARK: - 輔助方法
    
    private func batteryStateDescription(_ state: WKInterfaceDeviceBatteryState) -> String {
        switch state {
        case .unknown:
            return "未知"
        case .unplugged:
            return "未充電"
        case .charging:
            return "充電中"
        case .full:
            return "已滿"
        @unknown default:
            return "其他狀態"
        }
    }
    
    /**
     * 生成效能報告
     */
    func generatePerformanceReport() -> String {
        let currentMemory = getCurrentMemoryUsage()
        let memoryIncrease = currentMemory - memoryBaseline
        let (batteryLevel, batteryState) = getCurrentBatteryInfo()
        
        return """
        📊 watchOS Keystone 效能報告
        ================================
        
        📱 設備資訊:
        • 設備型號: \(WKInterfaceDevice.current().model)
        • 系統版本: \(WKInterfaceDevice.current().systemVersion)
        
        🔋 電池狀態:
        • 電池電量: \(Int(batteryLevel * 100))%
        • 電池狀態: \(batteryStateDescription(batteryState))
        
        📊 記憶體使用:
        • 當前使用: \(currentMemory / 1024 / 1024) MB
        • 基準線: \(memoryBaseline / 1024 / 1024) MB
        • 增量: \(memoryIncrease / 1024 / 1024) MB
        
        ⚡ 建議:
        \(memoryIncrease < 5 * 1024 * 1024 ? "✅ 記憶體使用良好" : "⚠️ 建議優化記憶體使用")
        \(batteryLevel > 0.2 ? "✅ 電池電量充足" : "⚠️ 電池電量較低")
        """
    }
}

// MARK: - 效能測試擴展

extension PerformanceMonitor {
    
    /**
     * 批量測試 Keystone 連接效能
     */
    func runKeystoneBenchmark(iterations: Int = 10) async {
        logger.info("🚀 開始 Keystone 效能基準測試，迭代次數: \(iterations)")
        
        var durations: [TimeInterval] = []
        var memoryIncreases: [UInt64] = []
        
        for i in 1...iterations {
            logger.info("📊 測試迭代 \(i)/\(iterations)")
            
            let startMemory = getCurrentMemoryUsage()
            let startTime = Date().timeIntervalSince1970
            
            // 模擬 Keystone 連接流程
            await simulateKeystoneConnection()
            
            let duration = Date().timeIntervalSince1970 - startTime
            let endMemory = getCurrentMemoryUsage()
            let memoryIncrease = endMemory - startMemory
            
            durations.append(duration)
            memoryIncreases.append(memoryIncrease)
            
            // 短暫等待以避免過載
            try? await Task.sleep(nanoseconds: 500_000_000) // 0.5秒
        }
        
        // 計算統計數據
        let avgDuration = durations.reduce(0, +) / Double(durations.count)
        let maxDuration = durations.max() ?? 0
        let minDuration = durations.min() ?? 0
        
        let avgMemory = memoryIncreases.reduce(0, +) / UInt64(memoryIncreases.count)
        let maxMemory = memoryIncreases.max() ?? 0
        
        logger.info("""
        📊 基準測試結果:
        ⏱️ 平均耗時: \(String(format: "%.2f", avgDuration * 1000))ms
        ⏱️ 最大耗時: \(String(format: "%.2f", maxDuration * 1000))ms
        ⏱️ 最小耗時: \(String(format: "%.2f", minDuration * 1000))ms
        📊 平均記憶體增量: \(avgMemory / 1024 / 1024) MB
        📊 最大記憶體增量: \(maxMemory / 1024 / 1024) MB
        """)
    }
    
    /**
     * 模擬 Keystone 連接流程
     */
    private func simulateKeystoneConnection() async {
        // 模擬狀態轉換
        try? await Task.sleep(nanoseconds: 100_000_000) // 0.1秒
        
        // 模擬 WatchConnectivity 通信
        try? await Task.sleep(nanoseconds: 200_000_000) // 0.2秒
        
        // 模擬數據處理
        try? await Task.sleep(nanoseconds: 300_000_000) // 0.3秒
        
        // 模擬 UI 更新
        try? await Task.sleep(nanoseconds: 100_000_000) // 0.1秒
    }
}