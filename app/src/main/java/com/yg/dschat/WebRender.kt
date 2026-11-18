package com.yg.dschat

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/**
 * 用本地 assets 里的 KaTeX / mermaid 渲染公式和图表。
 * WebView 高度由 JS 回传，避免内容被裁切。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebRender(
    kind: String,
    payload: String,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    var heightDp by remember(payload, dark) { mutableStateOf(if (kind == "math") 56.dp else 220.dp) }

    key(payload, dark) {
        AndroidView(
            modifier = modifier.fillMaxWidth().height(heightDp),
            factory = { c ->
                WebView(c).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // WebView 只负责渲染本地模板，不需要访问文件系统
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.allowFileAccessFromFileURLs = false
                    settings.allowUniversalAccessFromFileURLs = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onHeight(px: Int) {
                                val density = c.resources.displayMetrics.density
                                val dp = (px / density).coerceAtLeast(24f)
                                post { heightDp = dp.dp }
                            }
                        },
                        "NativeBridge",
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            view?.evaluateJavascript(
                                "render(${JSONObject.quote(kind)},${JSONObject.quote(payload)},$dark)",
                                null,
                            )
                        }
                    }
                    loadUrl("file:///android_asset/render.html")
                }
            },
            onRelease = { wv ->
                // 不释放会一直持有 native 层资源，公式多的长对话能把内存吃光
                runCatching {
                    wv.loadUrl("about:blank")
                    wv.removeJavascriptInterface("NativeBridge")
                    wv.stopLoading()
                    wv.destroy()
                }
            },
        )
    }
}
