@file:OptIn(ExperimentalMaterial3Api::class)

package com.skintracker.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.skintracker.app.data.Settings
import com.skintracker.app.data.Snap
import com.skintracker.app.data.Stat
import com.skintracker.app.data.Tracked
import com.skintracker.app.data.analyze
import com.skintracker.app.data.confidenceText
import com.skintracker.app.data.fmtMoney
import com.skintracker.app.data.fmtPct
import com.skintracker.app.data.fmtSignedMoney
import java.util.Locale

private fun numText(d: Double?): String = if (d == null) "" else String.format(Locale.US, "%.2f", d)
private fun parseNum(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", color = C.muted)
        Text(text, color = C.text, fontSize = 14.sp)
    }
}

@Composable
private fun KeyValue(label: String, value: String, sub: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = C.muted, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(value, fontWeight = FontWeight.Medium)
            if (sub != null) Text(sub, color = C.muted, fontSize = 12.sp)
        }
    }
}

private fun statText(s: Stat?): String {
    val m = if (s == null) null else (s.median ?: s.avg)
    return if (m == null || s == null || s.volume == 0) "—" else "${fmtMoney(m)} · продано ${s.volume}"
}

@Composable
private fun StatCell(label: String, value: Double?, text: String? = null) {
    Column {
        Text(label, color = C.muted, fontSize = 11.sp)
        Text(text ?: fmtMoney(value), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        if (text == null) RubLine(value, fontSize = 11.sp)
    }
}

@Composable
fun DetailScreen(
    t: Tracked?,
    settings: Settings,
    onBack: () -> Unit,
    onPosition: (Double?, Double?, Double?) -> Unit,
    onSold: (Double) -> Unit,
    history: List<Snap>?,
    onNeedHistory: () -> Unit,
    onDelete: () -> Unit
) {
    if (t == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val a = remember(t, settings) { analyze(t, settings, System.currentTimeMillis()) }
    val n = parseName(t.name)
    val ctx = LocalContext.current
    var showPos by remember { mutableStateOf(false) }
    var showSold by remember { mutableStateOf(false) }
    LaunchedEffect(t.name) { onNeedHistory() }
    var window by rememberSaveable { mutableStateOf(2) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.bg)
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ───── шапка ─────
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.radialGradient(listOf(signalColor(a.signal).copy(alpha = 0.18f), C.card))),
                contentAlignment = Alignment.Center
            ) {
                if (t.icon != null) {
                    AsyncImage(
                        model = t.icon, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (n.top.isNotEmpty()) Text(n.top, color = C.muted)
                    Text(n.skin, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    if (n.wear != null) Text(n.wear, color = C.muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    val shown = a.bestBuy?.ask ?: a.price
                    Text(fmtMoney(shown), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    RubLine(shown, fontSize = 14.sp)
                    if (a.price != null) ChangeText(a.changes.getOrNull(0), 13.sp)
                }
            }

            // ───── вывод аналитики ─────
            Section("СОВЕТ") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SignalBadge(a.signal)
                    if (a.fair != null) Text(confidenceText(a.confidence), color = C.muted, fontSize = 12.sp)
                }
                Text(a.headline, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                a.reasons.forEach { Bullet(it) }
            }

            // ───── цены на площадках ─────
            if (a.platforms.isNotEmpty()) {
                Section("ГДЕ СКОЛЬКО СТОИТ") {
                    a.platforms.forEach { p ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.title, fontWeight = FontWeight.Medium)
                                Text("в продаже: ${p.qty}", color = C.muted, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(fmtMoney(p.ask), fontWeight = FontWeight.Bold)
                                RubLine(p.ask)
                            }
                        }
                    }
                    val b = a.bestBuy
                    val sl = a.bestSell
                    if (b != null) Bullet("Купить дешевле всего: ${b.title} (${fmtMoney(b.ask)}).")
                    if (sl != null) Bullet("Продать выгоднее всего: ${sl.title}. На руки останется ${fmtMoney(sl.net)} (комиссия уже вычтена).")
                }
            }

            // ───── мои цены ─────
            Section("МОИ ЦЕНЫ") {
                if (t.buyPrice == null && t.targetSell == null && t.targetBuy == null) {
                    Text(
                        "Напиши, за сколько ты купил — я покажу, сколько заработаешь. Ещё можно указать цену, при которой сообщить тебе.",
                        color = C.muted
                    )
                }
                if (t.buyPrice != null) {
                    KeyValue("Куплено за", fmtMoney(t.buyPrice), fmtRub(t.buyPrice, LocalRub.current))
                    if (a.profitAbs != null && a.profitPct != null) {
                        val col = if (a.profitAbs >= 0) C.up else C.down
                        Column {
                            Text(
                                (if (a.profitAbs >= 0) "Если продать сейчас, заработаешь " else "Если продать сейчас, потеряешь ") + "${fmtSignedMoney(a.profitAbs)} (${fmtPct(a.profitPct)})",
                                color = col, fontWeight = FontWeight.Bold
                            )
                            RubLine(a.profitAbs, signed = true, color = col)
                            Text(
                                "считаю по реальным продажам и уже вычитаю комиссию площадки",
                                color = C.muted, fontSize = 12.sp
                            )
                        }
                    }
                }
                if (t.targetSell != null) KeyValue("Сообщить, когда дороже", fmtMoney(t.targetSell), fmtRub(t.targetSell, LocalRub.current))
                if (t.targetBuy != null) KeyValue("Сообщить, когда дешевле", fmtMoney(t.targetBuy), fmtRub(t.targetBuy, LocalRub.current))
                if (t.buyPrice != null) {
                    OutlinedButton(onClick = { showSold = true }) { Text("Я продал этот скин") }
                }
                OutlinedButton(onClick = { showPos = true }) {
                    Text(
                        if (t.buyPrice == null && t.targetSell == null && t.targetBuy == null) "Указать цены" else "Изменить"
                    )
                }
            }

            // ───── динамика ─────
            Section("КАК МЕНЯЛАСЬ ЦЕНА") {
                val now = System.currentTimeMillis()
                val day = 24L * 3_600_000L
                val span = when (window) {
                    0 -> day
                    1 -> 7 * day
                    2 -> 30 * day
                    3 -> 90 * day
                    else -> Long.MAX_VALUE
                }
                // готовая история с GitHub + свои проверки приложения после неё
                val hist = history ?: emptyList()
                val lastHist = hist.lastOrNull()?.t ?: Long.MIN_VALUE
                val merged: List<Snap> = hist + t.snaps.filter { it.t > lastHist }
                val own: List<Snap> = if (span == Long.MAX_VALUE) merged else merged.filter { it.t >= now - span }
                // пока своей истории мало — рисуем примерную по средним ценам продаж
                val rough = ArrayList<Snap>()
                val sl = t.sales
                fun addRough(ago: Long, st: Stat?) {
                    val m = if (st == null) null else (st.median ?: st.avg)
                    if (m != null && st != null && st.volume > 0) rough.add(Snap(now - ago, m))
                }
                addRough(45 * day, sl?.d90)
                addRough(15 * day, sl?.d30)
                addRough(day * 7 / 2, sl?.d7)
                addRough(day / 2, sl?.d1)
                (t.market?.minPrice)?.let { rough.add(Snap(now, it)) }
                val useRough = own.size < 3
                val shown: List<Snap> =
                    if (useRough) rough.filter { span == Long.MAX_VALUE || it.t >= now - span } else own

                val stat: Stat? = when (window) {
                    0 -> sl?.d1
                    1 -> sl?.d7
                    2 -> sl?.d30
                    else -> sl?.d90
                }
                if (stat != null && stat.volume > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatCell("Мин. цена", stat.min)
                        StatCell("Макс. цена", stat.max)
                        StatCell("Продаж", null, stat.volume.toString())
                        StatCell("Средняя", stat.avg ?: stat.median)
                    }
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("24 ч", "7 дн", "30 дн", "3 мес", "Всё время").forEachIndexed { i, l ->
                        TagChip(l, window == i) { window = i }
                    }
                }
                PriceChart(shown, Modifier.fillMaxWidth().height(190.dp))
                if (useRough && shown.size >= 2) {
                    Text(
                        "График примерный: он построен по ценам продаж. Точнее станет, когда накопится история (её можно включить в настройках).",
                        color = C.muted, fontSize = 11.sp
                    )
                }
                if (sl != null) {
                    Text("Сколько продаж в день", color = C.muted, fontSize = 12.sp)
                    SalesBars(sl)
                }
                a.changes.forEach { c ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.label, Modifier.weight(1f))
                        if (c.pct == null || c.abs == null) {
                            Text("пока мало данных", color = C.muted, fontSize = 13.sp)
                        } else {
                            Text(
                                fmtSignedMoney(c.abs) + "  " + arrowPct(c.pct, c.approx),
                                color = changeColor(c.pct), fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Text(
                    "Значок ≈ значит «примерно»: приложение только начало следить за скином и сравнивает с ценой прошлых продаж.",
                    color = C.muted, fontSize = 11.sp
                )
            }

            // ───── продажи ─────
            Section("ПРОДАЖИ НА SKINPORT") {
                KeyValue("Обычная цена", fmtMoney(a.fair), fmtRub(a.fair, LocalRub.current))
                KeyValue("Цена последних продаж", if (a.marketNow != null) fmtMoney(a.marketNow) else "—")
                KeyValue("Сейчас в продаже", (t.market?.quantity ?: 0).toString() + " шт.")
                KeyValue("За сутки", statText(t.sales?.d1))
                KeyValue("За неделю", statText(t.sales?.d7))
                KeyValue("За месяц", statText(t.sales?.d30))
                KeyValue("За 3 месяца", statText(t.sales?.d90))
                val d90 = t.sales?.d90
                val mn = d90?.min
                val mx = d90?.max
                if (mn != null && mx != null) KeyValue("Была от и до (3 месяца)", "${fmtMoney(mn)} – ${fmtMoney(mx)}")
                if (a.liquidity != null) Text(a.liquidity, color = C.muted, fontSize = 13.sp)
                Text(
                    "Обычная цена — то, за сколько скин чаще всего продавался за последние месяцы. Одна странная сделка её не меняет.",
                    color = C.muted, fontSize = 11.sp
                )
            }

            Button(
                onClick = {
                    val url = t.market?.itemPage
                        ?: ("https://skinport.com/market?search=" + Uri.encode(t.name))
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.accent, contentColor = C.bg)
            ) { Text("Открыть на Skinport", fontWeight = FontWeight.Bold) }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text("Убрать из списка", color = C.down)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showSold) {
        var got by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSold = false },
            containerColor = C.card,
            title = { Text("Я продал") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = got, onValueChange = { got = it }, singleLine = true,
                        label = { Text("Сколько получил на руки, \$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Text(
                        "Пиши сумму уже после комиссии площадки. Сделка попадёт в «Мои сделки».",
                        color = C.muted, fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = parseNum(got)
                    if (v != null && v > 0) {
                        onSold(v)
                        showSold = false
                    }
                }) { Text("Записать") }
            },
            dismissButton = { TextButton(onClick = { showSold = false }) { Text("Отмена") } }
        )
    }

    if (showPos) {
        var buy by remember { mutableStateOf(numText(t.buyPrice)) }
        var sell by remember { mutableStateOf(numText(t.targetSell)) }
        var low by remember { mutableStateOf(numText(t.targetBuy)) }
        val kb = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        AlertDialog(
            onDismissRequest = { showPos = false },
            containerColor = C.card,
            title = { Text("Мои цены, в долларах") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = buy, onValueChange = { buy = it }, singleLine = true,
                        label = { Text("Купил за") }, keyboardOptions = kb
                    )
                    OutlinedTextField(
                        value = sell, onValueChange = { sell = it }, singleLine = true,
                        label = { Text("Сообщить, когда станет дороже") }, keyboardOptions = kb
                    )
                    OutlinedTextField(
                        value = low, onValueChange = { low = it }, singleLine = true,
                        label = { Text("Сообщить, когда станет дешевле") }, keyboardOptions = kb
                    )
                    Text("Пустое поле — ничего не задано.", color = C.muted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onPosition(parseNum(buy), parseNum(sell), parseNum(low))
                    showPos = false
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { showPos = false }) { Text("Отмена") } }
        )
    }
}
