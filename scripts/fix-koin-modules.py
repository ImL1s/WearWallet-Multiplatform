#!/usr/bin/env python3
"""
修復轉換後的 Koin 模組
"""

import os
import re
from pathlib import Path

def fix_koin_module(file_path):
    """修復單個 Koin 模組文件"""
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original_content = content
    
    # 1. 修復 provides 函數體
    # 將 return xxx 改為只有 xxx
    content = re.sub(
        r'single\s*{\s*return\s+([^}]+)\s*}',
        r'single { \1 }',
        content
    )
    
    content = re.sub(
        r'factory\s*{\s*return\s+([^}]+)\s*}',
        r'factory { \1 }',
        content
    )
    
    # 2. 修復參數注入
    # 將 (context) 改為 get()
    content = re.sub(r'\(context\)', '(get())', content)
    content = re.sub(r'\(application\)', '(get())', content)
    
    # 3. 修復依賴注入參數
    # DatabaseVersionMonitor(context) -> DatabaseVersionMonitor(get())
    content = re.sub(
        r'(\w+)\(context\)',
        r'\1(get())',
        content
    )
    
    # 4. 修復變量引用
    # versionMonitor -> get()
    content = re.sub(
        r'single\s*{\s*(\w+)\s*}',
        lambda m: f'single {{ get<{m.group(1).replace("get", "").title()}>() }}' if 'get' not in m.group(1) else f'single {{ {m.group(1)} }}',
        content
    )
    
    # 5. 添加缺失的 androidContext
    if 'androidContext' not in content and 'Context' in content:
        content = re.sub(
            r'(import org\.koin\.dsl\.module\n)',
            r'\1import org.koin.android.ext.koin.androidContext\n',
            content
        )
    
    # 6. 修復 Room database builder
    content = re.sub(
        r'Room\.databaseBuilder\(\s*context,',
        r'Room.databaseBuilder(\n            get(),',
        content
    )
    
    # 7. 修復 @Named 參數
    content = re.sub(
        r'@Named\("([^"]+)"\)\s*(\w+):',
        r'/* Named("\1") */ \2:',
        content
    )
    
    # 8. 修復函數聲明
    # fun provideXxx(param: Type): ReturnType 改為 single<ReturnType> { }
    content = re.sub(
        r'fun provide(\w+)\(([^)]*)\):\s*(\w+)\s*{([^}]+)}',
        r'single<\3> { \4 }',
        content,
        flags=re.MULTILINE | re.DOTALL
    )
    
    # 9. 清理多餘的空行和縮進
    content = re.sub(r'\n{3,}', '\n\n', content)
    
    # 10. 修復模組名稱（確保是 val 而不是 object）
    content = re.sub(
        r'^object (\w+Module)\s*{',
        lambda m: f'val {m.group(1)[0].lower() + m.group(1)[1:]} = module {{',
        content,
        flags=re.MULTILINE
    )
    
    if content != original_content:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

def main():
    """主函數"""
    di_dir = Path('/Users/iml1s/Documents/WearWallet/wear/src/main/java/com/cbstudio/wearwallet/di')
    
    processed = 0
    fixed = 0
    
    # 處理所有 Module.kt 文件
    for module_file in di_dir.glob('*Module.kt'):
        if module_file.name == 'WearKoinModule.kt':
            continue  # 跳過我們手動創建的主模組
            
        processed += 1
        print(f"Fixing: {module_file.name}")
        if fix_koin_module(module_file):
            fixed += 1
            print(f"  ✓ Fixed")
        else:
            print(f"  - No fixes needed")
    
    print(f"\nProcessed {processed} files, fixed {fixed} files")

if __name__ == '__main__':
    main()