#!/usr/bin/env python3
"""
修復剩餘的 @Inject 註解
"""

import re
from pathlib import Path

def fix_inject_annotations(file_path):
    """修復文件中的 @Inject 註解"""
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    
    # 移除 @Inject import
    content = re.sub(r'import javax\.inject\.Inject\s*\n', '', content)
    
    # 將 class Xxx @Inject constructor 改為 class Xxx
    content = re.sub(r'class (\w+)\s+@Inject\s+constructor', r'class \1', content)
    
    # 移除其他 @Inject
    content = re.sub(r'@Inject\s+', '', content)
    
    # 添加 Koin import 如果需要
    if 'by inject()' in content and 'import org.koin' not in content:
        content = re.sub(
            r'(package [\w.]+\n)',
            r'\1\nimport org.koin.core.component.KoinComponent\nimport org.koin.core.component.inject',
            content
        )
    
    if content != original:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False

# 修復具體文件
files_to_fix = [
    '/Users/iml1s/Documents/WearWallet/wear/src/main/java/com/cbstudio/wearwallet/presentation/wearfi/WearFiHealthMiningManager.kt',
    '/Users/iml1s/Documents/WearWallet/wear/src/main/java/com/cbstudio/wearwallet/security/BehavioralBiometricEngine.kt',
    '/Users/iml1s/Documents/WearWallet/wear/src/main/java/com/cbstudio/wearwallet/services/FirebaseService.kt',
    '/Users/iml1s/Documents/WearWallet/wear/src/main/java/com/cbstudio/wearwallet/utils/ImageCache.kt',
    '/Users/iml1s/Documents/WearWallet/wear/src/main/java/com/cbstudio/wearwallet/wearfi/WearFiService.kt'
]

for file_path in files_to_fix:
    if Path(file_path).exists():
        if fix_inject_annotations(file_path):
            print(f"✓ Fixed {Path(file_path).name}")
        else:
            print(f"- No changes needed for {Path(file_path).name}")
    else:
        print(f"✗ File not found: {Path(file_path).name}")