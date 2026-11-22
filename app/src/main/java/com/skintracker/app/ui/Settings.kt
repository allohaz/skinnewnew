@file:OptIn(ExperimentalMaterial3Api::class)

package com.skintracker.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skintracker.app.data.Platforms
import com.skintracker.app.data.Settings
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(s: Settings, onDismiss: () -> Unit, onSave: (Settings) -> Unit) {
    var sell by remember { mutableFloatStateOf(s.sellPct.toFloat()) }
    var profit by remember { mutableFloatStateOf(s.profitPct.toFloat()) }
    var notify by remember { mutableStateOf(s.notify) }
    var quiet by remember { mutableStateOf(s.quiet) }
    var theme by remember { mutableStateOf(s.theme) }
    var repo by remember { mutableStateOf(s.historyRepo) }
    var showRub by remember { mutableStateOf(s.showRub) }
    var hours by remember { mutableStateOf(s.everyHours) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.card,
        title = { Text("Настройки") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Сообщать «покупай», если заработок от ${profit.roundToInt()}%")
                Slider(value = profit, onValueChange = { profit = it }, valueRange = 1f..30f, steps = 28)
                Text("Это чистая прибыль, когда комиссия площадки уже вычтена.", color = C.muted, fontSize = 12.sp)

                Text("Сообщать «продавай», если цена выше обычной на ${sell.roundToInt()}%")
                Slider(value = sell, onValueChange = { sell = it }, valueRange = 3f..30f, steps = 26)
                Text(
                    "У скинов, цена которых сильно скачет, приложение само ждёт чуть больше.",
                    color = C.muted, fontSize = 12.sp
                )

                Text("Тема:")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Как в телефоне", "Тёмная", "Светлая").forEachIndexed { i, l ->
                        TagChip(l, theme == i) { theme = i }
                    }
                }

                Text("Проверять цены:")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 6, 12, 24).forEach { h ->
                        TagChip("раз в $h ч", hours == h) { hours = h }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Присылать уведомления", Modifier.weight(1f))
                    Switch(checked = notify, onCheckedChange = { notify = it })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Не беспокоить ночью")
                        Text("с 23:00 до 8:00 уведомлений не будет", color = C.muted, fontSize = 12.sp)
                    }
                    Switch(checked = quiet, onCheckedChange = { quiet = it })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Показывать рубли")
                        Text(
                            if (s.rubRate > 0) "по курсу ЦБ: ${String.format(java.util.Locale.US, "%.2f", s.rubRate)} ₽ за \$" else "курс ещё не загружен",
                            color = C.muted, fontSize = 12.sp
                        )
                    }
                    Switch(checked = showRub, onCheckedChange = { showRub = it })
                }
                Text(
                    "Рубли примерные. Реальный курс при выводе денег может отличаться.",
                    color = C.muted, fontSize = 12.sp
                )

                Text("История цен с GitHub", fontWeight = FontWeight.Bold)
                Text(
                    "Если настроил сбор цен на GitHub (см. README), впиши репозиторий в виде «логин/название». Тогда на графиках будет настоящая история с первого дня. Репозиторий должен быть открытым (public).",
                    color = C.muted, fontSize = 12.sp
                )
                OutlinedTextField(
                    value = repo, onValueChange = { repo = it.trim() }, singleLine = true,
                    label = { Text("Например: allohaz/skin-tracker") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    s.copy(
                        sellPct = sell.roundToInt(),
                        profitPct = profit.roundToInt(),
                        notify = notify,
                        quiet = quiet,
                        theme = theme,
                        historyRepo = repo.trim().trim('/'),
                        showRub = showRub,
                        everyHours = hours
                    )
                )
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** Выбор площадок: включить/выключить и поправить комиссию. */
@Composable
fun PlatformsDialog(s: Settings, onDismiss: () -> Unit, onSave: (Settings) -> Unit) {
    var on by remember { mutableStateOf(s.platforms) }
    var fees by remember {
        mutableStateOf(Platforms.all.associate { it.id to s.fee(it.id).toString() })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.card,
        title = { Text("Площадки") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Выбери, где искать цены. Я покажу, где скин дешевле купить и где выгоднее продать.",
                    color = C.muted, fontSize = 13.sp
                )
                Platforms.all.forEach { p ->
                    val enabled = p.id in on
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.title, fontWeight = FontWeight.Bold)
                                Text(p.note, color = C.muted, fontSize = 12.sp)
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { v ->
                                    // хотя бы одна площадка должна остаться включённой
                                    val next = if (v) on + p.id else on - p.id
                                    if (next.isNotEmpty()) on = next
                                }
                            )
                        }
                        if (enabled) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Комиссия при продаже, %", color = C.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                OutlinedTextField(
                                    value = fees[p.id] ?: "",
                                    onValueChange = { v -> fees = fees + (p.id to v.filter { it.isDigit() }.take(2)) },
                                    singleLine = true,
                                    modifier = Modifier.width(76.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                        }
                    }
                }
                Text(
                    "Комиссии примерные — у площадок они меняются. Если знаешь точную, впиши её.",
                    color = C.muted, fontSize = 12.sp
                )
                Text(
                    "Совет «покупать/продавать» считается по Skinport: там открытая история продаж.",
                    color = C.muted, fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val newFees = HashMap<String, Int>()
                Platforms.all.forEach { p ->
                    val v = fees[p.id]?.toIntOrNull()
                    if (v != null && v in 0..50 && v != p.defaultFee) newFees[p.id] = v
                }
                onSave(s.copy(platforms = on, fees = newFees))
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
