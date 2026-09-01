#!/usr/bin/env python3
"""
將所有 Hilt Module 轉換為 Koin Module
"""

import os
import re
from pathlib import Path

def convert_hilt_module_to_koin(file_path):
    """將單個 Hilt Module 文件轉換為 Koin"""
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original_content = content
    
    # 1. 移除 Hilt imports
    content = re.sub(r'import dagger\.Module\s*\n', '', content)
    content = re.sub(r'import dagger\.Provides\s*\n', '', content)
    content = re.sub(r'import dagger\.Binds\s*\n', '', content)
    content = re.sub(r'import dagger\.hilt\.InstallIn\s*\n', '', content)
    content = re.sub(r'import dagger\.hilt\.components\.\w+\s*\n', '', content)
    content = re.sub(r'import dagger\.hilt\.android\.qualifiers\.\w+\s*\n', '', content)
    content = re.sub(r'import javax\.inject\.\w+\s*\n', '', content)
    
    # 2. 添加 Koin imports
    if 'import org.koin.dsl.module' not in content:
        # 在 package 聲明後添加
        content = re.sub(
            r'(package [\w.]+\n\n)',
            r'\1import org.koin.dsl.module\nimport org.koin.core.module.Module\n',
            content
        )
    
    # 3. 移除 @Module 和 @InstallIn 註解
    content = re.sub(r'@Module\s*\n', '', content)
    content = re.sub(r'@InstallIn\([^)]*\)\s*\n', '', content)
    
    # 4. 轉換 object Module 為 val module
    # @Module object XxxModule -> val xxxModule = module
    content = re.sub(
        r'object (\w+Module)\s*{',
        lambda m: f'val {m.group(1)[0].lower() + m.group(1)[1:]} = module {{',
        content
    )
    
    # 5. 轉換 abstract class Module
    content = re.sub(
        r'abstract class (\w+Module)\s*{',
        lambda m: f'val {m.group(1)[0].lower() + m.group(1)[1:]} = module {{',
        content
    )
    
    # 6. 轉換 @Provides 函數
    # @Provides @Singleton fun provideXxx() -> single { Xxx() }
    content = re.sub(
        r'@Provides\s*\n\s*@Singleton\s*\n\s*fun provide(\w+)\([^)]*\):[^{]*{([^}]+)}',
        r'single { \2 }',
        content,
        flags=re.MULTILINE | re.DOTALL
    )
    
    # @Provides fun provideXxx() -> factory { Xxx() }
    content = re.sub(
        r'@Provides\s*\n\s*fun provide(\w+)\([^)]*\):[^{]*{([^}]+)}',
        r'factory { \2 }',
        content,
        flags=re.MULTILINE | re.DOTALL
    )
    
    # 7. 轉換 @Binds
    # @Binds abstract fun bindXxx(impl: XxxImpl): Xxx
    content = re.sub(
        r'@Binds\s*\n\s*@Singleton\s*\n\s*abstract fun bind\w+\([\w:]+\s+(\w+)\):\s*(\w+)',
        r'single<\2> { \1() }',
        content
    )
    
    # 8. 清理多餘的空行
    content = re.sub(r'\n{3,}', '\n\n', content)
    
    if content != original_content:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

def main():
    """主函數"""
    di_dir = Path('/Users/iml1s/Documents/WearWallet/wear/src/main/java/com/cbstudio/wearwallet/di')
    
    processed = 0
    modified = 0
    
    # 獲取所有 Module 文件
    module_files = [
        'DatabaseModule.kt',
        'NetworkModule.kt', 
        'RepositoryModule.kt',
        'ViewModelModule.kt',
        'UseCaseV2Module.kt',
        'FirebaseModule.kt',
        'SecurityModule.kt',
        'BiometricModule.kt',
        'VoiceModule.kt',
        'AIModule.kt',
        'WearFiModule.kt',
        'CrossChainModule.kt',
        'SubscriptionModule.kt',
        'DeFiModule.kt',
        'TokenModule.kt',
        'ENSModule.kt',
        'DebitCardModule.kt',
        'ApiKeyModule.kt',
        'PriceServiceModule.kt',
        'BillingModule.kt',
        'ComplicationModule.kt',
        'RemoteConfigModule.kt',
        'TransactionModule.kt',
        'TransactionDataSourceModule.kt',
        'DatasourceModule.kt',
        'UtilsModule.kt',
        'KmpAdapterModule.kt',
        'KmpServiceModule.kt',
        'KmpMigrationModule.kt',
        'DirectKmpModule.kt',
        'BasicWalletRepositoryModule.kt',
        'WalletRepositoryMigrationModule.kt'
    ]
    
    for module_file in module_files:
        file_path = di_dir / module_file
        if file_path.exists():
            processed += 1
            print(f"Processing: {module_file}")
            if convert_hilt_module_to_koin(file_path):
                modified += 1
                print(f"  ✓ Converted to Koin")
            else:
                print(f"  - No changes needed")
        else:
            print(f"  ✗ File not found: {module_file}")
    
    print(f"\nProcessed {processed} files, modified {modified} files")

if __name__ == '__main__':
    main()