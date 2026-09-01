//
//  WatchOSNumericKeypad.swift
//  WatchWallet Watch App
//
//  Created for WearWallet
//

import SwiftUI
import WatchKit

struct WatchOSNumericKeypad: View {
    @Binding var text: String
    var showDecimal: Bool = true
    
    private let columns = [
        GridItem(.flexible()),
        GridItem(.flexible()),
        GridItem(.flexible())
    ]
    
    var body: some View {
        LazyVGrid(columns: columns, spacing: 6) {
            ForEach(1...9, id: \.self) { number in
                Button(action: {
                    WKInterfaceDevice.current().play(.click)
                    self.text += "\(number)"
                }) {
                    Text("\(number)")
                        .font(.title2)
                        .fontWeight(.medium)
                        .frame(maxWidth: .infinity, minHeight: 44) // HIG min target size
                }
                .buttonStyle(.bordered)
            }
            
            // Decimal Point
            Button(action: {
                WKInterfaceDevice.current().play(.click)
                if showDecimal && !text.contains(".") {
                    if text.isEmpty {
                        text = "0."
                    } else {
                        text += "."
                    }
                }
            }) {
                Text(".")
                    .font(.title2)
                    .fontWeight(.bold)
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.bordered)
            .disabled(!showDecimal)
            .opacity(showDecimal ? 1.0 : 0.0)
            
            // Zero
            Button(action: {
                WKInterfaceDevice.current().play(.click)
                if !text.isEmpty {
                    text += "0"
                } else if showDecimal {
                    text = "0"
                }
            }) {
                Text("0")
                    .font(.title2)
                    .fontWeight(.medium)
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.bordered)
            
            // Delete
            Button(action: {
                WKInterfaceDevice.current().play(.click)
                if !text.isEmpty {
                    text.removeLast()
                }
            }) {
                Image(systemName: "delete.left.fill") // Fill icon for better visibility
                    .font(.title3)
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.bordered)
            .tint(.red) // Standard destructive tint
        }
        .padding(.horizontal, 4)
    }
}

struct WatchOSNumericKeypad_Previews: PreviewProvider {
    @State static var text = ""
    static var previews: some View {
        WatchOSNumericKeypad(text: $text)
    }
}
