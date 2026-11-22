package com.skintracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.skintracker.app.data.Change
import com.skintracker.app.data.Signal
import com.skintracker.app.data.Snap
import com.skintracker.app.data.fmtMoney
import java.util.Locale
import kotlin.math.abs

/** Курс рубля к доллару; 0 — рубли не показываем. */
val LocalRub = compositionLocalOf { 0.0 }

fun fmtRub(usd: Double?, rate: Double, signed: Boolean = false): String? {
    if (usd == null || rate <= 0.0) return null
    val num = String.format(Locale.US, "%,.0f", abs(usd * rate)).replace(',', ' ')
    val sign = if (!signed) "" else if (usd >= 0) "+" else "−"
    return "≈ $sign$num ₽"
}

@Composable
fun RubLine(
    usd: Double?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
    color: Color = C.muted,
    signed: Boolean = false
) {
    val r = fmtRub(usd, LocalRub.current, signed) ?: return
    Text(r, color = color, fontSize = fontSize, modifier = modifier)
}

/** «$12.40 (≈ 1 020 ₽)» одной строкой. */
@Composable
fun usdRub(usd: Double?): String {
    val r = fmtRub(usd, LocalRub.current)
    return if (r == null) fmtMoney(usd) else fmtMoney(usd) + " (" + r + ")"
}

data class ParsedName(val top: String, val skin: String, val wear: String?)

/** "StatTrak™ AK-47 | Redline (Field-Tested)" -> верх: "StatTrak™ AK-47", скин: "Redline", износ: "Field-Tested" */
fun parseName(full: String): ParsedName {
    var s = full
    var prefix = ""
    for (p in listOf("★ StatTrak™ ", "StatTrak™ ", "Souvenir ", "★ ")) {
        if (s.startsWith(p)) {
            prefix = p.trim()
            s = s.removePrefix(p)
            break
        }
    }
    var wear: String? = null
    val m = Regex("\\s\\(([^)]+)\\)$").find(s)
    if (m != null) {
        wear = m.groupValues[1]
        s = s.substring(0, m.range.first)
    }
    val parts = s.split(" | ")
    val weapon = parts[0]
    val skin = parts.getOrNull(1)
    return if (skin == null) ParsedName(prefix, weapon, wear)
    else ParsedName((prefix + " " + weapon).trim(), skin, wear)
}

val WEARS = listOf("Factory New", "Minimal Wear", "Field-Tested", "Well-Worn", "Battle-Scarred")

fun wearOf(full: String): String? = Regex("\\s\\(([^)]+)\\)$").find(full)?.groupValues?.get(1)

fun baseOf(full: String): String = full.replace(Regex("\\s\\([^)]+\\)$"), "")

fun wearShort(w: String?): String = when (w) {
    "Factory New" -> "FN"
    "Minimal Wear" -> "MW"
    "Field-Tested" -> "FT"
    "Well-Worn" -> "WW"
    "Battle-Scarred" -> "BS"
    null -> ""
    else -> w
}

fun wearOrder(full: String): Int {
    val i = WEARS.indexOf(wearOf(full))
    return if (i < 0) 99 else i
}

fun signalColor(s: Signal): Color = when (s) {
    Signal.SELL -> C.sell
    Signal.BUY -> C.buy
    Signal.FLIP -> C.flip
    Signal.HOLD -> C.muted
    Signal.NO_DATA -> C.muted
}

fun signalText(s: Signal): String = when (s) {
    Signal.SELL -> "ПРОДАВАТЬ"
    Signal.BUY -> "ПОКУПАТЬ"
    Signal.FLIP -> "ПЕРЕКУП"
    Signal.HOLD -> "ЖДАТЬ"
    Signal.NO_DATA -> "СОВЕТА НЕТ"
}

@Composable
fun SignalBadge(s: Signal) {
    val col = signalColor(s)
    Box(
        Modifier
            .background(col.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(signalText(s), color = col, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TagChip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) C.accent else C.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            color = if (selected) C.bg else C.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Стрелка и процент: «▲ 3.2%». */
fun arrowPct(pct: Double?, approx: Boolean = false): String {
    if (pct == null) return "—"
    val arrow = when {
        pct > 0.05 -> "▲ "
        pct < -0.05 -> "▼ "
        else -> "• "
    }
    return (if (approx) "≈ " else "") + arrow + String.format(Locale.US, "%.1f%%", abs(pct))
}

fun changeColor(pct: Double?): Color = when {
    pct == null -> C.muted
    pct > 0.05 -> C.up
    pct < -0.05 -> C.down
    else -> C.muted
}

@Composable
fun ChangeText(c: Change?, fontSize: TextUnit = 12.sp) {
    Text(
        arrowPct(c?.pct, c?.approx == true),
        color = changeColor(c?.pct),
        fontSize = fontSize,
        fontWeight = FontWeight.Medium
    )
}

@Composable
fun SkinImage(url: String?, size: Dp, corner: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Brush.radialGradient(listOf(C.card2, C.bg))),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = url, contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(6.dp)
            )
        }
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = C.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        content()
    }
}

private fun fmtAxisDate(t: Long, spanMs: Long): String {
    val f = if (spanMs <= 2L * 86_400_000L) "HH:mm" else "dd.MM"
    return java.text.SimpleDateFormat(f, Locale.getDefault()).format(java.util.Date(t))
}

/** График цены: сетка, подписи цены слева и дат снизу. */
@Composable
fun PriceChart(snaps: List<Snap>, modifier: Modifier = Modifier) {
    if (snaps.size < 2) {
        Box(
            modifier.background(C.card2, RoundedCornerShape(16.dp)).padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("График появится после нескольких проверок цены", color = C.muted, fontSize = 13.sp)
        }
        return
    }
    val minP = snaps.minOf { it.p }
    val maxP = snaps.maxOf { it.p }
    val pad = (maxP - minP).let { if (it > 0.0) it * 0.1 else maxP * 0.02 + 0.01 }
    val lo = minP - pad
    val hi = maxP + pad
    val col = if (snaps.last().p >= snaps.first().p) C.up else C.down
    val t0 = snaps.first().t
    val t1 = snaps.last().t
    val span = (t1 - t0).coerceAtLeast(1L)
    Column(modifier.background(C.card2, RoundedCornerShape(16.dp)).padding(top = 8.dp, bottom = 6.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val range = hi - lo
                fun px(t: Long): Float = (t - t0).toFloat() / span.toFloat() * w
                fun py(p: Double): Float = h - ((p - lo) / range).toFloat() * h
                for (k in 0..3) {
                    val y = h * k / 3f
                    drawLine(C.muted.copy(alpha = 0.15f), Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
                }
                val line = Path()
                val fill = Path()
                snaps.forEachIndexed { i, sn ->
                    val x = px(sn.t)
                    val y = py(sn.p)
                    if (i == 0) {
                        line.moveTo(x, y)
                        fill.moveTo(x, h)
                        fill.lineTo(x, y)
                    } else {
                        line.lineTo(x, y)
                        fill.lineTo(x, y)
                    }
                }
                fill.lineTo(px(t1), h)
                fill.close()
                drawPath(fill, Brush.verticalGradient(listOf(col.copy(alpha = 0.30f), Color.Transparent)))
                drawPath(line, col, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val last = snaps.last()
                drawCircle(col, 4.dp.toPx(), Offset(px(last.t), py(last.p)))
            }
            Column(Modifier.fillMaxSize().padding(start = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(fmtMoney(hi), color = C.muted, fontSize = 10.sp)
                Text(fmtMoney((hi + lo) / 2), color = C.muted, fontSize = 10.sp)
                Text(fmtMoney(lo), color = C.muted, fontSize = 10.sp)
            }
        }
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(fmtAxisDate(t0, span), color = C.muted, fontSize = 10.sp)
            Text(fmtAxisDate(t0 + span / 2, span), color = C.muted, fontSize = 10.sp)
            Text(fmtAxisDate(t1, span), color = C.muted, fontSize = 10.sp)
        }
    }
}

/** Столбики: сколько продаж в день было в разные периоды. */
@Composable
fun SalesBars(sales: com.skintracker.app.data.Sales?, modifier: Modifier = Modifier) {
    if (sales == null) return
    val v1 = sales.d1.volume
    val v7 = sales.d7.volume
    val v30 = sales.d30.volume
    val v90 = sales.d90.volume
    val items = listOf(
        "сутки" to v1.toDouble(),
        "2–7 дн" to ((v7 - v1).coerceAtLeast(0) / 6.0),
        "8–30 дн" to ((v30 - v7).coerceAtLeast(0) / 23.0),
        "31–90 дн" to ((v90 - v30).coerceAtLeast(0) / 60.0)
    ).reversed()
    val mx = items.maxOf { it.second }.coerceAtLeast(1.0)
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        items.forEach { (label, v) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(String.format(Locale.US, "%.0f", v), color = C.text, fontSize = 11.sp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((8 + 52 * (v / mx)).dp)
                        .background(C.accent.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                )
                Text(label, color = C.muted, fontSize = 10.sp)
            }
        }
    }
}
