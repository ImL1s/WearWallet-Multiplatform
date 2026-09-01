#!/usr/bin/env python3
"""
修復 KmpAdapterModule 中的語法錯誤
"""

import re

file_path = '/Users/iml1s/Documents/WearWallet/wear/src/main/java/com/cbstudio/wearwallet/di/KmpAdapterModule.kt'

with open(file_path, 'r') as f:
    content = f.read()

# 修復錯誤的 inject() 語法
# val repository: Type by inject() 是錯誤的，應該直接 get()
content = re.sub(
    r'val (\w+): (\w+) by inject\(\)\s*\n\s*return \1',
    r'get<\2>()',
    content
)

# 移除多餘的 return
content = re.sub(
    r'return ([\w<>()]+)',
    r'\1',
    content
)

# 修復 single { } 塊
content = re.sub(
    r'single\s*{\s*\n\s*//([^\n]*)\n\s*get<(\w+)>\(\)\s*\n\s*}',
    r'single { //\1\n        get<\2>()\n    }',
    content
)

# 寫回文件
with open(file_path, 'w') as f:
    f.write(content)

print("✓ Fixed KmpAdapterModule.kt")