package com.yg.dschat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 官方「搜索结果」底部面板：一条网页一行，点击跳转浏览器。
 * 用同窗口的 Box 覆盖实现，避免 ModalBottomSheet 建新 window 导致截图/语义树分裂。
 */
@Composable
fun SearchSheet(hits: List<SearchHit>, onDismiss: () -> Unit) {
    val colors = Ds.colors
    val ctx = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { onDismiss() }
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(colors.layer2)
                .testTag("search-sheet")
                .clickable(enabled = false) {}
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)) {
                Text(
                    "搜索结果",
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.labelPrimary,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                ) {
                    Icon(Icons.Default.Close, "关闭", tint = colors.labelTertiary)
                }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(hits) { h ->
                    SourceRow(h) {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(h.url)))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SourceRow(h: SearchHit, onClick: () -> Unit) {
    val colors = Ds.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SiteIcon(h, 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                h.site.ifBlank { hostOf(h.url) },
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = colors.labelSecondary,
            )
            if (h.date.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(h.date, fontSize = 12.sp, color = colors.labelTertiary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            h.title.ifBlank { h.url },
            fontSize = 15.sp,
            lineHeight = 22.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = colors.labelPrimary,
        )
        if (h.snippet.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                h.snippet,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = colors.labelSecondary,
            )
        }
    }
    HorizontalDivider(color = colors.borderL1, modifier = Modifier.padding(horizontal = 20.dp))
}

private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)
