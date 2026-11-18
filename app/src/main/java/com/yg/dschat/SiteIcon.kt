package com.yg.dschat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage

/**
 * 站点图标：有 site_icon 时加载 favicon，加载中/失败/无地址时退回首字母圆点。
 * 用 SubcomposeAsyncImage 是为了在 JVM 快照（无网络）下也能看到占位形状。
 */
@Composable
fun SiteIcon(hit: SearchHit, size: Dp = 20.dp) {
    val colors = Ds.colors
    if (hit.icon.isBlank()) {
        LetterDot(hit, size)
        return
    }
    SubcomposeAsyncImage(
        model = hit.icon,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size).clip(CircleShape),
        loading = { LetterDot(hit, size) },
        error = { LetterDot(hit, size) },
    )
}

@Composable
private fun LetterDot(hit: SearchHit, size: Dp) {
    val colors = Ds.colors
    Box(
        Modifier.size(size).clip(CircleShape).background(colors.brand.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            hit.site.trim().take(1).ifBlank { "?" },
            fontSize = (size.value * 0.5f).sp,
            color = colors.brandText,
        )
    }
}
