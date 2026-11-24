package com.appscheduler.lsposed.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 内置背景的浅色 / 深色渐变；顺序固定，供主题页与正式界面共用 */
private data class BackgroundColors(
    val light: List<Int>,
    val dark: List<Int>
)

private val backgroundPresets: Map<BackgroundStyle, BackgroundColors> = mapOf(
    BackgroundStyle.MIST to BackgroundColors(
        light = listOf(0xFFE7F3FA.toInt(), 0xFFFBFDFF.toInt(), 0xFFDCEAF2.toInt()),
        dark = listOf(0xFF1B2A31.toInt(), 0xFF101B21.toInt(), 0xFF24353E.toInt())
    ),
    BackgroundStyle.AURORA to BackgroundColors(
        light = listOf(0xFFDDF4EE.toInt(), 0xFFE8EDFF.toInt(), 0xFFF4E8FF.toInt()),
        dark = listOf(0xFF0C2A2A.toInt(), 0xFF171C3A.toInt(), 0xFF2A1633.toInt())
    ),
    BackgroundStyle.DUSK to BackgroundColors(
        light = listOf(0xFFFFE8D6.toInt(), 0xFFF5DAE8.toInt(), 0xFFDDE4FF.toInt()),
        dark = listOf(0xFF2D1B19.toInt(), 0xFF23162E.toInt(), 0xFF191B35.toInt())
    ),
    BackgroundStyle.FOREST to BackgroundColors(
        light = listOf(0xFFE6F0E2.toInt(), 0xFFF4F8EA.toInt(), 0xFFDCE9DA.toInt()),
        dark = listOf(0xFF15261A.toInt(), 0xFF1D2D1F.toInt(), 0xFF101C15.toInt())
    ),
    BackgroundStyle.INK to BackgroundColors(
        light = listOf(0xFFE8EDF1.toInt(), 0xFFF8FAFC.toInt(), 0xFFDDE3E9.toInt()),
        dark = listOf(0xFF171B1F.toInt(), 0xFF0C0F12.toInt(), 0xFF242B31.toInt())
    )
)

fun backgroundColors(style: BackgroundStyle, darkTheme: Boolean): List<Int> {
    val preset = backgroundPresets[style] ?: return emptyList()
    return if (darkTheme) preset.dark else preset.light
}

fun backgroundBrush(style: BackgroundStyle, darkTheme: Boolean): Brush? {
    val colors = backgroundColors(style, darkTheme)
    if (colors.isEmpty()) return null
    return Brush.verticalGradient(colors.map { Color(it) })
}

/**
 * 全屏主题背景：先铺主题底色，再叠加内置渐变或自选图片，最后覆盖一层
 * 可调的内容遮罩，保证标题与正文在复杂图片上仍可辨认。
 */
@Composable
fun AppThemeBackground(
    settings: ThemeSettings,
    darkTheme: Boolean = settings.darkTheme(isSystemInDarkTheme()),
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, settings.backgroundImageUri) {
        value = if (
            settings.backgroundStyle == BackgroundStyle.IMAGE &&
            !settings.backgroundImageUri.isNullOrBlank()
        ) {
            withContext(Dispatchers.IO) {
                decodeBackground(context, settings.backgroundImageUri!!)
            }
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (settings.backgroundStyle) {
            BackgroundStyle.NONE -> Unit

            BackgroundStyle.IMAGE -> {
                val image = bitmap
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(settings.backgroundAlpha),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            else -> {
                val brush = backgroundBrush(settings.backgroundStyle, darkTheme)
                if (brush != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(settings.backgroundAlpha)
                            .background(brush)
                    )
                }
            }
        }

        if (settings.backgroundStyle != BackgroundStyle.NONE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.background.copy(
                            alpha = settings.backgroundScrim.coerceIn(0f, 0.8f)
                        )
                    )
            )
        }

        content()
    }
}

private fun decodeBackground(context: Context, uri: String): ImageBitmap? {
    return try {
        val parsed = Uri.parse(uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, parsed)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > 1600) {
                    val scale = 1600f / longest
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1)
                    )
                }
            }.asImageBitmap()
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
            }
        }
    } catch (t: Throwable) {
        null
    }
}
