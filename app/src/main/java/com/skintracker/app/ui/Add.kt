@file:OptIn(ExperimentalMaterial3Api::class)

package com.skintracker.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skintracker.app.data.Catalogs
import com.skintracker.app.data.Found
import com.skintracker.app.data.Sales
import com.skintracker.app.data.Settings
import com.skintracker.app.data.Tracked
import com.skintracker.app.data.Weapons
import com.skintracker.app.data.analyze
import com.skintracker.app.data.confidenceText
import com.skintracker.app.data.fmtMoney
import com.skintracker.app.data.norm

private const val PAGE_GROUPS = 30

/** Один скин со всеми износами: в списке показывается одной строкой. */
data class SkinGroup(val base: String, val names: List<String>)

private fun groupNames(list: List<String>): List<SkinGroup> {
    val map = LinkedHashMap<String, MutableList<String>>()
    for (n in list) map.getOrPut(baseOf(n)) { mutableListOf() }.add(n)
    return map.map { (b, ns) -> SkinGroup(b, ns.sortedBy { wearOrder(it) }) }
}

@Composable
fun AddScreen(
    tracked: Set<String>,
    settings: Settings,
    results: List<String>,
    icons: Map<String, String>,
    searching: Boolean,
    doneFor: String,
    catalogs: Catalogs,
    previewSales: Map<String, Sales>,
    previewLoading: Boolean,
    snackbar: SnackbarHostState,
    onQuery: (String) -> Unit,
    onMoreIcons: () -> Unit,
    onWarm: () -> Unit,
    onNeedSales: (List<String>) -> Unit,
    onAdd: (Found) -> Unit,
    onBack: () -> Unit
) {
    var q by rememberSaveable { mutableStateOf("") }
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var visible by rememberSaveable { mutableStateOf(PAGE_GROUPS) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        onWarm()
        onQuery(q)
        try {
            focus.requestFocus()
        } catch (e: Exception) {
        }
    }

    val groups = remember(results) { groupNames(results) }
    val suggestions = remember(q) {
        val s = Weapons.suggest(q)
        // не показываем подсказку, если она в точности совпадает с тем, что уже введено
        if (s.size == 1 && norm(s[0].name) == norm(q)) emptyList() else s
    }
    val ambiguous = q.isNotBlank() && Weapons.resolve(q) == null
    val trimmed = q.trim()
    val loadingBase = catalogs.isEmpty() && searching

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Добавить скин", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.bg)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = q,
                    onValueChange = {
                        q = it
                        visible = PAGE_GROUPS
                        onQuery(it)
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("Начни вводить: АК, 47, AWP, redline…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (q.isNotEmpty()) {
                            IconButton(onClick = {
                                q = ""
                                visible = PAGE_GROUPS
                                onQuery("")
                            }) { Icon(Icons.Default.Close, contentDescription = "Очистить") }
                        }
                    }
                )
            }

            if (trimmed.isEmpty()) {
                item {
                    Text(
                        "Выбери оружие или начни вводить — подсказки появятся сразу.",
                        color = C.muted, fontSize = 13.sp
                    )
                }
                Weapons.categories.forEach { cat ->
                    item(key = "cat-$cat") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(cat, color = C.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(Weapons.inCategory(cat), key = { it.name }) { w ->
                                    TagChip(w.name) {
                                        q = w.name
                                        visible = PAGE_GROUPS
                                        onQuery(w.name)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (suggestions.isNotEmpty()) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(suggestions, key = { it.name }) { w ->
                                TagChip(w.name) {
                                    q = w.name
                                    visible = PAGE_GROUPS
                                    onQuery(w.name)
                                }
                            }
                        }
                    }
                }
                when {
                    ambiguous -> item {
                        Text("Выбери оружие из подсказок выше — так найдётся точнее.", color = C.muted)
                    }
                    searching && groups.isEmpty() -> item {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                            if (loadingBase) Text("Загружаю список скинов…", color = C.muted, fontSize = 13.sp)
                        }
                    }
                    groups.isEmpty() && doneFor == trimmed -> item {
                        Column(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Ничего не найдено", fontWeight = FontWeight.Bold)
                            Text(
                                "Попробуй написать иначе. Названия скинов лучше вводить по-английски (например, Redline), а оружие можно и по-русски.",
                                color = C.muted, fontSize = 13.sp, textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                if (groups.isNotEmpty()) {
                    item {
                        Text(
                            "Найдено скинов: ${groups.size}",
                            color = C.muted, fontSize = 12.sp
                        )
                    }
                }
                items(groups.take(visible), key = { it.base }) { g ->
                    GroupRow(g, icons[g.base], catalogs, tracked) { openGroup = g.base }
                }
                if (groups.size > visible) {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = {
                                visible += PAGE_GROUPS
                                onMoreIcons()
                            }) { Text("Показать ещё (${groups.size - visible})") }
                        }
                    }
                }
            }
        }
    }

    val open = groups.firstOrNull { it.base == openGroup }
    if (open != null) {
        PreviewSheet(
            g = open,
            icon = icons[open.base],
            settings = settings,
            catalogs = catalogs,
            sales = previewSales,
            loading = previewLoading,
            tracked = tracked,
            onNeedSales = onNeedSales,
            onAdd = onAdd,
            onDismiss = { openGroup = null }
        )
    }
}

@Composable
private fun GroupRow(g: SkinGroup, icon: String?, catalogs: Catalogs, tracked: Set<String>, onClick: () -> Unit) {
    val n = parseName(g.names.first())
    val from = g.names.mapNotNull { catalogs.price(it) }.minOrNull()
    val inList = g.names.any { it in tracked }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.card)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkinImage(icon, 64.dp, 12.dp)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            if (n.top.isNotEmpty()) Text(n.top, color = C.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(n.skin, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (g.names.size > 1) Text("износов: ${g.names.size}", color = C.muted, fontSize = 12.sp)
            else if (n.wear != null) Text(n.wear, color = C.muted, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (from != null) {
                Text(if (g.names.size > 1) "от ${fmtMoney(from)}" else fmtMoney(from), fontWeight = FontWeight.Bold)
                RubLine(from)
            }
            if (inList) Icon(Icons.Default.Check, contentDescription = "В списке", tint = C.up, modifier = Modifier.size(18.dp))
        }
    }
}

/** Просмотр скина до добавления: цена на площадках, износы и простой вывод. */
@Composable
private fun PreviewSheet(
    g: SkinGroup,
    icon: String?,
    settings: Settings,
    catalogs: Catalogs,
    sales: Map<String, Sales>,
    loading: Boolean,
    tracked: Set<String>,
    onNeedSales: (List<String>) -> Unit,
    onAdd: (Found) -> Unit,
    onDismiss: () -> Unit
) {
    val names = g.names
    var sel by rememberSaveable(g.base) { mutableStateOf<String?>(null) }
    val cur = sel?.takeIf { it in names }
        ?: names.firstOrNull { wearOf(it) == "Field-Tested" }
        ?: names.first()

    LaunchedEffect(g.base) { onNeedSales(names) }

    val now = System.currentTimeMillis()
    val m = catalogs.skinport[cur]
    val sl = sales[cur]
    val temp = Tracked(
        name = cur, icon = icon, buyPrice = null, addedAt = now, market = m, sales = sl,
        updatedAt = if (catalogs.isEmpty()) 0L else now, lastSignal = null, snaps = emptyList(),
        quotes = catalogs.quotes(cur)
    )
    val a = analyze(temp, settings, now)
    val n = parseName(cur)
    val already = cur in tracked

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = C.bg
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SkinImage(icon, 150.dp, 24.dp)
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (n.top.isNotEmpty()) Text(n.top, color = C.muted)
                Text(n.skin, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                if (n.wear != null) Text(n.wear, color = C.muted)
            }

            if (names.size > 1) {
                Text("Износ (состояние скина):", color = C.muted, fontSize = 12.sp)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    names.forEach { nm ->
                        val p = catalogs.price(nm)
                        TagChip(wearShort(wearOf(nm)) + (if (p != null) " · " + fmtMoney(p) else ""), nm == cur) { sel = nm }
                    }
                }
            }

            // цены на площадках
            if (a.platforms.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Где сколько стоит", color = C.muted, fontSize = 12.sp)
                    a.platforms.forEach { p ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.title, Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(fmtMoney(p.ask), fontWeight = FontWeight.Bold)
                                RubLine(p.ask)
                            }
                        }
                    }
                }
            } else {
                Text(
                    if (catalogs.isEmpty()) "Загружаю цены…" else "Сейчас этот вариант никто не продаёт.",
                    color = C.muted, fontSize = 13.sp
                )
            }

            // вывод простыми словами
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SignalBadge(a.signal)
                    if (a.fair != null) Text(confidenceText(a.confidence), color = C.muted, fontSize = 12.sp)
                }
                if (sl == null && loading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Смотрю историю продаж…", color = C.muted)
                    }
                } else if (sl == null) {
                    Text("Историю продаж загрузить не вышло. Совет появится после добавления и обновления.", color = C.muted)
                } else {
                    Text(a.headline, fontWeight = FontWeight.Bold)
                    a.reasons.take(4).forEach { r ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•", color = C.muted)
                            Text(r, color = C.muted, fontSize = 13.sp)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onAdd(Found(cur, icon))
                    onDismiss()
                },
                enabled = !already,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.accent, contentColor = C.bg)
            ) {
                Text(if (already) "Уже в списке" else "Добавить в список", fontWeight = FontWeight.Bold)
            }
        }
    }
}
