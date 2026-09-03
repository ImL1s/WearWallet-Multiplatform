package com.cbstudio.wearwallet.presentation.qa

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.cbstudio.wearwallet.presentation.TestTags

/**
 * Persistent label so emulator overlay screenshots cannot be treated as chain evidence.
 */
@Composable
fun WearQaFixtureBanner(modifier: Modifier = Modifier) {
    if (!WearQaHarness.isActive()) return
    Text(
        text = WearQaFixtures.BANNER_TEXT,
        color = Color(0xFFFFB74D),
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag(TestTags.QA_FIXTURE_BANNER)
    )
}
