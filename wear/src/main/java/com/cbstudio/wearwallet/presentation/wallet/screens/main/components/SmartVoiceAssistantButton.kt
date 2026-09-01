package com.cbstudio.wearwallet.presentation.wallet.screens.main.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.compose.material3.MaterialTheme
import com.cbstudio.wearwallet.R
import kotlinx.coroutines.delay

@Composable
fun SmartVoiceAssistantButton(
    onClick: () -> Unit,
    scrollState: ScalingLazyListState,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(true) }
    var lastScrollPosition by remember { mutableStateOf(0) }
    
    // 監聽滾動狀態
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            val currentPosition = scrollState.centerItemIndex
            // 向下滾動時隱藏
            if (currentPosition > lastScrollPosition) {
                isVisible = false
            }
            lastScrollPosition = currentPosition
        } else {
            // 停止滾動後延遲顯示
            delay(1000)
            isVisible = true
        }
    }
    
    // 進入/退出動畫
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(300)
        ) + fadeOut(),
        modifier = modifier
    ) {
        // 懸浮式設計，最小化視覺干擾
        Surface(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = stringResource(R.string.ai_voice_assistant),
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
            }
        }
    }
}
