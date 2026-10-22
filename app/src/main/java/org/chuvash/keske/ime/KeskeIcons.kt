package org.chuvash.keske.ime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * МОДУЛЬ: KeskeIcons.kt
 * Единый монохромный векторный набор иконок для всех функциональных клавиш и верхней панели:
 * Shift, CapsLock, Backspace, Enter, Globe, Swap (перестановка клавиш), Clipboard, Emoji, Settings,
 * HideKeyboard (⌄), ImeSwitcher (⌨), HexGrid (⬡).
 */
enum class KeskeIconType {
    SHIFT,
    CAPS_LOCK,
    BACKSPACE,
    ENTER,
    GLOBE,
    SWAP,
    CLIPBOARD,
    EMOJI,
    SETTINGS,
    HIDE_KEYBOARD,
    IME_SWITCHER,
    HEX_GRID
}

@Composable
fun KeskeVectorIcon(
    type: KeskeIconType,
    tint: Color,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(
            width = (w * 0.105f).coerceAtLeast(2.2f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        when (type) {
            KeskeIconType.SHIFT, KeskeIconType.CAPS_LOCK -> {
                val p = Path().apply {
                    moveTo(w * 0.50f, h * 0.14f)
                    lineTo(w * 0.86f, h * 0.50f)
                    lineTo(w * 0.65f, h * 0.50f)
                    lineTo(w * 0.65f, h * 0.76f)
                    lineTo(w * 0.35f, h * 0.76f)
                    lineTo(w * 0.35f, h * 0.50f)
                    lineTo(w * 0.14f, h * 0.50f)
                    close()
                }
                drawPath(p, color = tint, style = stroke)
                if (type == KeskeIconType.CAPS_LOCK) {
                    drawLine(
                        color = tint,
                        start = Offset(w * 0.32f, h * 0.88f),
                        end = Offset(w * 0.68f, h * 0.88f),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round
                    )
                }
            }

            KeskeIconType.BACKSPACE -> {
                val p = Path().apply {
                    moveTo(w * 0.34f, h * 0.22f)
                    lineTo(w * 0.88f, h * 0.22f)
                    lineTo(w * 0.88f, h * 0.78f)
                    lineTo(w * 0.34f, h * 0.78f)
                    lineTo(w * 0.12f, h * 0.50f)
                    close()
                }
                drawPath(p, color = tint, style = stroke)
                drawLine(tint, Offset(w * 0.46f, h * 0.38f), Offset(w * 0.68f, h * 0.62f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.68f, h * 0.38f), Offset(w * 0.46f, h * 0.62f), stroke.width, StrokeCap.Round)
            }

            KeskeIconType.ENTER -> {
                val p = Path().apply {
                    moveTo(w * 0.78f, h * 0.22f)
                    lineTo(w * 0.78f, h * 0.58f)
                    lineTo(w * 0.22f, h * 0.58f)
                }
                drawPath(p, color = tint, style = stroke)
                val arrow = Path().apply {
                    moveTo(w * 0.38f, h * 0.40f)
                    lineTo(w * 0.20f, h * 0.58f)
                    lineTo(w * 0.38f, h * 0.76f)
                }
                drawPath(arrow, color = tint, style = stroke)
            }

            KeskeIconType.GLOBE -> {
                val r = w * 0.38f
                val c = Offset(w * 0.5f, h * 0.5f)
                drawCircle(color = tint, radius = r, center = c, style = stroke)
                drawLine(tint, Offset(c.x - r, c.y), Offset(c.x + r, c.y), stroke.width)
                drawLine(tint, Offset(c.x, c.y - r), Offset(c.x, c.y + r), stroke.width)
                drawOval(
                    color = tint,
                    topLeft = Offset(c.x - r * 0.52f, c.y - r),
                    size = Size(r * 1.04f, r * 2f),
                    style = stroke
                )
            }

            KeskeIconType.SWAP -> {
                drawLine(tint, Offset(w * 0.18f, h * 0.34f), Offset(w * 0.82f, h * 0.34f), stroke.width, StrokeCap.Round)
                val topArrow = Path().apply {
                    moveTo(w * 0.64f, h * 0.18f)
                    lineTo(w * 0.82f, h * 0.34f)
                    lineTo(w * 0.64f, h * 0.50f)
                }
                drawPath(topArrow, color = tint, style = stroke)

                drawLine(tint, Offset(w * 0.82f, h * 0.66f), Offset(w * 0.18f, h * 0.66f), stroke.width, StrokeCap.Round)
                val botArrow = Path().apply {
                    moveTo(w * 0.36f, h * 0.50f)
                    lineTo(w * 0.18f, h * 0.66f)
                    lineTo(w * 0.36f, h * 0.82f)
                }
                drawPath(botArrow, color = tint, style = stroke)
            }

            KeskeIconType.CLIPBOARD -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.22f, h * 0.22f),
                    size = Size(w * 0.56f, h * 0.64f),
                    cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                    style = stroke
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.36f, h * 0.12f),
                    size = Size(w * 0.28f, h * 0.14f),
                    cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
                    style = stroke
                )
                drawLine(tint, Offset(w * 0.34f, h * 0.46f), Offset(w * 0.66f, h * 0.46f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.34f, h * 0.64f), Offset(w * 0.58f, h * 0.64f), stroke.width, StrokeCap.Round)
            }

            KeskeIconType.EMOJI -> {
                val r = w * 0.38f
                val c = Offset(w * 0.5f, h * 0.5f)
                drawCircle(color = tint, radius = r, center = c, style = stroke)
                drawCircle(color = tint, radius = stroke.width * 0.65f, center = Offset(w * 0.37f, h * 0.42f))
                drawCircle(color = tint, radius = stroke.width * 0.65f, center = Offset(w * 0.63f, h * 0.42f))
                val smile = Path().apply {
                    moveTo(w * 0.35f, h * 0.58f)
                    quadraticTo(w * 0.50f, h * 0.72f, w * 0.65f, h * 0.58f)
                }
                drawPath(smile, color = tint, style = stroke)
            }

            KeskeIconType.SETTINGS -> {
                val c = Offset(w * 0.5f, h * 0.5f)
                drawCircle(color = tint, radius = w * 0.16f, center = c, style = stroke)
                drawCircle(color = tint, radius = w * 0.32f, center = c, style = stroke)
                val dirs = listOf(
                    Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f),
                    Offset(0.7f, 0.7f), Offset(-0.7f, -0.7f), Offset(0.7f, -0.7f), Offset(-0.7f, 0.7f)
                )
                dirs.forEach { d ->
                    drawLine(
                        color = tint,
                        start = Offset(c.x + d.x * w * 0.31f, c.y + d.y * h * 0.31f),
                        end = Offset(c.x + d.x * w * 0.41f, c.y + d.y * h * 0.41f),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round
                    )
                }
            }

            KeskeIconType.HIDE_KEYBOARD -> {
                val p = Path().apply {
                    moveTo(w * 0.22f, h * 0.36f)
                    lineTo(w * 0.50f, h * 0.66f)
                    lineTo(w * 0.78f, h * 0.36f)
                }
                drawPath(p, color = tint, style = stroke)
            }

            KeskeIconType.IME_SWITCHER -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.12f, h * 0.24f),
                    size = Size(w * 0.76f, h * 0.52f),
                    cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                    style = stroke
                )
                val dotR = stroke.width * 0.55f
                for (col in listOf(0.28f, 0.50f, 0.72f)) {
                    drawCircle(tint, dotR, Offset(w * col, h * 0.40f))
                }
                drawLine(tint, Offset(w * 0.30f, h * 0.60f), Offset(w * 0.70f, h * 0.60f), stroke.width, StrokeCap.Round)
            }

            KeskeIconType.HEX_GRID -> {
                val p = Path().apply {
                    moveTo(w * 0.50f, h * 0.12f)
                    lineTo(w * 0.84f, h * 0.31f)
                    lineTo(w * 0.84f, h * 0.69f)
                    lineTo(w * 0.50f, h * 0.88f)
                    lineTo(w * 0.16f, h * 0.69f)
                    lineTo(w * 0.16f, h * 0.31f)
                    close()
                }
                drawPath(p, color = tint, style = stroke)
            }
        }
    }
}
