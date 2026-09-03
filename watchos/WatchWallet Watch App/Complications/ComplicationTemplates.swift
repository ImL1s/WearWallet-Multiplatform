//
//  ComplicationTemplates.swift
//  WatchWallet Watch App
//
//  Complication 模板生成器
//  提供不同類型的模板創建方法
//

import ClockKit
import SwiftUI

struct ComplicationTemplates {
    
    // MARK: - Token Price Templates
    
    static func createTokenPriceCircularSmall(data: ComplicationTokenData) -> CLKComplicationTemplateCircularSmallSimpleText {
        let text = data.isDataValid ? data.formattedPrice : "N/A"
        return CLKComplicationTemplateCircularSmallSimpleText(
            textProvider: CLKSimpleTextProvider(text: text)
        )
    }
    
    static func createTokenPriceModularSmall(data: ComplicationTokenData) -> CLKComplicationTemplateModularSmallColumnsText {
        let priceText = data.isDataValid ? "$\(data.formattedPrice)" : "N/A"
        let changeText = data.isDataValid ? data.formattedChange : "N/A"
        
        return CLKComplicationTemplateModularSmallColumnsText(
            row1Column1TextProvider: CLKSimpleTextProvider(text: data.symbol),
            row1Column2TextProvider: CLKSimpleTextProvider(text: priceText),
            row2Column1TextProvider: CLKSimpleTextProvider(text: "24h"),
            row2Column2TextProvider: CLKSimpleTextProvider(text: changeText)
        )
    }
    
    static func createTokenPriceGraphicCircular(data: ComplicationTokenData) -> CLKComplicationTemplateGraphicCircularOpenGaugeSimpleText {
        let centerText = data.isDataValid ? "$\(data.formattedPrice)" : "N/A"
        var gaugeValue: Float = 0.5 // 預設中間值
        var gaugeColor: UIColor = .blue
        
        // 根據24小時變化率設定gauge值和顏色
        if let change = data.priceChange24h {
            // 將 -10% 到 +10% 映射到 0.0 到 1.0
            gaugeValue = Float((change + 10) / 20).clamped(to: 0...1)
            gaugeColor = change >= 0 ? .green : .red
        }
        
        return CLKComplicationTemplateGraphicCircularOpenGaugeSimpleText(
            gaugeProvider: CLKSimpleGaugeProvider(
                style: .fill,
                gaugeColor: gaugeColor,
                fillFraction: gaugeValue
            ),
            bottomTextProvider: CLKSimpleTextProvider(text: data.symbol),
            centerTextProvider: CLKSimpleTextProvider(text: centerText)
        )
    }
    
    static func createTokenPriceGraphicCorner(data: ComplicationTokenData) -> CLKComplicationTemplateGraphicCornerTextImage {
        let text = data.isDataValid ? "$\(data.formattedPrice)" : "N/A"
        
        // 根據變化率選擇趨勢圖示
        let imageName: String
        if let change = data.priceChange24h {
            imageName = change >= 0 ? "chart.line.uptrend.xyaxis" : "chart.line.downtrend.xyaxis"
        } else {
            imageName = "chart.line.flattrend.xyaxis"
        }
        
        let image = UIImage(systemName: imageName) ?? UIImage(systemName: "chart.xyaxis.line")!
        
        return CLKComplicationTemplateGraphicCornerTextImage(
            textProvider: CLKSimpleTextProvider(text: text),
            imageProvider: CLKFullColorImageProvider(fullColorImage: image)
        )
    }
    
    // MARK: - Wallet Balance Templates
    
    static func createWalletBalanceUtilitarianSmall(data: ComplicationWalletData) -> CLKComplicationTemplateUtilitarianSmallFlat {
        let text = data.isDataValid ? "$\(data.formattedUsdValue)" : "錢包"
        
        return CLKComplicationTemplateUtilitarianSmallFlat(
            textProvider: CLKSimpleTextProvider(text: text)
        )
    }
    
    static func createWalletBalanceModularSmall(data: ComplicationWalletData) -> CLKComplicationTemplateModularSmallColumnsText {
        let usdText = data.isDataValid ? "$\(data.formattedUsdValue)" : "N/A"
        let balanceText = data.formattedBalance
        
        return CLKComplicationTemplateModularSmallColumnsText(
            row1Column1TextProvider: CLKSimpleTextProvider(text: "錢包"),
            row1Column2TextProvider: CLKSimpleTextProvider(text: usdText),
            row2Column1TextProvider: CLKSimpleTextProvider(text: data.nativeSymbol),
            row2Column2TextProvider: CLKSimpleTextProvider(text: balanceText)
        )
    }
    
    static func createWalletBalanceGraphicCircular(data: ComplicationWalletData) -> CLKComplicationTemplateGraphicCircularOpenGaugeSimpleText {
        let centerText = data.isDataValid ? "$\(data.formattedUsdValue)" : "N/A"
        
        // 錢包餘額的 gauge 顯示相對數量（這裡簡化為固定值）
        let gaugeValue: Float = data.isDataValid ? 0.7 : 0.0
        
        return CLKComplicationTemplateGraphicCircularOpenGaugeSimpleText(
            gaugeProvider: CLKSimpleGaugeProvider(
                style: .fill,
                gaugeColor: .blue,
                fillFraction: gaugeValue
            ),
            bottomTextProvider: CLKSimpleTextProvider(text: "錢包"),
            centerTextProvider: CLKSimpleTextProvider(text: centerText)
        )
    }
    
    // MARK: - Error Templates
    
    static func createErrorCircularSmall(message: String = "錯誤") -> CLKComplicationTemplateCircularSmallSimpleText {
        return CLKComplicationTemplateCircularSmallSimpleText(
            textProvider: CLKSimpleTextProvider(text: message)
        )
    }
    
    static func createErrorModularSmall(message: String = "數據不可用") -> CLKComplicationTemplateModularSmallColumnsText {
        return CLKComplicationTemplateModularSmallColumnsText(
            row1Column1TextProvider: CLKSimpleTextProvider(text: "WW"),
            row1Column2TextProvider: CLKSimpleTextProvider(text: "錯誤"),
            row2Column1TextProvider: CLKSimpleTextProvider(text: ""),
            row2Column2TextProvider: CLKSimpleTextProvider(text: "")
        )
    }
    
    static func createErrorUtilitarianSmall(message: String = "錯誤") -> CLKComplicationTemplateUtilitarianSmallFlat {
        return CLKComplicationTemplateUtilitarianSmallFlat(
            textProvider: CLKSimpleTextProvider(text: message)
        )
    }
    
    static func createErrorGraphicCircular(message: String = "錯誤") -> CLKComplicationTemplateGraphicCircularOpenGaugeSimpleText {
        return CLKComplicationTemplateGraphicCircularOpenGaugeSimpleText(
            gaugeProvider: CLKSimpleGaugeProvider(
                style: .fill,
                gaugeColor: .gray,
                fillFraction: 0.0
            ),
            bottomTextProvider: CLKSimpleTextProvider(text: "WW"),
            centerTextProvider: CLKSimpleTextProvider(text: message)
        )
    }
    
    static func createErrorGraphicCorner(message: String = "錯誤") -> CLKComplicationTemplateGraphicCornerTextImage {
        let image = UIImage(systemName: "exclamationmark.triangle") ?? UIImage()
        
        return CLKComplicationTemplateGraphicCornerTextImage(
            textProvider: CLKSimpleTextProvider(text: message),
            imageProvider: CLKFullColorImageProvider(fullColorImage: image)
        )
    }
    
    // MARK: - Sample Templates (預覽用)
    
    static func createSampleTokenPriceCircularSmall() -> CLKComplicationTemplateCircularSmallSimpleText {
        return CLKComplicationTemplateCircularSmallSimpleText(
            textProvider: CLKSimpleTextProvider(text: "$3.2K")
        )
    }
    
    static func createSampleTokenPriceModularSmall() -> CLKComplicationTemplateModularSmallColumnsText {
        return CLKComplicationTemplateModularSmallColumnsText(
            row1Column1TextProvider: CLKSimpleTextProvider(text: "ETH"),
            row1Column2TextProvider: CLKSimpleTextProvider(text: "$3,250"),
            row2Column1TextProvider: CLKSimpleTextProvider(text: "24h"),
            row2Column2TextProvider: CLKSimpleTextProvider(text: "+2.3%")
        )
    }
    
    static func createSampleWalletBalanceUtilitarianSmall() -> CLKComplicationTemplateUtilitarianSmallFlat {
        return CLKComplicationTemplateUtilitarianSmallFlat(
            textProvider: CLKSimpleTextProvider(text: "$10.5K")
        )
    }
    
    static func createSampleTokenPriceGraphicCircular() -> CLKComplicationTemplateGraphicCircularOpenGaugeSimpleText {
        return CLKComplicationTemplateGraphicCircularOpenGaugeSimpleText(
            gaugeProvider: CLKSimpleGaugeProvider(
                style: .fill,
                gaugeColor: .green,
                fillFraction: 0.65
            ),
            bottomTextProvider: CLKSimpleTextProvider(text: "ETH"),
            centerTextProvider: CLKSimpleTextProvider(text: "$3.2K")
        )
    }
    
    static func createSampleTokenPriceGraphicCorner() -> CLKComplicationTemplateGraphicCornerTextImage {
        let image = UIImage(systemName: "chart.line.uptrend.xyaxis") ?? UIImage()
        
        return CLKComplicationTemplateGraphicCornerTextImage(
            textProvider: CLKSimpleTextProvider(text: "$3,250"),
            imageProvider: CLKFullColorImageProvider(fullColorImage: image)
        )
    }
}

// MARK: - Extensions

extension Float {
    func clamped(to limits: ClosedRange<Float>) -> Float {
        return min(max(self, limits.lowerBound), limits.upperBound)
    }
}

extension CLKComplicationTemplate {
    
    /**
     * 為模板設定點擊動作（打開 WearWallet 應用）
     */
    func withAppLaunchAction() -> CLKComplicationTemplate {
        // 注意：在實際實現中，這裡需要創建 deep link 到特定頁面
        // 例如：wearwallet://complication?type=token_price
        
        // 由於 CLKComplicationTemplate 不直接支援點擊動作，
        // 需要在 Watch Face 層級處理用戶點擊
        return self
    }
}

// MARK: - Complication Types Enum

enum ComplicationDisplayType {
    case tokenPrice
    case walletBalance
    case gasPrice
    case portfolio
    
    var displayName: String {
        switch self {
        case .tokenPrice:
            return "代幣價格"
        case .walletBalance:
            return "錢包餘額"
        case .gasPrice:
            return "Gas 費用"
        case .portfolio:
            return "投資組合"
        }
    }
}