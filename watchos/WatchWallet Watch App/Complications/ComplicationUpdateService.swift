//
//  ComplicationUpdateService.swift
//  WatchWallet Watch App
//
//  Complication 更新服務
//  處理定期更新和與 PriceService 的整合
//

import Foundation
import ClockKit
import WatchKit
import coreKmp

@MainActor
class ComplicationUpdateService: ObservableObject {
    
    // MARK: - Singleton
    static let shared = ComplicationUpdateService()
    
    // MARK: - Properties
    private let priceService = PriceService.shared
    private let dataProvider = ComplicationDataProvider()
    
    private var updateTimer: Timer?
    private let updateInterval: TimeInterval = 15 * 60 // 15分鐘
    private let maxUpdatesPerHour = 4 // Apple 建議限制
    private var updateCount = 0
    private var lastHourReset = Date()
    
    // MARK: - Initialization
    private init() {
        setupUpdateTimer()
        observePriceServiceUpdates()
    }
    
    deinit {
        // Timer cleanup handled in nonisolated method
        updateTimer?.invalidate()
        updateTimer = nil
    }
    
    // MARK: - Public Methods
    
    /**
     * 立即更新所有 Complications
     */
    func updateComplications() async {
        guard canUpdateComplications() else {
            print("[ComplicationUpdateService] ⚠️ 已達到每小時更新限制")
            return
        }
        
        print("[ComplicationUpdateService] 🔄 開始更新 Complications...")
        
        do {
            // 刷新數據提供者的數據
            try await dataProvider.refreshAllData()
            
            // 請求 ClockKit 更新所有活躍的 complications
            let server = CLKComplicationServer.sharedInstance()
            
            if let activeComplications = server.activeComplications {
                for complication in activeComplications {
                    print("[ComplicationUpdateService] 更新 complication: \(complication.identifier ?? "unknown")")
                    server.reloadTimeline(for: complication)
                }
                
                incrementUpdateCount()
                print("[ComplicationUpdateService] ✅ 已更新 \(activeComplications.count) 個 complications")
            } else {
                print("[ComplicationUpdateService] ℹ️ 沒有活躍的 complications")
            }
            
        } catch {
            print("[ComplicationUpdateService] ❌ 更新 complications 失敗: \(error)")
        }
    }
    
    /**
     * 當應用啟動時初始化 complications 數據
     */
    func initializeComplications() async {
        print("[ComplicationUpdateService] 初始化 Complications 數據...")
        await updateComplications()
    }
    
    /**
     * 當價格數據更新時觸發 complication 更新
     */
    func onPriceDataUpdated() {
        Task {
            await updateComplications()
        }
    }
    
    /**
     * 強制刷新（忽略限制，僅在用戶明確操作時使用）
     */
    func forceUpdate() async {
        print("[ComplicationUpdateService] 🔄 強制更新 Complications...")
        
        do {
            try await dataProvider.refreshAllData()
            
            let server = CLKComplicationServer.sharedInstance()
            if let activeComplications = server.activeComplications {
                for complication in activeComplications {
                    server.reloadTimeline(for: complication)
                }
            }
            
            print("[ComplicationUpdateService] ✅ 強制更新完成")
        } catch {
            print("[ComplicationUpdateService] ❌ 強制更新失敗: \(error)")
        }
    }
    
    // MARK: - Private Methods
    
    private func setupUpdateTimer() {
        updateTimer = Timer.scheduledTimer(withTimeInterval: updateInterval, repeats: true) { [weak self] _ in
            Task { @MainActor in
                await self?.updateComplications()
            }
        }
        
        print("[ComplicationUpdateService] ⏰ 設定更新計時器，間隔: \(updateInterval/60) 分鐘")
    }
    
    private func stopUpdateTimer() {
        updateTimer?.invalidate()
        updateTimer = nil
        print("[ComplicationUpdateService] ⏹️ 停止更新計時器")
    }
    
    private func observePriceServiceUpdates() {
        // 監聽 PriceService 的更新通知
        NotificationCenter.default.addObserver(
            forName: .priceServiceDidUpdate,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task {
                await self?.updateComplications()
            }
        }
        
        print("[ComplicationUpdateService] 👂 開始監聽 PriceService 更新")
    }
    
    private func canUpdateComplications() -> Bool {
        let now = Date()
        
        // 檢查是否需要重置計數器（每小時重置）
        if now.timeIntervalSince(lastHourReset) >= 3600 {
            updateCount = 0
            lastHourReset = now
        }
        
        return updateCount < maxUpdatesPerHour
    }
    
    private func incrementUpdateCount() {
        updateCount += 1
        print("[ComplicationUpdateService] 📊 更新計數: \(updateCount)/\(maxUpdatesPerHour)")
    }
    
    /**
     * 獲取下次更新時間
     */
    func getNextUpdateTime() -> Date {
        return Date().addingTimeInterval(updateInterval)
    }
    
    /**
     * 檢查 complications 是否處於活躍狀態
     */
    func hasActiveComplications() -> Bool {
        let server = CLKComplicationServer.sharedInstance()
        return server.activeComplications?.isEmpty == false
    }
    
    /**
     * 獲取活躍 complications 的統計信息
     */
    func getActiveComplicationsInfo() -> [String: Any] {
        let server = CLKComplicationServer.sharedInstance()
        guard let activeComplications = server.activeComplications else {
            return ["count": 0, "types": []]
        }
        
        let familyTypes = activeComplications.map { $0.family.rawValue }
        let identifiers = activeComplications.compactMap { $0.identifier }
        
        return [
            "count": activeComplications.count,
            "families": familyTypes,
            "identifiers": identifiers,
            "updateCount": updateCount,
            "maxUpdatesPerHour": maxUpdatesPerHour,
            "nextUpdateTime": getNextUpdateTime()
        ]
    }
}

// MARK: - Notification Names

extension Notification.Name {
    // priceServiceDidUpdate is defined in PriceService.swift
    static let complicationDidUpdate = Notification.Name("ComplicationDidUpdate")
}

// MARK: - Extension for PriceService Integration
// Note: PriceService.notifyPriceUpdate() is defined in PriceService.swift

// MARK: - Background Task Support

extension ComplicationUpdateService {
    
    /**
     * 處理背景更新任務
     */
    func handleBackgroundRefresh() async {
        print("[ComplicationUpdateService] 🔄 處理背景更新...")
        
        // 在背景模式下，優先更新價格數據
        await priceService.refreshPrices()
        
        // 然後更新 complications
        await updateComplications()
        
        print("[ComplicationUpdateService] ✅ 背景更新完成")
    }
    
    /**
     * 配置背景更新頻率
     */
    func scheduleBackgroundUpdates() {
        // 告知系統我們需要背景更新來保持 complications 數據新鮮
        // WKExtension is deprecated in watchOS 7+, use WKApplication instead
        // userInfo needs to be a NSSecureCoding conforming object, using nil for now
        WKApplication.shared().scheduleBackgroundRefresh(
            withPreferredDate: getNextUpdateTime(),
            userInfo: nil,
            scheduledCompletion: { error in
                if let error = error {
                    print("[ComplicationUpdateService] ❌ 排程背景更新失敗: \(error)")
                } else {
                    print("[ComplicationUpdateService] ✅ 已排程背景更新")
                }
            }
        )
    }
}