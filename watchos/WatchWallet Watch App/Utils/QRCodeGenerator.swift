//
//  QRCodeGenerator.swift
//  WatchWallet Watch App
//
//  QR Code generation utility for watchOS
//  Since watchOS doesn't support CIFilter, we need to use alternative methods
//

import SwiftUI
import CoreGraphics

class QRCodeGenerator {
    
    // Generate QR code bitmap for watchOS
    // This creates a visual representation suitable for displaying wallet addresses
    static func generateQRCode(from string: String, size: CGSize = CGSize(width: 200, height: 200)) -> UIImage? {
        // For watchOS, we create a simplified but recognizable pattern
        // In production, integrate a library like QRCodeSwift or dagronf/QRCode
        
        UIGraphicsBeginImageContextWithOptions(size, true, 0.0)
        defer { UIGraphicsEndImageContext() }
        
        guard let context = UIGraphicsGetCurrentContext() else { return nil }
        
        // White background
        context.setFillColor(UIColor.white.cgColor)
        context.fill(CGRect(origin: .zero, size: size))
        
        // Create a visual pattern that represents the address
        // This is a placeholder - for production use a proper QR library
        drawAddressPattern(context: context, address: string, size: size)
        
        return UIGraphicsGetImageFromCurrentImageContext()
    }
    
    private static func drawAddressPattern(context: CGContext, address: String, size: CGSize) {
        context.setFillColor(UIColor.black.cgColor)
        
        // Draw a recognizable pattern with the address info
        let padding: CGFloat = 20
        let availableWidth = size.width - 2 * padding
        let availableHeight = size.height - 2 * padding
        
        // Draw border
        let borderWidth: CGFloat = 8
        context.fill(CGRect(x: padding, y: padding, width: availableWidth, height: borderWidth))
        context.fill(CGRect(x: padding, y: size.height - padding - borderWidth, width: availableWidth, height: borderWidth))
        context.fill(CGRect(x: padding, y: padding, width: borderWidth, height: availableHeight))
        context.fill(CGRect(x: size.width - padding - borderWidth, y: padding, width: borderWidth, height: availableHeight))
        
        // Draw positioning squares (corners)
        let squareSize: CGFloat = 24
        // Top-left
        drawPositioningSquare(context: context, x: padding + borderWidth, y: padding + borderWidth, size: squareSize)
        // Top-right
        drawPositioningSquare(context: context, x: size.width - padding - borderWidth - squareSize, y: padding + borderWidth, size: squareSize)
        // Bottom-left
        drawPositioningSquare(context: context, x: padding + borderWidth, y: size.height - padding - borderWidth - squareSize, size: squareSize)
        
        // Draw center pattern based on address
        let centerX = size.width / 2
        let centerY = size.height / 2
        
        // Display address info in center
        let attributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: 10, weight: .medium),
            .foregroundColor: UIColor.black
        ]
        
        let displayText = "Wallet QR\n\(String(address.prefix(6)))...\(String(address.suffix(4)))"
        let textSize = displayText.size(withAttributes: attributes)
        
        let textRect = CGRect(
            x: centerX - textSize.width / 2,
            y: centerY - textSize.height / 2,
            width: textSize.width,
            height: textSize.height
        )
        
        // White background for text
        context.setFillColor(UIColor.white.cgColor)
        context.fill(textRect.insetBy(dx: -4, dy: -4))
        
        // Draw text
        displayText.draw(in: textRect, withAttributes: attributes)
    }
    
    private static func drawPositioningSquare(context: CGContext, x: CGFloat, y: CGFloat, size: CGFloat) {
        // Outer black square
        context.setFillColor(UIColor.black.cgColor)
        context.fill(CGRect(x: x, y: y, width: size, height: size))
        
        // Inner white square
        let innerMargin: CGFloat = 6
        context.setFillColor(UIColor.white.cgColor)
        context.fill(CGRect(x: x + innerMargin, y: y + innerMargin, 
                           width: size - 2 * innerMargin, height: size - 2 * innerMargin))
        
        // Center black square
        let centerMargin: CGFloat = 10
        context.setFillColor(UIColor.black.cgColor)
        context.fill(CGRect(x: x + centerMargin, y: y + centerMargin,
                           width: size - 2 * centerMargin, height: size - 2 * centerMargin))
    }
    
    // Helper function to determine if a module should be part of corner markers
    private static func isCornerModule(row: Int, col: Int, modules: Int) -> Bool {
        let cornerSize = 7
        
        // Top-left corner
        if row < cornerSize && col < cornerSize {
            return row == 0 || row == cornerSize - 1 || col == 0 || col == cornerSize - 1 ||
                   (row >= 2 && row <= 4 && col >= 2 && col <= 4)
        }
        
        // Top-right corner
        if row < cornerSize && col >= modules - cornerSize {
            let adjustedCol = col - (modules - cornerSize)
            return row == 0 || row == cornerSize - 1 || adjustedCol == 0 || adjustedCol == cornerSize - 1 ||
                   (row >= 2 && row <= 4 && adjustedCol >= 2 && adjustedCol <= 4)
        }
        
        // Bottom-left corner
        if row >= modules - cornerSize && col < cornerSize {
            let adjustedRow = row - (modules - cornerSize)
            return adjustedRow == 0 || adjustedRow == cornerSize - 1 || col == 0 || col == cornerSize - 1 ||
                   (adjustedRow >= 2 && adjustedRow <= 4 && col >= 2 && col <= 4)
        }
        
        return false
    }
    
    // Add corner markers to make it look more like a QR code
    private static func addCornerMarkers(context: CGContext, size: CGSize, moduleSize: CGFloat) {
        context.setFillColor(UIColor.black.cgColor)
        
        let markerSize: CGFloat = 7 * moduleSize
        let innerMarkerSize: CGFloat = 3 * moduleSize
        let innerOffset: CGFloat = 2 * moduleSize
        
        // Top-left marker
        drawCornerMarker(
            context: context,
            x: 0,
            y: 0,
            outerSize: markerSize,
            innerSize: innerMarkerSize,
            innerOffset: innerOffset
        )
        
        // Top-right marker
        drawCornerMarker(
            context: context,
            x: size.width - markerSize,
            y: 0,
            outerSize: markerSize,
            innerSize: innerMarkerSize,
            innerOffset: innerOffset
        )
        
        // Bottom-left marker
        drawCornerMarker(
            context: context,
            x: 0,
            y: size.height - markerSize,
            outerSize: markerSize,
            innerSize: innerMarkerSize,
            innerOffset: innerOffset
        )
    }
    
    private static func drawCornerMarker(context: CGContext, x: CGFloat, y: CGFloat, outerSize: CGFloat, innerSize: CGFloat, innerOffset: CGFloat) {
        // Outer square
        context.setFillColor(UIColor.black.cgColor)
        context.fill(CGRect(x: x, y: y, width: outerSize, height: outerSize))
        
        // White inner square
        context.setFillColor(UIColor.white.cgColor)
        context.fill(CGRect(x: x + innerOffset/2, y: y + innerOffset/2, width: outerSize - innerOffset, height: outerSize - innerOffset))
        
        // Black center square
        context.setFillColor(UIColor.black.cgColor)
        context.fill(CGRect(x: x + innerOffset, y: y + innerOffset, width: innerSize, height: innerSize))
    }
}

// SwiftUI View for displaying QR codes
struct QRCodeView: View {
    let data: String
    let size: CGFloat
    @State private var showExplanation = false
    
    var body: some View {
        VStack(spacing: 8) {
            if let qrImage = QRCodeGenerator.generateQRCode(
                from: data,
                size: CGSize(width: size, height: size)
            ) {
                Image(uiImage: qrImage)
                    .interpolation(.none)
                    .resizable()
                    .scaledToFit()
                    .frame(width: size, height: size)
                    .onTapGesture {
                        showExplanation.toggle()
                    }
            } else {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.gray.opacity(0.3))
                    .frame(width: size, height: size)
                    .overlay(
                        VStack {
                            Image(systemName: "qrcode")
                                .font(.system(size: size / 3))
                                .foregroundColor(.gray)
                            Text("QR Code")
                                .font(.system(size: 10))
                                .foregroundColor(.gray)
                        }
                    )
            }
            
            if showExplanation {
                Text("請使用 iPhone 掃描")
                    .font(.system(size: 10))
                    .foregroundColor(.orange)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.3), value: showExplanation)
    }
}

// Note for production use
struct QRCodeGeneratorNote: View {
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "info.circle")
                .font(.system(size: 20))
                .foregroundColor(.blue)
            
            Text("QR Code 說明")
                .font(.system(size: 14, weight: .semibold))
            
            Text("由於 watchOS 限制，無法直接掃描 QR Code。請使用配對的 iPhone 應用程式進行掃描。")
                .font(.system(size: 11))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
        }
        .padding()
        .background(Color.blue.opacity(0.1))
        .cornerRadius(8)
    }
}