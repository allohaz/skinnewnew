@file:OptIn(ExperimentalMaterial3Api::class)

package com.skintracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skintracker.app.data.Analysis
import com.skintracker.app.data.Platforms
import com.skintracker.app.data.Signal
import com.skintracker.app.data.Stored
import com.skintracker.app.data.Tracked
import com.skintracker.app.data.analyze
import com.skintracker.app.data.fmtAbsPct
import com.skintracker.app.data.fmtMoney
import com.skintracker.app.data.fmtSignedMoney

private typealias Row2 = Pair<Tracked, Analysis>

private val SORTS = listOf("Сначала советы", "Больше всего выросли", "Больше всего упали", "Дорогие сверху", "По названию")
private val SORT_SHORT = listOf("Советы", "Рост", "Падение", "Цена", "А–Я")

private fun ago(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60_000
    return when {
        m < 1 -> "только что"
        m < 60 -> "$m мин назад"
        m < 24 * 60 -> "${m / 60} ч назад"
        else -> "${m / (24 * 60)} дн назад"
    }
}

private fun signalOrder(s: Signal): Int = when (s) {
    Signal.SELL -> 0
    Signal.BUY -> 1
    Signal.FLIP -> 2
    Signal.HOLD -> 3
    Signal.NO_DATA -> 4
}

private fun shownPrice(a: Analysis): Double? = a.bestBuy?.ask ?: a.price

private fun comparatorFor(sort: Int): Comparator<Row2> = when (sort) {
    1 -> compareByDescending<Row2> { it.second.changes.getOrNull(0)?.pct ?: Double.NEGATIVE_INFINITY }
    2 -> compareBy<Row2> { it.second.changes.getOrNull(0)?.pct ?: Double.POSITIVE_INFINITY }
    3 -> compareByDescending<Row2> { shownPrice(it.second) ?: -1.0 }
    4 -> compareBy<Row2> { parseName(it.first.name).skin.lowercase() }
    else -> compareBy<Row2>({ signalOrder(it.second.signal) }, { it.first.name })
}

@Composable
fun HomeScreen(
    st: Stored,
    refreshing: Boolean,
    snackbar: SnackbarHostState,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    onPlatforms: () -> Unit,
    onTrades: () -> Unit
) {
    val rows: List<Row2> = remember(st) {
        val now = System.currentTimeMillis()
        st.items.map { it to analyze(it, st.settings, now) }
    }
    val sellN = rows.count { it.second.signal == Signal.SELL }
    val buyN = rows.count { it.second.signal == Signal.BUY }
    val flipN = rows.count { it.second.signal == Signal.FLIP }

    var filter by rememberSaveable { mutableStateOf(0) }
    var sort by rememberSaveable { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    val effFilter = when {
        filter == 1 && sellN == 0 -> 0
        filter == 2 && buyN == 0 -> 0
        filter == 3 && flipN == 0 -> 0
        else -> filter
    }

    val shown: List<Row2> = remember(rows, effFilter, sort) {
        val want = when (effFilter) {
            1 -> Signal.SELL
            2 -> Signal.BUY
            3 -> Signal.FLIP
            else -> null
        }
        val f = if (want == null) rows else rows.filter { it.second.signal == want }
        f.sortedWith(comparatorFor(sort))
    }
    val last = st.items.maxOfOrNull { it.updatedAt } ?: 0L

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Скин-трекер", fontWeight = FontWeight.Bold)
                        Text(
                            if (last == 0L) "ещё не обновлялось" else "обновлено ${ago(last)}",
                            fontSize = 12.sp, color = C.muted
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.bg)
            )
        },
        floatingActionButton = {
            if (rows.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Добавить скин") },
                    containerColor = C.accent,
                    contentColor = C.bg
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        if (rows.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Список пуст", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Добавь скины, за ценой которых хочешь следить. Приложение само проверит цены и подскажет, когда лучше купить или продать.",
                    color = C.muted, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onAdd,
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent, contentColor = C.bg)
                ) { Text("Добавить первый скин") }
                Spacer(Modifier.height(12.dp))
                TagChip("Площадки: ${st.settings.platforms.size}") { onPlatforms() }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val held = rows.filter { it.first.buyPrice != null && (it.second.marketNow ?: it.second.price) != null }
                if (held.isNotEmpty()) {
                    item { PortfolioCard(held, st.settings.fee(Platforms.SKINPORT)) }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (sellN + buyN + flipN > 0) {
                            TagChip("Все ${rows.size}", effFilter == 0) { filter = 0 }
                            if (sellN > 0) TagChip("Продавать $sellN", effFilter == 1) { filter = 1 }
                            if (buyN > 0) TagChip("Покупать $buyN", effFilter == 2) { filter = 2 }
                            if (flipN > 0) TagChip("Перекуп $flipN", effFilter == 3) { filter = 3 }
                        }
                        TagChip("Площадки: ${st.settings.platforms.size}") { onPlatforms() }
                        TagChip("Мои сделки" + (if (st.trades.isNotEmpty()) " ${st.trades.size}" else "")) { onTrades() }
                        Box {
                            TagChip("${SORT_SHORT[sort]} ▾") { menu = true }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                SORTS.forEachIndexed { i, s ->
                                    DropdownMenuItem(
                                        text = { Text(s) },
                                        onClick = {
                                            sort = i
                                            menu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                items(shown, key = { it.first.name }) { (t, a) ->
                    SkinCard(t, a) { onOpen(t.name) }
                }
            }
        }
    }
}

@Composable
private fun PortfolioCard(held: List<Row2>, feePct: Int) {
    val fee = feePct / 100.0
    val invested = held.sumOf { it.first.buyPrice ?: 0.0 }
    // сколько получишь «на руки», если продать сейчас
    val value = held.sumOf { (it.second.marketNow ?: it.second.price ?: 0.0) * (1 - fee) }
    val pl = value - invested
    val plPct = if (invested > 0) pl / invested * 100.0 else 0.0
    val col = if (pl >= 0) C.up else C.down
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            if (pl >= 0) "Мои покупки: можно заработать" else "Мои покупки: пока в минусе",
            color = C.muted, fontSize = 12.sp
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(fmtSignedMoney(pl), color = col, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                (if (pl >= 0) "+" else "−") + fmtAbsPct(plPct),
                color = col, fontSize = 15.sp, fontWeight = FontWeight.Bold
            )
        }
        RubLine(pl, signed = true, color = col)
        Text(
            "Ты вложил ${fmtMoney(invested)}. Если продать всё сейчас, после комиссии получишь около ${fmtMoney(value)}.",
            color = C.muted, fontSize = 12.sp
        )
    }
}

@Composable
private fun SkinCard(t: Tracked, a: Analysis, onClick: () -> Unit) {
    val n = parseName(t.name)
    val strong = a.signal == Signal.SELL || a.signal == Signal.BUY || a.signal == Signal.FLIP
    val shape = RoundedCornerShape(20.dp)
    val price = shownPrice(a)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(C.card)
            .then(if (strong) Modifier.border(1.dp, signalColor(a.signal).copy(alpha = 0.55f), shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkinImage(t.icon, 80.dp, 16.dp)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (n.top.isNotEmpty()) {
                Text(n.top, color = C.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                n.skin, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (n.wear != null) Text(n.wear, color = C.muted, fontSize = 12.sp)
            Text(
                a.short,
                color = if (strong) signalColor(a.signal) else C.muted,
                fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.size(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(fmtMoney(price), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            RubLine(price)
            if (a.bestBuy != null && a.platforms.size > 1) {
                Text("дешевле на ${a.bestBuy.title}", color = C.muted, fontSize = 11.sp)
            }
            if (a.price != null) ChangeText(a.changes.getOrNull(0))
            if (strong) {
                Spacer(Modifier.height(4.dp))
                SignalBadge(a.signal)
            }
        }
    }
}
