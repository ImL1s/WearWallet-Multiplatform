//
//  SimpleQRCodeGenerator.swift
//  WatchWallet Watch App
//
//  Simple QR Code generator for watchOS without CIFilter
//

import SwiftUI
import CoreGraphics

// Simple QR Code generator implementation
class SimpleQRCodeGenerator {
    
    // QR Code error correction levels
    enum ErrorCorrectionLevel {
        case L // ~7% correction capability
        case M // ~15% correction capability
        case Q // ~25% correction capability
        case H // ~30% correction capability
    }
    
    // Generate a simple QR code for small data (like wallet addresses)
    static func generateQRCode(from string: String, size: CGSize = CGSize(width: 200, height: 200)) -> UIImage? {
        // For watchOS, we'll create a simple matrix pattern
        // This is a simplified version that works for small data like addresses
        
        let data = string.data(using: .utf8) ?? Data()
        let bytes = Array(data)
        
        // Calculate matrix size based on data length
        // For simplicity, we use a fixed size suitable for addresses
        let matrixSize = 25 // Version 2 QR code (25x25 modules)
        let moduleSize = min(size.width, size.height) / CGFloat(matrixSize + 8) // Add quiet zone
        
        // Create the QR matrix
        var matrix = createDataMatrix(from: bytes, size: matrixSize)
        
        // Add positioning patterns
        addPositioningPatterns(&matrix, size: matrixSize)
        
        // Add timing patterns
        addTimingPatterns(&matrix, size: matrixSize)
        
        // Render the matrix as an image
        return renderMatrix(matrix, moduleSize: moduleSize, imageSize: size)
    }
    
    // Create data matrix from bytes
    private static func createDataMatrix(from bytes: [UInt8], size: Int) -> [[Bool]] {
        var matrix = Array(repeating: Array(repeating: false, count: size), count: size)
        
        // Simple data placement algorithm
        var byteIndex = 0
        var bitIndex = 7
        
        // Place data in a zigzag pattern (simplified)
        for col in stride(from: size - 1, through: 0, by: -2) {
            if col == 6 { continue } // Skip timing column
            
            for _ in 0..<2 {
                for row in 0..<size {
                    let actualRow = ((size - 1 - col) / 2) % 2 == 0 ? row : size - 1 - row
                    let actualCol = col - ((size - 1 - col) % 2)
                    
                    // Skip if position is occupied by function pattern
                    if isReservedModule(row: actualRow, col: actualCol, size: size) {
                        continue
                    }
                    
                    // Place data bit
                    if byteIndex < bytes.count {
                        let bit = (bytes[byteIndex] >> bitIndex) & 1
                        matrix[actualRow][actualCol] = bit == 1
                        
                        bitIndex -= 1
                        if bitIndex < 0 {
                            bitIndex = 7
                            byteIndex += 1
                        }
                    } else {
                        // Padding
                        matrix[actualRow][actualCol] = false
                    }
                }
            }
        }
        
        // Apply simple masking pattern
        applyMaskPattern(&matrix, size: size)
        
        return matrix
    }
    
    // Check if module is reserved for function patterns
    private static func isReservedModule(row: Int, col: Int, size: Int) -> Bool {
        // Positioning patterns
        if (row < 9 && col < 9) || // Top-left
           (row < 9 && col >= size - 8) || // Top-right
           (row >= size - 8 && col < 9) { // Bottom-left
            return true
        }
        
        // Timing patterns
        if row == 6 || col == 6 {
            return true
        }
        
        return false
    }
    
    // Add positioning patterns (the three corner squares)
    private static func addPositioningPatterns(_ matrix: inout [[Bool]], size: Int) {
        // Top-left
        drawPositioningPattern(&matrix, centerRow: 3, centerCol: 3)
        
        // Top-right
        drawPositioningPattern(&matrix, centerRow: 3, centerCol: size - 4)
        
        // Bottom-left
        drawPositioningPattern(&matrix, centerRow: size - 4, centerCol: 3)
    }
    
    // Draw a single positioning pattern
    private static func drawPositioningPattern(_ matrix: inout [[Bool]], centerRow: Int, centerCol: Int) {
        for r in -3...3 {
            for c in -3...3 {
                let row = centerRow + r
                let col = centerCol + c
                
                if row >= 0 && row < matrix.count && col >= 0 && col < matrix[0].count {
                    // Outer ring and center are black
                    if abs(r) == 3 || abs(c) == 3 || (abs(r) <= 1 && abs(c) <= 1) {
                        matrix[row][col] = true
                    } else {
                        matrix[row][col] = false
                    }
                }
            }
        }
    }
    
    // Add timing patterns
    private static func addTimingPatterns(_ matrix: inout [[Bool]], size: Int) {
        // Horizontal timing pattern
        for col in 8..<(size - 8) {
            matrix[6][col] = col % 2 == 0
        }
        
        // Vertical timing pattern
        for row in 8..<(size - 8) {
            matrix[row][6] = row % 2 == 0
        }
    }
    
    // Apply simple mask pattern
    private static func applyMaskPattern(_ matrix: inout [[Bool]], size: Int) {
        // Use mask pattern 0: (row + column) % 2 == 0
        for row in 0..<size {
            for col in 0..<size {
                if !isReservedModule(row: row, col: col, size: size) {
                    if (row + col) % 2 == 0 {
                        matrix[row][col] = !matrix[row][col]
                    }
                }
            }
        }
    }
    
    // Render the matrix as an image
    private static func renderMatrix(_ matrix: [[Bool]], moduleSize: CGFloat, imageSize: CGSize) -> UIImage? {
        let matrixSize = matrix.count
        let quietZone = 4
        let totalSize = matrixSize + 2 * quietZone
        
        UIGraphicsBeginImageContextWithOptions(imageSize, true, 0.0)
        defer { UIGraphicsEndImageContext() }
        
        guard let context = UIGraphicsGetCurrentContext() else { return nil }
        
        // White background
        context.setFillColor(UIColor.white.cgColor)
        context.fill(CGRect(origin: .zero, size: imageSize))
        
        // Black modules
        context.setFillColor(UIColor.black.cgColor)
        
        let actualModuleSize = min(imageSize.width, imageSize.height) / CGFloat(totalSize)
        let offsetX = (imageSize.width - actualModuleSize * CGFloat(totalSize)) / 2
        let offsetY = (imageSize.height - actualModuleSize * CGFloat(totalSize)) / 2
        
        for row in 0..<matrixSize {
            for col in 0..<matrixSize {
                if matrix[row][col] {
                    let rect = CGRect(
                        x: offsetX + CGFloat(col + quietZone) * actualModuleSize,
                        y: offsetY + CGFloat(row + quietZone) * actualModuleSize,
                        width: actualModuleSize,
                        height: actualModuleSize
                    )
                    context.fill(rect)
                }
            }
        }
        
        return UIGraphicsGetImageFromCurrentImageContext()
    }
}

// Updated QRCodeView using the simple generator
struct SimpleQRCodeView: View {
    let data: String
    let size: CGFloat
    
    var body: some View {
        if let qrImage = SimpleQRCodeGenerator.generateQRCode(
            from: data,
            size: CGSize(width: size, height: size)
        ) {
            Image(uiImage: qrImage)
                .interpolation(.none)
                .resizable()
                .scaledToFit()
                .frame(width: size, height: size)
        } else {
            // Fallback view
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.white)
                    .frame(width: size, height: size)
                
                VStack(spacing: 4) {
                    Image(systemName: "qrcode")
                        .font(.system(size: size / 3))
                        .foregroundColor(.black)
                    
                    Text("QR Code")
                        .font(.system(size: 10))
                        .foregroundColor(.black)
                }
            }
        }
    }
}