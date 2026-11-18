package com.yg.dschat

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import android.app.Activity

/**
 * 取自 chat-demo 的 theme.css / 各组件 module.css：
 * --dsw-alias-* 与 --dsw-specific-* 的浅色、深色值原样搬过来。
 */
class DsColors(val dark: Boolean) {

    // 底层
    val bg = if (dark) Color(0xFF0F0F0F) else Color(0xFFFFFFFF)
    val layer2 = if (dark) Color(0xFF232325) else Color(0xFFFFFFFF)
    val layer3 = if (dark) Color(0xFF2A2A2C) else Color(0xFFFFFFFF)
    val sidebar = if (dark) Color(0xFF141416) else Color(0xFFF9FAFB)
    val bubble = if (dark) Color(0xFF2A2A2C) else Color(0xFFEDF3FE)
    val input = if (dark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val codeBlock = if (dark) Color(0xFF1B1B1C) else Color(0xFFF9FAFB)
    val inlineCode = if (dark) Color(0xFF2C2C2E) else Color(0xFFEBEEF2)
    val citation = if (dark) Color(0xFF353638) else Color(0xFFEBEEF2)

    // 品牌（取自官方 APK 的 color/primary：浅色 #426EFE，夜间 #507BF2）
    val brand = if (dark) Color(0xFF507BF2) else Color(0xFF426EFE)
    val brandText = if (dark) Color(0xFF679EFE) else Color(0xFF426EFE)

    // 文字
    val labelPrimary = if (dark) Color(0xFFF9FAFB) else Color(0xFF0F1115)
    val labelSecondary = if (dark) Color(0xFFCFD3D6) else Color(0xFF61666B)
    val labelTertiary = if (dark) Color(0xFFADB2B8) else Color(0xFF81858C)
    val labelCaption = if (dark) Color(0xFF81858C) else Color(0xFFADB2B8)
    val onBrand = Color(0xFFFFFFFF)

    // 线
    val borderL1 = if (dark) Color(0x0FFFFFFF) else Color(0x0A000000)
    val borderL2 = if (dark) Color(0x1FFFFFFF) else Color(0x1A000000)
    val borderL3 = if (dark) Color(0x28FFFFFF) else Color(0x1F000000)

    // 交互
    val hover = if (dark) Color(0x14FFFFFF) else Color(0x0F263148)
    val active = if (dark) Color(0x24FFFFFF) else Color(0x1A263148)
    val navActive = if (dark) Color(0xFF1E2A4A) else Color(0xFFE4EDFD)
    val navActiveText = if (dark) Color(0xFF679EFE) else Color(0xFF426EFE)
    val ghostActiveFill = if (dark) Color(0xFF1B1B1C) else Color(0xFFEDF3FE)
    val ghostActiveBorder = if (dark) Color(0xFF4868B2) else Color(0xFFB7C8FE)
    val chipActiveBg = if (dark) Color(0xFF233050) else Color(0xFFEDF3FE)
    val chipActiveBorder = if (dark) Color(0xFF3B5AA8) else Color(0xFFB7C8FE)

    val error = if (dark) Color(0xFFF25A5A) else Color(0xFFEC1313)
    val codeKeyword = if (dark) Color(0xFFC792EA) else Color(0xFF8B3FA8)
    val codeString = if (dark) Color(0xFF9AD48A) else Color(0xFF0E7C4A)
    val codeNumber = if (dark) Color(0xFFF0A868) else Color(0xFFB5522B)
    val codeComment = if (dark) Color(0xFF7C838F) else Color(0xFF8A909B)
    val skeleton = if (dark) Color(0x14FFFFFF) else Color(0x0A000000)
}

val LocalDsColors = staticCompositionLocalOf { DsColors(false) }

object Ds {
    val colors: DsColors
        @Composable @ReadOnlyComposable get() = LocalDsColors.current

    // 尺寸（css px ≈ dp）
    val messageMaxWidth = 780.dp
    val sidebarWidth = 300.dp
    val headerHeight = 60.dp
    val radiusBubble = 22.dp
    val radiusInput = 28.dp
    val radiusItem = 16.dp
    val radiusCard = 12.dp

    // 字号
    val fsMd = 16.sp
    val lhMd = 28.sp      // markdown-base 16/28
    val fsSmall = 14.sp
    val lhSmall = 24.sp
    val fsXs = 13.sp
    val lhXs = 20.sp
    val fsCaption = 11.sp
    val lhCaption = 16.sp
}

@Composable
fun DsTheme(
    themeMode: String = "system",
    fontScale: Float = 1f,
    followSystemFont: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    // staticCompositionLocalOf 用 === 判断，每次重组新建实例会让整棵树失效
    val colors = remember(dark) { DsColors(dark) }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = colors.bg.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colors.bg.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.brand,
            onPrimary = Color.White,
            background = colors.bg,
            onBackground = colors.labelPrimary,
            surface = colors.bg,
            onSurface = colors.labelPrimary,
            surfaceVariant = colors.layer3,
            onSurfaceVariant = colors.labelSecondary,
            error = colors.error,
            outline = colors.borderL2,
        )
    } else {
        lightColorScheme(
            primary = colors.brand,
            onPrimary = Color.White,
            background = colors.bg,
            onBackground = colors.labelPrimary,
            surface = colors.bg,
            onSurface = colors.labelPrimary,
            surfaceVariant = colors.layer3,
            onSurfaceVariant = colors.labelSecondary,
            error = colors.error,
            outline = colors.borderL2,
        )
    }
    val baseDensity = LocalDensity.current
    val effectiveFontScale = baseDensity.fontScale * if (followSystemFont) 1f else fontScale
    CompositionLocalProvider(
        LocalDsColors provides colors,
        LocalDensity provides Density(baseDensity.density, effectiveFontScale),
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
