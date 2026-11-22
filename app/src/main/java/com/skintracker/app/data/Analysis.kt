package com.skintracker.app.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Signal { SELL, BUY, FLIP, HOLD, NO_DATA }

/**
 * Изменение цены за период.
 * approx = true — приблизительно: сравнение со средней ценой продаж, пока своей истории нет.
 */
data class Change(val label: String, val abs: Double?, val pct: Double?, val approx: Boolean)

/** Цена на одной площадке: ask — за сколько можно купить, net — сколько останется после продажи (за вычетом комиссии). */
data class PlatPrice(val id: String, val title: String, val ask: Double, val qty: Int, val net: Double)

data class Analysis(
    /** Самое дешёвое предложение на Skinport (по нему считается история). */
    val price: Double?,
    val signal: Signal,
    /** Одна строка: что делать. */
    val headline: String,
    /** Совсем коротко — для карточки в списке. */
    val short: String,
    /** Объяснение простыми словами. */
    val reasons: List<String>,
    /** Обычная цена скина. */
    val fair: Double?,
    /** Цена последних продаж, если их достаточно. */
    val marketNow: Double?,
    val devPct: Double?,
    /** Сколько можно заработать, если купить по текущему предложению и продать по обычной цене (после комиссии), %. */
    val netPct: Double?,
    /** 0 — данных мало, 1 — достаточно, 2 — много. */
    val confidence: Int,
    val liquidity: String?,
    val changes: List<Change>,
    /** Результат относительно цены покупки пользователя (после комиссии). */
    val profitAbs: Double?,
    val profitPct: Double?,
    val platforms: List<PlatPrice> = emptyList(),
    val bestBuy: PlatPrice? = null,
    val bestSell: PlatPrice? = null,
    val flipPct: Double? = null
)

private const val HOUR = 3_600_000L
private const val DAY = 24 * HOUR

private fun priceAt(snaps: List<Snap>, target: Long, tol: Long): Double? {
    var best: Snap? = null
    var bestD = Long.MAX_VALUE
    for (s in snaps) {
        val d = abs(s.t - target)
        if (d < bestD) {
            bestD = d
            best = s
        }
    }
    return if (best != null && bestD <= tol) best.p else null
}

private fun tolFor(period: Long): Long = (period / 5).coerceIn(6 * HOUR, 3 * DAY)

fun fmtMoney(v: Double?): String =
    if (v == null) "—" else String.format(Locale.US, "\$%,.2f", v)

fun fmtSignedMoney(v: Double): String =
    String.format(Locale.US, "%s\$%,.2f", if (v >= 0) "+" else "−", abs(v))

fun fmtPct(v: Double): String =
    String.format(Locale.US, "%s%.1f%%", if (v >= 0) "+" else "−", abs(v))

/** Процент без знака: «12%» или «7.5%». */
fun fmtAbsPct(v: Double): String =
    if (abs(v) >= 10) String.format(Locale.US, "%.0f%%", abs(v)) else String.format(Locale.US, "%.1f%%", abs(v))

private fun mid(s: Stat?): Double? = if (s == null) null else (s.median ?: s.avg)

private fun refOf(s: Stat?, minVolume: Int): Double? =
    if (s != null && s.volume >= minVolume) mid(s) else null

private fun liquidityText(v30: Int, qty: Int): String? {
    if (v30 <= 0) return null
    val perDay = v30 / 30.0
    val speed = when {
        v30 >= 90 -> "Продаётся быстро"
        v30 >= 30 -> "Продаётся нормально"
        else -> "Продаётся медленно"
    }
    val rate = if (perDay >= 1.0) "примерно ${perDay.roundToInt()} шт. в день" else "примерно $v30 шт. в месяц"
    val queue = if (qty > 0) {
        val days = qty / perDay
        if (days < 1.0) "" else " Перед тобой $qty предложений — продажа может занять около ${ceil(days).toInt()} дн."
    } else ""
    return "$speed: $rate.$queue"
}

fun confidenceText(c: Int): String = when (c) {
    2 -> "данных много"
    1 -> "данных достаточно"
    else -> "данных мало"
}

fun analyze(t: Tracked, s: Settings, now: Long): Analysis {
    val core = core(t, s, now)
    val withPlat = withPlatforms(core, t, s)
    return addWarnings(applyTargets(withPlat, t), t)
}

/** Предупреждения для тех, кто собирается покупать: простые и короткие. */
private fun addWarnings(a: Analysis, t: Tracked): Analysis {
    if (a.signal != Signal.BUY && a.signal != Signal.FLIP) return a
    val w = ArrayList<String>()
    w.add("Часто купленный скин нельзя продать сразу — бывает ждать до 7 дней. За это время цена может измениться.")
    val p = a.bestBuy?.ask ?: a.price
    if (p != null && p >= 50.0) {
        w.add("У дорогих скинов цена зависит от «флоата» (точного состояния), наклеек и узора. Перед покупкой проверь именно этот лот.")
    }
    val v30 = t.sales?.d30?.volume ?: 0
    if (v30 in 1..29) w.add("Скин продаётся медленно: быстро продать его может не получиться.")
    return a.copy(reasons = a.reasons + w)
}

private fun applyTargets(a: Analysis, t: Tracked): Analysis {
    val ask = a.bestBuy?.ask ?: a.price ?: return a
    val ts = t.targetSell
    val tb = t.targetBuy
    return when {
        ts != null && ask >= ts -> a.copy(
            signal = Signal.SELL,
            headline = "Цена дошла до твоей цели продажи (${fmtMoney(ts)})",
            short = "дошла до цели продажи ${fmtMoney(ts)}",
            reasons = listOf("Самое дешёвое предложение сейчас ${fmtMoney(ask)} — это не ниже твоей цели ${fmtMoney(ts)}.") + a.reasons
        )
        tb != null && ask <= tb -> a.copy(
            signal = Signal.BUY,
            headline = "Цена упала до твоей цели покупки (${fmtMoney(tb)})",
            short = "упала до цели покупки ${fmtMoney(tb)}",
            reasons = listOf("Самое дешёвое предложение сейчас ${fmtMoney(ask)} — это не выше твоей цели ${fmtMoney(tb)}.") + a.reasons
        )
        else -> a
    }
}

/** Цены на выбранных площадках и возможность перекупа между ними. */
private fun withPlatforms(a: Analysis, t: Tracked, s: Settings): Analysis {
    val list = ArrayList<PlatPrice>()
    for (p in Platforms.all) {
        if (p.id !in s.platforms) continue
        val q = t.quotes[p.id] ?: continue
        val price = q.price ?: continue
        list.add(PlatPrice(p.id, p.title, price, q.qty, price * (1 - s.fee(p.id) / 100.0)))
    }
    if (list.isEmpty()) return a
    val buy = list.minByOrNull { it.ask } ?: return a
    val sell = list.maxByOrNull { it.net } ?: return a
    var res = a.copy(platforms = list.sortedBy { it.ask }, bestBuy = buy, bestSell = sell)

    if (buy.id != sell.id && buy.ask > 0.0) {
        val profit = sell.net - buy.ask
        val pct = profit / buy.ask * 100.0
        res = res.copy(flipPct = pct)
        val fair = a.fair
        val sane = fair == null || (buy.ask >= fair * 0.5 && sell.ask <= fair * 1.6)
        if (pct >= s.profitPct && sane && (a.signal == Signal.HOLD || a.signal == Signal.NO_DATA)) {
            res = res.copy(
                signal = Signal.FLIP,
                headline = "Можно перекупить: купить на ${buy.title}, продать на ${sell.title} (+${fmtAbsPct(pct)})",
                short = "купить на ${buy.title} → продать на ${sell.title}: +${fmtAbsPct(pct)}",
                reasons = listOf(
                    "Дешевле всего скин сейчас на ${buy.title}: ${fmtMoney(buy.ask)}.",
                    "Дороже всего его можно продать на ${sell.title}: по ${fmtMoney(sell.ask)}. " +
                        "После комиссии (${s.fee(sell.id)}%) на руки останется ${fmtMoney(sell.net)}.",
                    "Заработок с одного скина: ${fmtMoney(profit)} (+${fmtAbsPct(pct)}).",
                    "Помни: скин и деньги придётся переносить между площадками — это время, и цена может измениться."
                ) + a.reasons
            )
        }
    }
    return res
}

private fun core(t: Tracked, s: Settings, now: Long): Analysis {
    val ask = t.market?.minPrice
    val qty = t.market?.quantity ?: 0
    val sl = t.sales
    val d1 = sl?.d1
    val d7 = sl?.d7
    val d30 = sl?.d30
    val d90 = sl?.d90
    val v1 = d1?.volume ?: 0
    val v7 = d7?.volume ?: 0
    val v30 = d30?.volume ?: 0
    val v90 = d90?.volume ?: 0
    val feePct = s.fee(Platforms.SKINPORT)
    val fee = feePct / 100.0
    val liq = liquidityText(v30, qty)

    // ───── как менялась цена ─────
    val changes = ArrayList<Change>()
    if (ask != null) {
        fun make(label: String, ref: Double?, approx: Boolean): Change =
            if (ref == null || ref <= 0.0) Change(label, null, null, false)
            else Change(label, ask - ref, (ask - ref) / ref * 100.0, approx)

        fun period(label: String, ago: Long, ref: Double?): Change {
            val snap = priceAt(t.snaps, now - ago, tolFor(ago))
            return if (snap != null) make(label, snap, false) else make(label, ref, true)
        }

        changes.add(period("Сегодня", DAY, refOf(d1, 3)))
        val p24 = priceAt(t.snaps, now - DAY, tolFor(DAY))
        val p48 = priceAt(t.snaps, now - 2 * DAY, tolFor(DAY))
        changes.add(
            if (p24 != null && p48 != null && p48 > 0) Change("Вчера", p24 - p48, (p24 - p48) / p48 * 100.0, false)
            else Change("Вчера", null, null, false)
        )
        changes.add(period("7 дней", 7 * DAY, refOf(d7, 5)))
        changes.add(period("30 дней", 30 * DAY, refOf(d30, 8)))
        changes.add(period("3 месяца", 90 * DAY, refOf(d90, 15)))
        changes.add(period("Год", 365 * DAY, null))
    }

    if (ask == null) {
        val why = if (t.updatedAt == 0L) "Данные ещё загружаются" else "На Skinport сейчас нет предложений"
        return Analysis(null, Signal.NO_DATA, why, why, emptyList(), null, null, null, null, 0, liq, changes, null, null)
    }

    // ───── обычная цена: берём «серединное» значение продаж, а не среднее — одна странная сделка его не двигает ─────
    val med30 = if (d30 != null && v30 >= 8) mid(d30) else null
    val med90 = if (d90 != null && v90 >= 15) mid(d90) else null
    val fair: Double? = when {
        med30 != null && med90 != null -> 0.6 * med30 + 0.4 * med90
        med30 != null -> med30
        med90 != null -> med90
        else -> null
    }

    // ───── цена последних продаж: только если продаж достаточно ─────
    val useDay = d1 != null && v1 >= 4
    val recent: Double? = when {
        useDay -> mid(d1)
        d7 != null && v7 >= 5 -> mid(d7)
        else -> null
    }
    val recentLabel = if (useDay) "за последние сутки" else "за последнюю неделю"
    val recentVol = if (useDay) v1 else v7
    val marketNow: Double = recent ?: ask

    val conf = when {
        v30 >= 60 -> 2
        v30 >= 20 -> 1
        else -> 0
    }

    // ───── результат относительно цены покупки пользователя ─────
    val bp = t.buyPrice
    val profitAbs: Double? = if (bp != null) marketNow * (1 - fee) - bp else null
    val profitPct: Double? = if (profitAbs != null && bp != null && bp > 0) profitAbs / bp * 100.0 else null

    if (fair == null) {
        return Analysis(
            ask, Signal.NO_DATA, "Мало продаж — совета нет", "мало продаж — совета нет",
            listOf(
                "За месяц продано всего $v30 шт. Такой скин сложно оценить: одна сделка сильно двигает цену.",
                "Смотри на цены на площадках и решай сам."
            ),
            null, recent, null, null, 0, liq, changes, profitAbs, profitPct
        )
    }

    val dev = (marketNow - fair) / fair * 100.0
    val askDev = (ask - fair) / fair * 100.0
    val netPct = (fair * (1 - fee) - ask) / ask * 100.0

    // Чем сильнее цена скачет, тем больше нужен сдвиг для совета «продавать».
    val dMax = d30?.max
    val dMin = d30?.min
    val rangeRel = if (dMax != null && dMin != null && med30 != null && med30 > 0) (dMax - dMin) / med30 else 0.0
    val sellThr = max(s.sellPct.toDouble(), min(rangeRel * 15.0, 15.0))

    val m1 = mid(d1)
    val m7 = mid(d7)
    val fallingFast = m1 != null && m7 != null && v1 >= 4 && v7 >= 8 && m1 < m7 * 0.94
    val risingFast = m1 != null && m7 != null && v1 >= 4 && v7 >= 8 && m1 > m7 * 1.05

    val usual = fmtMoney(fair)
    val buyBelow = fair * (1 - fee) / (1 + s.profitPct / 100.0)
    val sellAbove = fair * (1 + sellThr / 100.0)
    val levels = "Купить выгодно: ${fmtMoney(buyBelow)} и дешевле. Продать выгодно: ${fmtMoney(sellAbove)} и дороже."
    val weakData = if (conf == 0) "Данных мало — относись к совету осторожно." else null

    val sellOk = dev >= sellThr && (recent == null || askDev >= sellThr / 2)
    val buyOk = netPct >= s.profitPct

    fun result(sig: Signal, head: String, short: String, reasons: List<String>) = Analysis(
        ask, sig, head, short, reasons, fair, recent, dev, netPct, conf, liq, changes, profitAbs, profitPct
    )

    val myLine: String? = if (profitAbs != null && bp != null) {
        if (profitAbs >= 0) "Ты купил за ${fmtMoney(bp)}: если продать сейчас, заработаешь ${fmtMoney(profitAbs)} (${fmtAbsPct(profitPct ?: 0.0)})."
        else "Ты купил за ${fmtMoney(bp)}: если продать сейчас, выйдет в минус на ${fmtMoney(abs(profitAbs))}."
    } else null

    // ───── ПРОДАВАТЬ ─────
    if (sellOk) {
        val reasons = ArrayList<String>()
        reasons.add("Обычно скин стоит около $usual. Сейчас его покупают по ${fmtMoney(marketNow)} — на ${fmtAbsPct(dev)} дороже.")
        reasons.add(
            if (recent != null) "Это не случайность: $recentLabel продано $recentVol шт. по такой цене, дешёвых предложений почти не осталось."
            else "Самое дешёвое предложение уже ${fmtMoney(ask)} — тоже дороже обычного."
        )
        reasons.add("Если продать по ${fmtMoney(marketNow)}, на руки получишь ${fmtMoney(marketNow * (1 - fee))} (сайт заберёт $feePct%).")
        if (myLine != null) reasons.add(myLine)
        if (risingFast) reasons.add("За сутки цена ещё растёт: можно подождать, но она может и вернуться назад.")
        if (liq != null) reasons.add(liq)
        if (weakData != null) reasons.add(weakData)
        return result(
            Signal.SELL,
            "Сейчас дорого — хорошо продавать",
            "дорого: на ${fmtAbsPct(dev)} выше обычной цены",
            reasons
        )
    }

    // ───── ПОКУПАТЬ ─────
    if (buyOk) {
        val immediate = marketNow >= fair * 0.97
        if (fallingFast) {
            return result(
                Signal.HOLD,
                "Дёшево, но цена ещё падает — лучше подождать",
                "дёшево, но продолжает падать",
                listOf(
                    "Самое дешёвое предложение — ${fmtMoney(ask)}, а обычно скин стоит около $usual.",
                    "За последние сутки цена падает быстрее, чем раньше. Падение ещё не остановилось, покупать сейчас рискованно.",
                    levels
                )
            )
        }
        if (!immediate && conf == 0) {
            return result(
                Signal.HOLD,
                "Дёшево, но данных мало — лучше не рисковать",
                "дёшево, но данных мало",
                listOf(
                    "Самое дешёвое предложение — ${fmtMoney(ask)}, обычно скин стоит около $usual.",
                    "Продаж мало, поэтому неясно, вернётся ли цена к обычной.",
                    levels
                )
            )
        }
        val reasons = ArrayList<String>()
        reasons.add("Самое дешёвое предложение — ${fmtMoney(ask)}, а обычно скин стоит около $usual (дешевле на ${fmtAbsPct(askDev)}).")
        reasons.add(
            "Купишь за ${fmtMoney(ask)}, продашь за $usual — после комиссии $feePct% останется ${fmtMoney(fair * (1 - fee))}. " +
                "Заработок: ${fmtMoney(fair * (1 - fee) - ask)} с одного скина (+${fmtAbsPct(netPct)})."
        )
        reasons.add(
            if (immediate) "Последние продажи идут по обычной цене, так что перепродать будет реально."
            else "Сейчас весь рынок дешевле обычного. Цена может вернуться, но это не гарантировано."
        )
        if (askDev <= -40) reasons.add("Скидка очень большая — проверь скин: наклейки, редкий узор или ошибка в названии.")
        if (liq != null) reasons.add(liq)
        if (weakData != null) reasons.add(weakData)
        return result(
            Signal.BUY,
            "Сейчас дёшево — хорошо покупать",
            "дёшево: на ${fmtAbsPct(askDev)} ниже обычной цены, заработок +${fmtAbsPct(netPct)}",
            reasons
        )
    }

    // ───── ЖДАТЬ ─────
    val head: String
    val short: String
    val extra: String
    when {
        dev >= sellThr * 0.5 -> {
            head = "Чуть дороже обычного, но продавать рано"
            short = "чуть дороже обычного — продавать рано"
            extra = "Цена выше обычной на ${fmtAbsPct(dev)}, этого пока мало: лучше подождать, вдруг вырастет ещё."
        }
        netPct > 0 -> {
            head = "Дешевле обычного, но заработка почти нет"
            short = "дешевле обычного, но заработок мал (+${fmtAbsPct(netPct)})"
            extra = "Если купить сейчас и продать по обычной цене, после комиссии $feePct% останется всего +${fmtAbsPct(netPct)}. Ты просишь не меньше ${s.profitPct}%."
        }
        else -> {
            head = "Пока ничего не делай — цена обычная"
            short = "цена обычная"
            extra = "Обычная цена около $usual, сейчас ${fmtMoney(marketNow)}. Покупать выгодно только со скидкой, которая покрывает комиссию $feePct%."
        }
    }
    val reasons = ArrayList<String>()
    reasons.add(extra)
    reasons.add(levels)
    if (myLine != null) reasons.add(myLine)
    if (liq != null) reasons.add(liq)
    if (weakData != null) reasons.add(weakData)
    return result(Signal.HOLD, head, short, reasons)
}
