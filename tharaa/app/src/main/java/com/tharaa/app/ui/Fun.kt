package com.tharaa.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tharaa.app.domain.Phase
import kotlin.random.Random

/** جرّة مرسومة بلون الصندوق، والسائل فيها بيعكس قديش مليانة. */
@Composable
fun Jar(fill: Float, color: Color, modifier: Modifier = Modifier) {
    val level by animateFloatAsState(fill.coerceIn(0f, 1f), tween(900), label = "jar")
    val glass = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val neckW = w * 0.56f
        val neckH = h * 0.12f
        val bodyTop = neckH + h * 0.04f
        val body = Path().apply {
            moveTo((w - neckW) / 2, bodyTop)
            cubicTo(w * 0.02f, bodyTop, w * 0.02f, bodyTop + h * 0.08f, w * 0.02f, bodyTop + h * 0.2f)
            lineTo(w * 0.02f, h * 0.86f)
            cubicTo(w * 0.02f, h * 0.98f, w * 0.12f, h, w * 0.22f, h)
            lineTo(w * 0.78f, h)
            cubicTo(w * 0.88f, h, w * 0.98f, h * 0.98f, w * 0.98f, h * 0.86f)
            lineTo(w * 0.98f, bodyTop + h * 0.2f)
            cubicTo(w * 0.98f, bodyTop + h * 0.08f, w * 0.98f, bodyTop, (w + neckW) / 2, bodyTop)
            close()
        }
        // السائل
        clipPath(body) {
            val top = bodyTop + (h - bodyTop) * (1f - level)
            drawRect(color.copy(alpha = 0.85f), topLeft = Offset(0f, top), size = Size(w, h - top))
            drawRect(Color.White.copy(alpha = 0.18f), topLeft = Offset(w * 0.12f, top), size = Size(w * 0.1f, h - top))
        }
        drawPath(body, glass, style = Stroke(width = 2.dp.toPx()))
        // الغطاء
        drawRoundRect(
            color = color,
            topLeft = Offset((w - neckW * 1.12f) / 2, 0f),
            size = Size(neckW * 1.12f, neckH),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
    }
}

/** طريق المراحل الأربع، ونقطة ذهبية عند مرحلتك. */
@Composable
fun PhaseRoad(current: Phase, onDark: Boolean = true, modifier: Modifier = Modifier.fillMaxWidth()) {
    val base = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val n = Phase.entries.size
            val y = size.height / 2
            val pad = 11.dp.toPx()
            val step = (size.width - pad * 2) / (n - 1)
            // الطريق بيمشي من اليمين لليسار (عربي)
            fun xOf(i: Int) = size.width - pad - step * i
            drawLine(base.copy(alpha = 0.25f), Offset(xOf(0), y), Offset(xOf(n - 1), y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Gold, Offset(xOf(0), y), Offset(xOf(current.step - 1), y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            Phase.entries.forEachIndexed { i, p ->
                val done = p.step <= current.step
                val r = if (p == current) 9.dp.toPx() else 5.dp.toPx()
                drawCircle(if (done) Gold else base.copy(alpha = 0.35f), r, Offset(xOf(i), y))
                if (p == current) drawCircle(Color.White, 3.5.dp.toPx(), Offset(xOf(i), y))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Phase.entries.forEach { p ->
                Text(
                    p.title,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    color = if (p == current) Gold else base.copy(alpha = 0.6f),
                    fontWeight = if (p == current) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

data class Slice(val label: String, val value: Double, val color: Color)

/** دائرة المصاريف حسب التصنيف، والمجموع بالنص. */
@Composable
fun Donut(slices: List<Slice>, center: String, sub: String, modifier: Modifier = Modifier.size(190.dp)) {
    val total = slices.sumOf { it.value }.coerceAtLeast(0.0001)
    val sweep by animateFloatAsState(1f, tween(800), label = "donut")
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 26.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            var start = -90f
            slices.forEach { s ->
                val a = (s.value / total * 360f * sweep).toFloat()
                drawArc(s.color, start, (a - 1.5f).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                start += a
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(center, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun StreakChip(days: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (days > 0) Gold.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (days > 0) "🔥" else "🌱", fontSize = 14.sp)
        Spacer(Modifier.width(4.dp))
        Text(
            if (days > 0) "$days ${if (days in 3..10) "أيام" else "يوم"} متتالية" else "ابدأ سلسلتك اليوم",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private data class Bit(val x: Float, val speed: Float, val drift: Float, val spin: Float, val color: Color, val w: Float, val delay: Float)

private val confettiColors = listOf(Gold, Color(0xFF2E7D57), Color(0xFFE8C15A), Color(0xFF00897B), Color(0xFFD84315), Color(0xFF8E24AA))

/** احتفال بالإنجاز: قصاصات ذهبية بتنزل + رسالة. بينتهي لحاله. */
@Composable
fun Celebration(message: String, onDone: () -> Unit) {
    val bits = remember(message) {
        val r = Random(message.hashCode())
        List(70) {
            Bit(
                x = r.nextFloat(), speed = 0.6f + r.nextFloat() * 0.8f, drift = (r.nextFloat() - 0.5f) * 0.25f,
                spin = r.nextFloat() * 720f, color = confettiColors[r.nextInt(confettiColors.size)],
                w = 6f + r.nextFloat() * 6f, delay = r.nextFloat() * 0.3f,
            )
        }
    }
    val t = remember(message) { Animatable(0f) }
    LaunchedEffect(message) {
        t.animateTo(1f, tween(2800, easing = LinearEasing))
        onDone()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            bits.forEach { b ->
                val p = ((t.value - b.delay) / (1f - b.delay)).coerceIn(0f, 1f)
                if (p <= 0f) return@forEach
                val y = -20f + (size.height + 40f) * p * b.speed
                val x = size.width * (b.x + b.drift * p)
                rotate(b.spin * p, Offset(x, y)) {
                    drawRect(b.color.copy(alpha = 1f - p * 0.6f), Offset(x, y), Size(b.w.dp.toPx() / 2, b.w.dp.toPx()))
                }
            }
        }
        val alpha = when {
            t.value < 0.1f -> t.value * 10
            t.value > 0.8f -> (1f - t.value) * 5
            else -> 1f
        }
        Column(
            Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(DeepGreen.copy(alpha = 0.94f * alpha))
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🏆", fontSize = 44.sp, color = Color.White.copy(alpha = alpha))
            Text(message, color = Color.White.copy(alpha = alpha), fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}
