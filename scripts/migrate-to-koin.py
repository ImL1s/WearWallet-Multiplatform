#!/usr/bin/env python3
"""
批量將 Hilt 遷移到 Koin
"""

import os
import re
from pathlib import Path

def process_file(file_path):
    """處理單個文件"""
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original_content = content
    
    # 1. 替換 imports
    content = re.sub(r'import androidx\.hilt\.navigation\.compose\.hiltViewModel\b', 
                     'import org.koin.androidx.compose.koinViewModel', content)
    
    content = re.sub(r'import dagger\.hilt\.android\.AndroidEntryPoint\b',
                     '// import dagger.hilt.android.AndroidEntryPoint  // Removed Hilt', content)
    
    content = re.sub(r'import dagger\.hilt\.android\.lifecycle\.HiltViewModel\b',
                     '// import dagger.hilt.android.lifecycle.HiltViewModel  // Removed Hilt', content)
    
    content = re.sub(r'import javax\.inject\.Inject\b',
                     'import org.koin.core.component.KoinComponent\nimport org.koin.core.component.inject', content)
    
    # 2. 移除 @AndroidEntryPoint
    content = re.sub(r'@AndroidEntryPoint\s*\n', '// @AndroidEntryPoint  // Removed Hilt\n', content)
    
    # 3. 移除 @HiltViewModel
    content = re.sub(r'@HiltViewModel\s*\n', '// @HiltViewModel  // Removed Hilt\n', content)
    
    # 4. 替換 hiltViewModel() 為 koinViewModel()
    content = re.sub(r'\bhiltViewModel\(\)', 'koinViewModel()', content)
    
    # 5. 替換 @Inject constructor 為 Koin style
    # ViewModel 類
    content = re.sub(r'class (\w+ViewModel)[^{]*@Inject constructor\(',
                     r'class \1(', content)
    
    # UseCase 類
    content = re.sub(r'class (\w+UseCase)[^{]*@Inject constructor\(',
                     r'class \1(', content)
    
    # 6. 替換 @Inject lateinit var 為 Koin inject
    content = re.sub(r'@Inject\s+lateinit var (\w+): ([\w.<>]+)',
                     r'private val \1: \2 by inject()', content)
    
    if content != original_content:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

def main():
    """主函數"""
    wear_dir = Path('/Users/iml1s/Documents/WearWallet/wear/src/main/java')
    
    processed = 0
    modified = 0
    
    # 遍歷所有 Kotlin 文件
    for kt_file in wear_dir.rglob('*.kt'):
        processed += 1
        if process_file(kt_file):
            modified += 1
            print(f"Modified: {kt_file.relative_to(wear_dir)}")
    
    print(f"\nProcessed {processed} files, modified {modified} files")

if __name__ == '__main__':
    main()