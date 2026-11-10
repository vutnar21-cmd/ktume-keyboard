package org.chuvash.ktume.ime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import org.chuvash.ktume.data.EnterKeyAction

/**
 * МОДУЛЬ: KesjeIcons.kt — единый монохромный векторный набор иконок на Canvas
 * для функциональных клавиш, контекстного Enter, меню раскладок и верхней панели.
 */
enum class KesjeIconType {
    SHIFT,
    CAPS_LOCK,
    BACKSPACE,
    ENTER,
    SEARCH,
    SEND,
    NEXT,
    DONE,
    GLOBE,
    CLIPBOARD,
    EMOJI,
    SETTINGS,
    IME_SWITCHER,
    ARROW_LEFT,
    ARROW_RIGHT
}

internal fun EnterKeyAction.toIconType(): KesjeIconType = when (this) {
    EnterKeyAction.NEWLINE -> KesjeIconType.ENTER
    EnterKeyAction.SEARCH -> KesjeIconType.SEARCH
    EnterKeyAction.SEND -> KesjeIconType.SEND
    EnterKeyAction.GO -> KesjeIconType.ARROW_RIGHT
    EnterKeyAction.NEXT -> KesjeIconType.NEXT
    EnterKeyAction.DONE -> KesjeIconType.DONE
}

private val SETTINGS_GEAR_DIRS = arrayOf(
    Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f),
    Offset(0.7f, 0.7f), Offset(-0.7f, -0.7f), Offset(0.7f, -0.7f), Offset(-0.7f, 0.7f)
)

@Composable
fun KesjeVectorIcon(
    type: KesjeIconType,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    val path = remember { Path() }
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(
            width = (w * 0.105f).coerceAtLeast(2.2f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        when (type) {
            KesjeIconType.SHIFT, KesjeIconType.CAPS_LOCK -> {
                path.reset()
                path.moveTo(w * 0.50f, h * 0.14f)
                path.lineTo(w * 0.86f, h * 0.50f)
                path.lineTo(w * 0.65f, h * 0.50f)
                path.lineTo(w * 0.65f, h * 0.76f)
                path.lineTo(w * 0.35f, h * 0.76f)
                path.lineTo(w * 0.35f, h * 0.50f)
                path.lineTo(w * 0.14f, h * 0.50f)
                path.close()
                drawPath(path, color = tint, style = stroke)
                if (type == KesjeIconType.CAPS_LOCK) {
                    drawLine(
                        color = tint,
                        start = Offset(w * 0.32f, h * 0.88f),
                        end = Offset(w * 0.68f, h * 0.88f),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round
                    )
                }
            }

            KesjeIconType.BACKSPACE -> {
                path.reset()
                path.moveTo(w * 0.34f, h * 0.22f)
                path.lineTo(w * 0.88f, h * 0.22f)
                path.lineTo(w * 0.88f, h * 0.78f)
                path.lineTo(w * 0.34f, h * 0.78f)
                path.lineTo(w * 0.12f, h * 0.50f)
                path.close()
                drawPath(path, color = tint, style = stroke)
                drawLine(tint, Offset(w * 0.46f, h * 0.38f), Offset(w * 0.68f, h * 0.62f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.68f, h * 0.38f), Offset(w * 0.46f, h * 0.62f), stroke.width, StrokeCap.Round)
            }

            KesjeIconType.ENTER -> {
                path.reset()
                path.moveTo(w * 0.78f, h * 0.22f)
                path.lineTo(w * 0.78f, h * 0.58f)
                path.lineTo(w * 0.22f, h * 0.58f)
                path.moveTo(w * 0.38f, h * 0.40f)
                path.lineTo(w * 0.20f, h * 0.58f)
                path.lineTo(w * 0.38f, h * 0.76f)
                drawPath(path, color = tint, style = stroke)
            }

            KesjeIconType.SEARCH -> {
                val r = w * 0.26f
                val c = Offset(w * 0.43f, h * 0.43f)
                drawCircle(color = tint, radius = r, center = c, style = stroke)
                drawLine(
                    color = tint,
                    start = Offset(w * 0.62f, h * 0.62f),
                    end = Offset(w * 0.85f, h * 0.85f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
            }

            KesjeIconType.SEND -> {
                path.reset()
                path.moveTo(w * 0.16f, h * 0.22f)
                path.lineTo(w * 0.86f, h * 0.50f)
                path.lineTo(w * 0.16f, h * 0.78f)
                path.lineTo(w * 0.30f, h * 0.50f)
                path.close()
                drawPath(path, color = tint, style = stroke)
                drawLine(tint, Offset(w * 0.30f, h * 0.50f), Offset(w * 0.82f, h * 0.50f), stroke.width, StrokeCap.Round)
            }

            KesjeIconType.NEXT -> {
                drawLine(tint, Offset(w * 0.14f, h * 0.50f), Offset(w * 0.68f, h * 0.50f), stroke.width, StrokeCap.Round)
                path.reset()
                path.moveTo(w * 0.46f, h * 0.28f)
                path.lineTo(w * 0.68f, h * 0.50f)
                path.lineTo(w * 0.46f, h * 0.72f)
                drawPath(path, color = tint, style = stroke)
                drawLine(tint, Offset(w * 0.84f, h * 0.24f), Offset(w * 0.84f, h * 0.76f), stroke.width, StrokeCap.Round)
            }

            KesjeIconType.DONE -> {
                path.reset()
                path.moveTo(w * 0.18f, h * 0.52f)
                path.lineTo(w * 0.42f, h * 0.76f)
                path.lineTo(w * 0.84f, h * 0.26f)
                drawPath(path, color = tint, style = stroke)
            }

            KesjeIconType.GLOBE -> {
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

            KesjeIconType.CLIPBOARD -> {
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

            KesjeIconType.EMOJI -> {
                val r = w * 0.38f
                val c = Offset(w * 0.5f, h * 0.5f)
                drawCircle(color = tint, radius = r, center = c, style = stroke)
                drawCircle(color = tint, radius = stroke.width * 0.65f, center = Offset(w * 0.37f, h * 0.42f))
                drawCircle(color = tint, radius = stroke.width * 0.65f, center = Offset(w * 0.63f, h * 0.42f))
                path.reset()
                path.moveTo(w * 0.35f, h * 0.58f)
                path.quadraticTo(w * 0.50f, h * 0.72f, w * 0.65f, h * 0.58f)
                drawPath(path, color = tint, style = stroke)
            }

            KesjeIconType.SETTINGS -> {
                val c = Offset(w * 0.5f, h * 0.5f)
                drawCircle(color = tint, radius = w * 0.16f, center = c, style = stroke)
                drawCircle(color = tint, radius = w * 0.32f, center = c, style = stroke)
                SETTINGS_GEAR_DIRS.forEach { d ->
                    drawLine(
                        color = tint,
                        start = Offset(c.x + d.x * w * 0.31f, c.y + d.y * h * 0.31f),
                        end = Offset(c.x + d.x * w * 0.41f, c.y + d.y * h * 0.41f),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round
                    )
                }
            }

            KesjeIconType.IME_SWITCHER -> {
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

            KesjeIconType.ARROW_LEFT -> {
                drawLine(tint, Offset(w * 0.80f, h * 0.50f), Offset(w * 0.20f, h * 0.50f), stroke.width, StrokeCap.Round)
                path.reset()
                path.moveTo(w * 0.44f, h * 0.26f)
                path.lineTo(w * 0.20f, h * 0.50f)
                path.lineTo(w * 0.44f, h * 0.74f)
                drawPath(path, color = tint, style = stroke)
            }

            KesjeIconType.ARROW_RIGHT -> {
                drawLine(tint, Offset(w * 0.20f, h * 0.50f), Offset(w * 0.80f, h * 0.50f), stroke.width, StrokeCap.Round)
                path.reset()
                path.moveTo(w * 0.56f, h * 0.26f)
                path.lineTo(w * 0.80f, h * 0.50f)
                path.lineTo(w * 0.56f, h * 0.74f)
                drawPath(path, color = tint, style = stroke)
            }
        }
    }
}
