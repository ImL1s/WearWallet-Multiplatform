package com.cbstudio.wearwallet.presentation.components.ens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay

/**
 * WearOS 優化的 ENS 輸入組件
 * 支援 ENS 名稱和地址輸入，提供即時驗證
 */
@Composable
fun ENSInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "輸入 ENS 或地址",
    onDone: (() -> Unit)? = null,
    onVoiceInput: (() -> Unit)? = null,
    isValidating: Boolean = false,
    validationResult: ValidationResult? = null
) {
    val focusRequester = remember { FocusRequester() }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 輸入區域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 文字輸入
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { newValue ->
                        // 過濾非法字符
                        val filtered = newValue.filter { 
                            it.isLetterOrDigit() || it == '.' || it == '-'
                        }
                        onValueChange(filtered)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = MaterialTheme.colors.onBackground,
                        textAlign = TextAlign.Center
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onDone?.invoke() }
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colors.primary)
                )
                
                // 佔位符
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // 語音輸入按鈕
            if (onVoiceInput != null) {
                Spacer(modifier = Modifier.width(8.dp))
                CompactButton(
                    onClick = onVoiceInput,
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.2f)
                    )
                ) {
                    Text("🎤", fontSize = 16.sp)
                }
            }
        }
        
        // 驗證狀態
        Spacer(modifier = Modifier.height(8.dp))
        ValidationStatusDisplay(
            isValidating = isValidating,
            validationResult = validationResult
        )
        
        // ENS 建議（未來功能）
        if (value.isNotEmpty() && value.length > 2 && !value.contains(".")) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "💡 嘗試 $value.eth",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier.clickable {
                    onValueChange("$value.eth")
                }
            )
        }
    }
}

/**
 * 驗證狀態顯示組件
 */
@Composable
private fun ValidationStatusDisplay(
    isValidating: Boolean,
    validationResult: ValidationResult?
) {
    when {
        isValidating -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "驗證中...",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }
        }
        validationResult != null -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val (icon, text, color) = when (validationResult) {
                    is ValidationResult.ValidENS -> Triple(
                        "✓", 
                        "有效的 ENS: ${formatAddress(validationResult.address)}", 
                        Color(0xFF4CAF50)
                    )
                    is ValidationResult.ValidAddress -> Triple(
                        "✓",
                        if (validationResult.ensName != null) {
                            "地址: ${validationResult.ensName}"
                        } else {
                            "有效的地址"
                        },
                        Color(0xFF4CAF50)
                    )
                    is ValidationResult.Invalid -> Triple(
                        "✗", 
                        validationResult.message, 
                        MaterialTheme.colors.error
                    )
                }
                
                Text(
                    text = icon,
                    fontSize = 12.sp,
                    color = color
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.caption2,
                    color = color
                )
            }
        }
    }
}

/**
 * 驗證結果
 */
sealed class ValidationResult {
    data class ValidENS(val ensName: String, val address: String) : ValidationResult()
    data class ValidAddress(val address: String, val ensName: String? = null) : ValidationResult()
    data class Invalid(val message: String) : ValidationResult()
}

/**
 * 格式化地址
 */
private fun formatAddress(address: String): String {
    return if (address.length > 10) {
        "${address.substring(0, 6)}...${address.substring(address.length - 4)}"
    } else {
        address
    }
}
