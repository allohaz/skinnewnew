@file:OptIn(ExperimentalMaterial3Api::class)

package com.skintracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skintracker.app.data.Trade
import com.skintracker.app.data.fmtMoney
import com.skintracker.app.data.fmtPct
import com.skintracker.app.data.fmtSignedMoney
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun SumCell(label: String, trades: List<Trade>, modifier: Modifier = Modifier) {
    val profit = trades.sumOf { it.got - it.buy }
    val col = if (profit >= 0) C.up else C.down
    Column(modifier) {
        Text(label, color = C.muted, fontSize = 12.sp)
        Text(fmtSignedMoney(profit), color = col, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        RubLine(profit, signed = true, color = col)
        Text("сделок: ${trades.size}", color = C.muted, fontSize = 11.sp)
    }
}

@Composable
fun TradesScreen(trades: List<Trade>, onDelete: (Long) -> Unit, onBack: () -> Unit) {
    val now = System.currentTimeMillis()
    val day = 24L * 3_600_000L
    val week = trades.filter { it.at >= now - 7 * day }
    val month = trades.filter { it.at >= now - 30 * day }
    val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Мои сделки", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.bg)
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.card).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SumCell("За неделю", week)
                    SumCell("За месяц", month)
                    SumCell("Всего", trades)
                }
            }
            if (trades.isEmpty()) {
                item {
                    Text(
                        "Пока пусто. Когда продашь скин, открой его и нажми «Я продал». Для этого у скина должна быть указана цена покупки.",
                        color = C.muted
                    )
                }
            }
            items(trades.sortedByDescending { it.at }, key = { it.at.toString() + it.name }) { x ->
                val profit = x.got - x.buy
                val pct = if (x.buy > 0) profit / x.buy * 100.0 else 0.0
                val col = if (profit >= 0) C.up else C.down
                val n = parseName(x.name)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (n.top.isNotEmpty()) Text(n.top, color = C.muted, fontSize = 12.sp)
                            Text(n.skin + (if (n.wear != null) " · " + wearShort(n.wear) else ""), fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(fmtSignedMoney(profit) + " (" + fmtPct(pct) + ")", color = col, fontWeight = FontWeight.Bold)
                            RubLine(profit, signed = true, color = col)
                        }
                    }
                    Text(
                        "Купил за ${fmtMoney(x.buy)}, получил ${fmtMoney(x.got)} · ${fmt.format(Date(x.at))}",
                        color = C.muted, fontSize = 12.sp
                    )
                    TextButton(onClick = { onDelete(x.at) }) { Text("Удалить запись", color = C.muted, fontSize = 12.sp) }
                }
            }
        }
    }
}
