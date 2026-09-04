//
//  ComplicationController.swift
//  WatchWallet Watch App
//
//  Watch Face Complications 控制器
//  基於 WearOS 實現，提供代幣價格、錢包餘額等信息
//

import ClockKit
import SwiftUI
// Note: coreKmp dependency is handled by PriceService and ComplicationDataProvider

class ComplicationController: NSObject, CLKComplicationDataSource {
    
    // MARK: - Private Properties
    private let priceService = PriceService.shared
    private let dataProvider = ComplicationDataProvider()
    
    // 支援的 Complication Families
    private let supportedFamilies: [CLKComplicationFamily] = [
        .circularSmall,
        .modularSmall,
        .utilitarianSmall,
        .graphicCorner,
        .graphicCircular
    ]
    
    // MARK: - Timeline Configuration
    
    func getSupportedTimeTravelDirections(for complication: CLKComplication, withHandler handler: @escaping (CLKComplicationTimeTravelDirections) -> Void) {
        // 不支援時間旅行，因為加密貨幣價格是即時數據
        handler([])
    }
    
    func getTimelineStartDate(for complication: CLKComplication, withHandler handler: @escaping (Date?) -> Void) {
        handler(nil)
    }
    
    func getTimelineEndDate(for complication: CLKComplication, withHandler handler: @escaping (Date?) -> Void) {
        handler(nil)
    }
    
    func getPrivacyBehavior(for complication: CLKComplication, withHandler handler: @escaping (CLKComplicationPrivacyBehavior) -> Void) {
        // 價格信息不是隱私敏感數據
        handler(.showOnLockScreen)
    }
    
    // MARK: - Timeline Population
    
    func getCurrentTimelineEntry(for complication: CLKComplication, withHandler handler: @escaping (CLKComplicationTimelineEntry?) -> Void) {
        Task {
            do {
                let template = try await createComplicationTemplate(for: complication.family)
                let entry = CLKComplicationTimelineEntry(date: Date(), complicationTemplate: template)
                handler(entry)
            } catch {
                print("[ComplicationController] ❌ 獲取當前時間線條目失敗: \(error)")
                handler(nil)
            }
        }
    }
    
    func getTimelineEntries(for complication: CLKComplication, before date: Date, limit: Int, withHandler handler: @escaping ([CLKComplicationTimelineEntry]?) -> Void) {
        // 不提供歷史數據
        handler(nil)
    }
    
    func getTimelineEntries(for complication: CLKComplication, after date: Date, limit: Int, withHandler handler: @escaping ([CLKComplicationTimelineEntry]?) -> Void) {
        // 提供未來 4 小時的預測更新時間點
        Task {
            let updateInterval: TimeInterval = 15 * 60 // 15分鐘
            var futureEntries: [CLKComplicationTimelineEntry] = []
            
            for index in 1...min(limit, 16) {
                let futureDate = date.addingTimeInterval(TimeInterval(index) * updateInterval)
                if let template = try? await createComplicationTemplate(for: complication.family) {
                    let entry = CLKComplicationTimelineEntry(date: futureDate, complicationTemplate: template)
                    futureEntries.append(entry)
                }
            }
            
            handler(futureEntries.isEmpty ? nil : futureEntries)
        }
    }
    
    // MARK: - Placeholder Templates
    
    func getLocalizableSampleTemplate(for complication: CLKComplication, withHandler handler: @escaping (CLKComplicationTemplate?) -> Void) {
        let template = createSampleTemplate(for: complication.family)
        handler(template)
    }
    
    // MARK: - Template Creation
    
    private func createComplicationTemplate(for family: CLKComplicationFamily) async throws -> CLKComplicationTemplate {
        switch family {
        case .circularSmall:
            return try await createCircularSmallTemplate()
        case .modularSmall:
            return try await createModularSmallTemplate()
        case .utilitarianSmall:
            return try await createUtilitarianSmallTemplate()
        case .graphicCorner:
            return try await createGraphicCornerTemplate()
        case .graphicCircular:
            return try await createGraphicCircularTemplate()
        default:
            throw ComplicationError.unsupportedFamily
        }
    }
    
    // MARK: - Circular Small Template (主要代幣價格)
    
    private func createCircularSmallTemplate() async throws -> CLKComplicationTemplateCircularSmallSimpleText {
        do {
            let tokenData = try await dataProvider.getPrimaryTokenData()
            return ComplicationTemplates.createTokenPriceCircularSmall(data: tokenData)
        } catch {
            print("[ComplicationController] ❌ 創建 Circular Small 模板失敗: \(error)")
            return ComplicationTemplates.createErrorCircularSmall()
        }
    }
    
    // MARK: - Modular Small Template (價格 + 變化率)
    
    private func createModularSmallTemplate() async throws -> CLKComplicationTemplateModularSmallColumnsText {
        do {
            let tokenData = try await dataProvider.getPrimaryTokenData()
            return ComplicationTemplates.createTokenPriceModularSmall(data: tokenData)
        } catch {
            print("[ComplicationController] ❌ 創建 Modular Small 模板失敗: \(error)")
            return ComplicationTemplates.createErrorModularSmall()
        }
    }
    
    // MARK: - Utilitarian Small Template (錢包總餘額)
    
    private func createUtilitarianSmallTemplate() async throws -> CLKComplicationTemplateUtilitarianSmallFlat {
        do {
            let balanceData = try await dataProvider.getWalletBalanceData()
            return ComplicationTemplates.createWalletBalanceUtilitarianSmall(data: balanceData)
        } catch {
            print("[ComplicationController] ❌ 創建 Utilitarian Small 模板失敗: \(error)")
            return ComplicationTemplates.createErrorUtilitarianSmall()
        }
    }
    
    // MARK: - Graphic Corner Template (進階功能 - 趨勢圖表)
    
    private func createGraphicCornerTemplate() async throws -> CLKComplicationTemplateGraphicCornerTextImage {
        do {
            let tokenData = try await dataProvider.getPrimaryTokenData()
            return ComplicationTemplates.createTokenPriceGraphicCorner(data: tokenData)
        } catch {
            print("[ComplicationController] ❌ 創建 Graphic Corner 模板失敗: \(error)")
            return ComplicationTemplates.createErrorGraphicCorner()
        }
    }
    
    // MARK: - Graphic Circular Template
    
    private func createGraphicCircularTemplate() async throws -> CLKComplicationTemplateGraphicCircularOpenGaugeSimpleText {
        do {
            let tokenData = try await dataProvider.getPrimaryTokenData()
            return ComplicationTemplates.createTokenPriceGraphicCircular(data: tokenData)
        } catch {
            print("[ComplicationController] ❌ 創建 Graphic Circular 模板失敗: \(error)")
            return ComplicationTemplates.createErrorGraphicCircular()
        }
    }
    
    // MARK: - Sample Templates (預覽用)
    
    private func createSampleTemplate(for family: CLKComplicationFamily) -> CLKComplicationTemplate? {
        switch family {
        case .circularSmall:
            return ComplicationTemplates.createSampleTokenPriceCircularSmall()
        case .modularSmall:
            return ComplicationTemplates.createSampleTokenPriceModularSmall()
        case .utilitarianSmall:
            return ComplicationTemplates.createSampleWalletBalanceUtilitarianSmall()
        case .graphicCorner:
            return ComplicationTemplates.createSampleTokenPriceGraphicCorner()
        case .graphicCircular:
            return ComplicationTemplates.createSampleTokenPriceGraphicCircular()
        default:
            return nil
        }
    }
    
    // MARK: - Helper Methods
    
    private func formatPrice(_ price: Double) -> String {
        if price >= 10000 {
            return String(format: "%.1fK", price / 1000)
        } else if price >= 1 {
            return String(format: "%.0f", price)
        } else {
            return String(format: "%.3f", price)
        }
    }
}

// MARK: - Supporting Types

enum ComplicationError: LocalizedError {
    case unsupportedFamily
    case dataNotAvailable
    case networkError
    
    var errorDescription: String? {
        switch self {
        case .unsupportedFamily:
            return "不支援的 Complication 類型"
        case .dataNotAvailable:
            return "數據不可用"
        case .networkError:
            return "網路錯誤"
        }
    }
}

// Extension for clamping values
extension Comparable {
    func clamped(to limits: ClosedRange<Self>) -> Self {
        return min(max(self, limits.lowerBound), limits.upperBound)
    }
}