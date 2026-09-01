//
//  AddressBookSettingsView.swift
//  WatchWallet Watch App
//
//  地址簿設定視圖 - 管理地址簿的導入導出和設定
//  Created: 2025-08-07
//

import SwiftUI

/**
 * 地址簿設定視圖
 * 
 * 提供地址簿的導入、導出、備份和其他設定選項
 */
struct AddressBookSettingsView: View {
    @ObservedObject var viewModel: AddressBookViewModel
    @Environment(\.dismiss) private var dismiss
    
    @State private var showingImportOptions = false
    @State private var showingExportOptions = false
    @State private var showingClearConfirm = false
    @State private var showingSortOptions = false
    @State private var showingMockDataConfirm = false
    @State private var exportData: Data?
    @State private var showingShareSheet = false
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // 統計資訊
                    statisticsSection
                    
                    // 資料管理
                    dataManagementSection
                    
                    // 顯示設定
                    displaySettingsSection
                    
                    // 開發者選項
                    #if DEBUG
                    developerSection
                    #endif
                }
                .padding()
            }
            .navigationTitle("地址簿設定")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") {
                        dismiss()
                    }
                }
            }
            .sheet(isPresented: $showingImportOptions) {
                ImportOptionsView(viewModel: viewModel)
            }
            .sheet(isPresented: $showingExportOptions) {
                ExportOptionsView(viewModel: viewModel) { data in
                    self.exportData = data
                    self.showingShareSheet = true
                }
            }
            .sheet(isPresented: $showingSortOptions) {
                SortOptionsView(selectedOption: $viewModel.sortOption)
            }
            .alert("清除所有聯絡人", isPresented: $showingClearConfirm) {
                Button("取消", role: .cancel) { }
                Button("清除", role: .destructive) {
                    Task {
                        await viewModel.clearAllContacts()
                    }
                }
            } message: {
                Text("此操作將刪除所有聯絡人資料，且無法復原。確定要繼續嗎？")
            }
            .alert("生成測試資料", isPresented: $showingMockDataConfirm) {
                Button("取消", role: .cancel) { }
                Button("生成") {
                    Task {
                        await viewModel.generateMockData()
                    }
                }
            } message: {
                Text("這將創建一些測試用的聯絡人資料，是否繼續？")
            }
        }
    }
    
    // MARK: - Statistics Section
    
    private var statisticsSection: some View {
        VStack(spacing: 12) {
            Text("地址簿統計")
                .font(.system(size: 14, weight: .semibold))
                .frame(maxWidth: .infinity, alignment: .leading)
            
            HStack(spacing: 12) {
                StatCard(
                    title: "總聯絡人",
                    value: "\(viewModel.contacts.count)",
                    icon: "person.2",
                    color: .blue
                )
                
                StatCard(
                    title: "收藏",
                    value: "\(viewModel.favoriteContacts.count)",
                    icon: "star.fill",
                    color: .yellow
                )
            }
            
            HStack(spacing: 12) {
                StatCard(
                    title: "區塊鏈",
                    value: "\(viewModel.allChainTypes.count)",
                    icon: "link",
                    color: .green
                )
                
                StatCard(
                    title: "標籤",
                    value: "\(viewModel.allTags.count)",
                    icon: "tag",
                    color: .purple
                )
            }
        }
    }
    
    // MARK: - Data Management Section
    
    private var dataManagementSection: some View {
        VStack(spacing: 12) {
            Text("資料管理")
                .font(.system(size: 14, weight: .semibold))
                .frame(maxWidth: .infinity, alignment: .leading)
            
            // 導入聯絡人
            Button(action: { showingImportOptions = true }) {
                HStack {
                    Image(systemName: "square.and.arrow.down")
                        .frame(width: 20)
                    Text("導入聯絡人")
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                }
                .padding(.vertical, 8)
                .padding(.horizontal, 12)
                .background(Color.gray.opacity(0.2))
                .cornerRadius(8)
            }
            .buttonStyle(.plain)
            
            // 導出聯絡人
            Button(action: { showingExportOptions = true }) {
                HStack {
                    Image(systemName: "square.and.arrow.up")
                        .frame(width: 20)
                    Text("導出聯絡人")
                    Spacer()
                    Text("\(viewModel.contacts.count)")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                }
                .padding(.vertical, 8)
                .padding(.horizontal, 12)
                .background(Color.gray.opacity(0.2))
                .cornerRadius(8)
            }
            .buttonStyle(.plain)
            .disabled(viewModel.contacts.isEmpty)
            
            // 清除所有資料
            Button(action: { showingClearConfirm = true }) {
                HStack {
                    Image(systemName: "trash")
                        .frame(width: 20)
                        .foregroundColor(.red)
                    Text("清除所有聯絡人")
                        .foregroundColor(.red)
                    Spacer()
                }
                .padding(.vertical, 8)
                .padding(.horizontal, 12)
                .background(Color.gray.opacity(0.2))
                .cornerRadius(8)
            }
            .buttonStyle(.plain)
            .disabled(viewModel.contacts.isEmpty)
        }
    }
    
    // MARK: - Display Settings Section
    
    private var displaySettingsSection: some View {
        VStack(spacing: 12) {
            Text("顯示設定")
                .font(.system(size: 14, weight: .semibold))
                .frame(maxWidth: .infinity, alignment: .leading)
            
            // 排序方式
            Button(action: { showingSortOptions = true }) {
                HStack {
                    Image(systemName: viewModel.sortOption.icon)
                        .frame(width: 20)
                    Text("排序方式")
                    Spacer()
                    Text(viewModel.sortOption.rawValue)
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                }
                .padding(.vertical, 8)
                .padding(.horizontal, 12)
                .background(Color.gray.opacity(0.2))
                .cornerRadius(8)
            }
            .buttonStyle(.plain)
            
            // 清除篩選
            Button(action: { viewModel.clearFilters() }) {
                HStack {
                    Image(systemName: "xmark.circle")
                        .frame(width: 20)
                    Text("清除所有篩選")
                    Spacer()
                }
                .padding(.vertical, 8)
                .padding(.horizontal, 12)
                .background(Color.gray.opacity(0.2))
                .cornerRadius(8)
            }
            .buttonStyle(.plain)
            .disabled(viewModel.searchText.isEmpty && 
                     viewModel.selectedChainFilter == nil && 
                     viewModel.selectedTagFilter == nil)
        }
    }
    
    // MARK: - Developer Section
    
    #if DEBUG
    private var developerSection: some View {
        VStack(spacing: 12) {
            Text("開發者選項")
                .font(.system(size: 14, weight: .semibold))
                .frame(maxWidth: .infinity, alignment: .leading)
            
            Button(action: { showingMockDataConfirm = true }) {
                HStack {
                    Image(systemName: "hammer")
                        .frame(width: 20)
                    Text("生成測試資料")
                    Spacer()
                }
                .padding(.vertical, 8)
                .padding(.horizontal, 12)
                .background(Color.gray.opacity(0.2))
                .cornerRadius(8)
            }
            .buttonStyle(.plain)
        }
    }
    #endif
}

// MARK: - Stat Card Component

struct StatCard: View {
    let title: String
    let value: String
    let icon: String
    let color: Color
    
    var body: some View {
        VStack(spacing: 4) {
            Image(systemName: icon)
                .font(.system(size: 16))
                .foregroundColor(color)
            
            Text(value)
                .font(.system(size: 18, weight: .bold))
            
            Text(title)
                .font(.system(size: 10))
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .background(Color.gray.opacity(0.2))
        .cornerRadius(10)
    }
}

// MARK: - Import Options View

struct ImportOptionsView: View {
    @ObservedObject var viewModel: AddressBookViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var importMethod = ImportMethod.file
    @State private var showingFilePicker = false
    
    enum ImportMethod: String, CaseIterable {
        case file = "從檔案"
        case iphone = "從 iPhone"
        case icloud = "從 iCloud"
        
        var icon: String {
            switch self {
            case .file: return "doc"
            case .iphone: return "iphone"
            case .icloud: return "icloud"
            }
        }
        
        var description: String {
            switch self {
            case .file: return "導入 JSON 格式的聯絡人檔案"
            case .iphone: return "從配對的 iPhone 同步聯絡人"
            case .icloud: return "從 iCloud 備份還原聯絡人"
            }
        }
    }
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text("選擇導入來源")
                    .font(.system(size: 14, weight: .semibold))
                    .frame(maxWidth: .infinity, alignment: .leading)
                
                ForEach(ImportMethod.allCases, id: \.self) { method in
                    Button(action: {
                        importMethod = method
                        performImport()
                    }) {
                        HStack(spacing: 12) {
                            Image(systemName: method.icon)
                                .font(.system(size: 20))
                                .frame(width: 30)
                            
                            VStack(alignment: .leading, spacing: 2) {
                                Text(method.rawValue)
                                    .font(.system(size: 14, weight: .medium))
                                
                                Text(method.description)
                                    .font(.system(size: 11))
                                    .foregroundColor(.secondary)
                            }
                            
                            Spacer()
                            
                            if importMethod == method {
                                Image(systemName: "checkmark")
                                    .foregroundColor(.blue)
                            }
                        }
                        .padding(12)
                        .background(Color.gray.opacity(0.2))
                        .cornerRadius(10)
                    }
                    .buttonStyle(.plain)
                }
                
                Spacer()
                
                Button("取消") {
                    dismiss()
                }
                .buttonStyle(.bordered)
            }
            .padding()
            .navigationTitle("導入聯絡人")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
    
    private func performImport() {
        switch importMethod {
        case .file:
            // TODO: 實現檔案選擇器
            showingFilePicker = true
        case .iphone:
            // TODO: 實現 iPhone 同步
            viewModel.successMessage = "iPhone 同步功能開發中"
        case .icloud:
            // TODO: 實現 iCloud 同步
            viewModel.successMessage = "iCloud 同步功能開發中"
        }
        dismiss()
    }
}

// MARK: - Export Options View

struct ExportOptionsView: View {
    @ObservedObject var viewModel: AddressBookViewModel
    let onExport: (Data) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var exportMethod = ExportMethod.json
    @State private var isExporting = false
    
    enum ExportMethod: String, CaseIterable {
        case json = "JSON 格式"
        case csv = "CSV 格式"
        case vcard = "vCard 格式"
        
        var icon: String {
            switch self {
            case .json: return "doc.text"
            case .csv: return "tablecells"
            case .vcard: return "person.crop.square"
            }
        }
        
        var description: String {
            switch self {
            case .json: return "可重新導入的完整資料格式"
            case .csv: return "適用於 Excel 等試算表軟體"
            case .vcard: return "標準通訊錄格式"
            }
        }
    }
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text("選擇導出格式")
                    .font(.system(size: 14, weight: .semibold))
                    .frame(maxWidth: .infinity, alignment: .leading)
                
                Text("將導出 \(viewModel.contacts.count) 個聯絡人")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                
                ForEach(ExportMethod.allCases, id: \.self) { method in
                    Button(action: {
                        exportMethod = method
                    }) {
                        HStack(spacing: 12) {
                            Image(systemName: method.icon)
                                .font(.system(size: 20))
                                .frame(width: 30)
                            
                            VStack(alignment: .leading, spacing: 2) {
                                Text(method.rawValue)
                                    .font(.system(size: 14, weight: .medium))
                                
                                Text(method.description)
                                    .font(.system(size: 11))
                                    .foregroundColor(.secondary)
                            }
                            
                            Spacer()
                            
                            if exportMethod == method {
                                Image(systemName: "checkmark")
                                    .foregroundColor(.blue)
                            }
                        }
                        .padding(12)
                        .background(Color.gray.opacity(0.2))
                        .cornerRadius(10)
                    }
                    .buttonStyle(.plain)
                }
                
                Spacer()
                
                Button(action: performExport) {
                    HStack {
                        if isExporting {
                            ProgressView()
                                .scaleEffect(0.8)
                        } else {
                            Image(systemName: "square.and.arrow.up")
                        }
                        Text("導出")
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(isExporting)
                
                Button("取消") {
                    dismiss()
                }
                .buttonStyle(.bordered)
            }
            .padding()
            .navigationTitle("導出聯絡人")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
    
    private func performExport() {
        isExporting = true
        
        Task {
            // 目前只支援 JSON 格式
            if let data = await viewModel.exportContacts() {
                onExport(data)
                dismiss()
            }
            isExporting = false
        }
    }
}

// MARK: - Sort Options View

struct SortOptionsView: View {
    @Binding var selectedOption: SortOption
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationStack {
            List(SortOption.allCases, id: \.self) { option in
                Button(action: {
                    selectedOption = option
                    dismiss()
                }) {
                    HStack {
                        Image(systemName: option.icon)
                            .frame(width: 20)
                        
                        Text(option.rawValue)
                            .font(.system(size: 14))
                        
                        Spacer()
                        
                        if selectedOption == option {
                            Image(systemName: "checkmark")
                                .foregroundColor(.blue)
                        }
                    }
                }
                .buttonStyle(.plain)
            }
            .navigationTitle("排序方式")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") {
                        dismiss()
                    }
                }
            }
        }
    }
}

// MARK: - Preview

struct AddressBookSettingsView_Previews: PreviewProvider {
    static var previews: some View {
        AddressBookSettingsView(viewModel: AddressBookViewModel())
    }
}