#!/usr/bin/env python3
"""
最終修復 Koin 模組中的語法問題
"""

import os
import re
from pathlib import Path

def final_fix_module(file_path):
    """最終修復模組文件"""
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    fixed_lines = []
    in_single_or_factory = False
    brace_count = 0
    current_block = []
    
    for line in lines:
        # 檢測 single 或 factory 開始
        if re.match(r'\s*(single|factory)', line):
            in_single_or_factory = True
            brace_count = line.count('{') - line.count('}')
            current_block = [line]
            if brace_count == 0:  # 單行定義
                in_single_or_factory = False
                fixed_lines.append(line)
        elif in_single_or_factory:
            current_block.append(line)
            brace_count += line.count('{') - line.count('}')
            
            if brace_count == 0:  # block 結束
                # 修復這個 block
                block_text = ''.join(current_block)
                
                # 移除多餘的 return
                block_text = re.sub(r'return\s+', '', block_text)
                
                # 修復變量引用
                block_text = re.sub(r'(\w+Monitor|database|migrationManager|migrationCallback|versionMonitor)', r'get()', block_text)
                
                # 修復單行格式
                if block_text.count('\n') <= 2:
                    block_text = re.sub(r'\s*\n\s*', ' ', block_text)
                    block_text = re.sub(r'\s+}', ' }', block_text)
                
                fixed_lines.append(block_text)
                in_single_or_factory = False
                current_block = []
        else:
            fixed_lines.append(line)
    
    # 寫回文件
    with open(file_path, 'w', encoding='utf-8') as f:
        f.writelines(fixed_lines)
    
    return True

def main():
    """主函數"""
    di_dir = Path('/Users/iml1s/Documents/WearWallet/wear/src/main/java/com/cbstudio/wearwallet/di')
    
    # 直接修復 DatabaseModule
    db_module = di_dir / 'DatabaseModule.kt'
    
    # 手動重寫 DatabaseModule
    database_module_content = """package com.cbstudio.wearwallet.di

import android.content.Context
import androidx.room.Room
import com.cbstudio.wearwallet.data.db.DatabaseHealthChecker
import com.cbstudio.wearwallet.data.db.migration.DatabaseMigrationManager
import com.cbstudio.wearwallet.data.db.migration.DatabaseVersionMonitor
import com.cbstudio.wearwallet.data.db.migration.MigrationCallback
import com.cbstudio.wearwallet.shared.data.db.WalletDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    
    single { DatabaseVersionMonitor(get()) }
    
    single { DatabaseMigrationManager() }
    
    single { MigrationCallback(get()) }
    
    single { DatabaseHealthChecker(get()) }
    
    single {
        Room.databaseBuilder(
            get<Context>(),
            WalletDatabase::class.java,
            DatabaseMigrationManager.DATABASE_NAME
        )
        .addMigrations(*get<DatabaseMigrationManager>().getAllMigrations())
        .addCallback(get<MigrationCallback>())
        .fallbackToDestructiveMigration()
        .build()
    }
    
    single { get<WalletDatabase>().transactionDao() }
    
    single { get<WalletDatabase>().customTokenDao() }
    
    single { get<WalletDatabase>().notificationDao() }
    
    single { get<WalletDatabase>().contactDao() }
    
    single { get<WalletDatabase>().nftDao() }
}
"""
    
    with open(db_module, 'w') as f:
        f.write(database_module_content)
    print("✓ Fixed DatabaseModule.kt")
    
    # 修復其他有問題的模組
    problem_modules = [
        'UseCaseV2Module.kt',
        'ViewModelModule.kt',
        'RepositoryModule.kt'
    ]
    
    for module_name in problem_modules:
        module_file = di_dir / module_name
        if module_file.exists():
            final_fix_module(module_file)
            print(f"✓ Fixed {module_name}")

if __name__ == '__main__':
    main()