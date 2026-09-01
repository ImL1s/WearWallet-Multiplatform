#!/usr/bin/env python3
"""
修復 Koin DI 類型推斷問題的腳本
將所有 'by inject()' 替換為 'by inject<Type>()'
"""

import os
import re
import sys

def fix_inject_type_inference(file_path):
    """修復單個文件中的 Koin 類型推斷問題"""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # 原始內容
        original_content = content
        
        # 正則表達式匹配模式：private val name: Type by inject()
        pattern = r'(\s*private val \w+):\s*([A-Za-z0-9_<>]+)\s+by inject\(\)'
        
        # 替換為：private val name: Type by inject<Type>()
        def replacement(match):
            declaration = match.group(1)
            type_name = match.group(2)
            return f"{declaration}: {type_name} by inject<{type_name}>()"
        
        # 執行替換
        content = re.sub(pattern, replacement, content)
        
        # 如果內容有變化，寫回文件
        if content != original_content:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f"✅ 已修復: {file_path}")
            return True
        else:
            print(f"⏭️  無需修復: {file_path}")
            return False
            
    except Exception as e:
        print(f"❌ 錯誤處理 {file_path}: {e}")
        return False

def find_kotlin_files_with_inject(directory):
    """查找包含 'by inject()' 的 Kotlin 文件"""
    files_to_fix = []
    
    for root, dirs, files in os.walk(directory):
        # 跳過某些目錄
        dirs[:] = [d for d in dirs if d not in ['.git', 'build', 'target']]
        
        for file in files:
            if file.endswith('.kt'):
                file_path = os.path.join(root, file)
                try:
                    with open(file_path, 'r', encoding='utf-8') as f:
                        content = f.read()
                        if 'by inject()' in content:
                            files_to_fix.append(file_path)
                except Exception:
                    continue
    
    return files_to_fix

def main():
    """主函數"""
    # 獲取 wear 模組目錄
    wear_dir = '/Users/iml1s/Documents/WearWallet/wear/src/main/java'
    
    if not os.path.exists(wear_dir):
        print(f"❌ 目錄不存在: {wear_dir}")
        sys.exit(1)
    
    print("🔍 搜索需要修復的文件...")
    files_to_fix = find_kotlin_files_with_inject(wear_dir)
    
    if not files_to_fix:
        print("✅ 沒有找到需要修復的文件")
        return
    
    print(f"📝 找到 {len(files_to_fix)} 個需要修復的文件")
    
    fixed_count = 0
    for file_path in files_to_fix:
        if fix_inject_type_inference(file_path):
            fixed_count += 1
    
    print(f"\n🎉 修復完成! 共修復了 {fixed_count} 個文件")

if __name__ == "__main__":
    main()