/**
 * Wear OS 手勢檢測器 - AI 語音助手快速啟動
 * 
 * 支援手勢：
 * - Digital Crown 快速旋轉 (2秒內旋轉超過閾值)
 * - 雙指捏合手勢 (pinch gesture)
 * - 長按 + Crown 旋轉組合手勢
 * 
 * 更新 (2025-07-28): 針對 Wear OS 優化的手勢識別
 * - 避免誤觸發，增加確認機制
 * - 震動反饋提升用戶體驗
 * - 與系統級 Gemini Live 服務整合
 */

package com.cbstudio.wearwallet.ai.gesture

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import kotlin.math.abs

/**
 * 手勢類型
 */
enum class AIGestureType {
    CROWN_ROTATION,    // Digital Crown 快速旋轉
    PINCH_GESTURE,     // 雙指捏合
    LONG_PRESS_CROWN,  // 長按 + Crown 旋轉
    TRIPLE_TAP         // 三連擊 (備用手勢)
}

/**
 * 手勢檢測結果
 */
data class GestureDetectionResult(
    val gestureType: AIGestureType,
    val confidence: Float, // 0.0 - 1.0
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 系統級手勢檢測服務
 */
class WearOSGestureDetectionService : KoinComponent {
    
    // Crown 旋轉檢測參數
    private var crownRotationAccumulator = 0f
    private var lastCrownEvent = 0L
    private val crownRotationThreshold = 5f // 累積旋轉閾值
    private val crownTimeWindow = 2000L // 2秒時間窗口
    
    // 捏合手勢檢測參數
    private var initialScale = 1f
    private var isTrackingPinch = false
    private val pinchThreshold = 0.3f // 縮放閾值
    
    // 長按檢測參數
    private var longPressStartTime = 0L
    private var isLongPressing = false
    private val longPressThreshold = 800L // 800ms 長按閾值
    
    /**
     * 檢測 Digital Crown 旋轉手勢
     */
    fun detectCrownRotation(scrollDelta: Float): GestureDetectionResult? {
        val currentTime = System.currentTimeMillis()
        
        // 重置累積器如果超過時間窗口
        if (currentTime - lastCrownEvent > crownTimeWindow) {
            crownRotationAccumulator = 0f
        }
        
        // 累積旋轉量
        crownRotationAccumulator += abs(scrollDelta)
        lastCrownEvent = currentTime
        
        // 檢查是否達到閾值
        if (crownRotationAccumulator >= crownRotationThreshold) {
            crownRotationAccumulator = 0f // 重置
            
            // 計算信心度 (基於旋轉速度)
            val confidence = (abs(scrollDelta) / 2f).coerceIn(0.7f, 1.0f)
            
            return GestureDetectionResult(
                gestureType = AIGestureType.CROWN_ROTATION,
                confidence = confidence
            )
        }
        
        return null
    }
    
    /**
     * 檢測捏合手勢
     */
    fun detectPinchGesture(scaleFactor: Float): GestureDetectionResult? {
        if (!isTrackingPinch) {
            isTrackingPinch = true
            initialScale = scaleFactor
            return null
        }
        
        val scaleChange = abs(scaleFactor - initialScale)
        
        if (scaleChange >= pinchThreshold) {
            isTrackingPinch = false
            
            // 計算信心度 (基於縮放幅度)
            val confidence = (scaleChange / 0.6f).coerceIn(0.6f, 1.0f)
            
            return GestureDetectionResult(
                gestureType = AIGestureType.PINCH_GESTURE,
                confidence = confidence
            )
        }
        
        return null
    }
    
    /**
     * 檢測長按 + Crown 組合手勢
     */
    fun detectLongPressCrown(isPressed: Boolean, scrollDelta: Float = 0f): GestureDetectionResult? {
        val currentTime = System.currentTimeMillis()
        
        if (isPressed && !isLongPressing) {
            longPressStartTime = currentTime
            isLongPressing = true
            return null
        }
        
        if (!isPressed) {
            isLongPressing = false
            longPressStartTime = 0L
            return null
        }
        
        // 檢查是否達到長按時間 + 有 Crown 旋轉
        val pressDuration = currentTime - longPressStartTime
        if (isLongPressing && pressDuration >= longPressThreshold && abs(scrollDelta) > 0.5f) {
            isLongPressing = false
            
            val confidence = 0.9f // 組合手勢具有高信心度
            
            return GestureDetectionResult(
                gestureType = AIGestureType.LONG_PRESS_CROWN,
                confidence = confidence
            )
        }
        
        return null
    }
    
    /**
     * Release builds do not ship Gemini Live. Fail-closed: no service start,
     * no in-app "assistant" navigation that would look like success.
     */
    fun triggerAIAssistant(@Suppress("UNUSED_PARAMETER") gestureType: AIGestureType) {
        return
    }
}

/**
 * Compose 手勢檢測器組件
 */
@Composable
fun AIGestureDetector(
    modifier: Modifier = Modifier,
    onGestureDetected: (WearOSGestureDetectionService) -> Unit = {},
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    // 注入手勢檢測服務
    val gestureService = remember { WearOSGestureDetectionService() }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            // Digital Crown 旋轉檢測 - 這個不會干擾滑動
            .onRotaryScrollEvent { event ->
                val result = gestureService.detectCrownRotation(event.verticalScrollPixels)
                result?.let {
                    if (it.confidence >= 0.7f) {
                        coroutineScope.launch {
                            gestureService.triggerAIAssistant(it.gestureType)
                        }
                    }
                }
                false // 不消費事件，讓其他組件也能處理
            }
            // 僅檢測捏合手勢，不檢測其他觸摸事件以避免干擾滑動
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    // 只在明顯捏合時觸發
                    if (kotlin.math.abs(zoom - 1.0f) > 0.2f) {
                        val pinchResult = gestureService.detectPinchGesture(zoom)
                        pinchResult?.let {
                            if (it.confidence >= 0.8f) {
                                coroutineScope.launch {
                                    gestureService.triggerAIAssistant(it.gestureType)
                                }
                            }
                        }
                    }
                }
            }
    ) {
        content()
    }
    
    // 傳遞手勢服務給父組件
    LaunchedEffect(gestureService) {
        onGestureDetected(gestureService)
    }
}

/**
 * AI 手勢檢測 ViewModel
 */
class AIGestureViewModel(
    private val gestureService: WearOSGestureDetectionService
) : ViewModel() {
    
    private val _gestureDetected = mutableStateOf<GestureDetectionResult?>(null)
    val gestureDetected: State<GestureDetectionResult?> = _gestureDetected
    
    /**
     * 手動觸發手勢檢測 (用於測試)
     */
    fun triggerTestGesture(gestureType: AIGestureType) {
        gestureService.triggerAIAssistant(gestureType)
        _gestureDetected.value = GestureDetectionResult(
            gestureType = gestureType,
            confidence = 1.0f
        )
    }
    
    /**
     * 清除手勢檢測結果
     */
    fun clearGestureResult() {
        _gestureDetected.value = null
    }
}
