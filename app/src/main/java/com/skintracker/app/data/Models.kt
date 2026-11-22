package com.skintracker.app.data

/** Статистика продаж за период (из Skinport sales/history). */
data class Stat(
    val min: Double?,
    val max: Double?,
    val avg: Double?,
    val median: Double?,
    val volume: Int
)

data class Sales(val d1: Stat, val d7: Stat, val d30: Stat, val d90: Stat)

/** Текущее состояние рынка Skinport: минимальная цена среди предложений. */
data class Market(
    val minPrice: Double?,
    val quantity: Int,
    val suggested: Double?,
    val itemPage: String?
)

/** Самая низкая цена на любой площадке и число предложений. */
data class Quote(val price: Double?, val qty: Int)

/** Снимок цены (Skinport), который приложение сохраняет само при каждой проверке. */
data class Snap(val t: Long, val p: Double)

data class Tracked(
    val name: String,
    val icon: String?,
    val buyPrice: Double?,
    val addedAt: Long,
    val market: Market?,
    val sales: Sales?,
    val updatedAt: Long,
    val lastSignal: String?,
    val snaps: List<Snap>,
    val targetSell: Double? = null,
    val targetBuy: Double? = null,
    /** Цены на площадках: id площадки → цена. */
    val quotes: Map<String, Quote> = emptyMap()
)

data class Settings(
    /** Насколько цена должна быть выше обычной, чтобы советовать продавать (%). */
    val sellPct: Int = 10,
    /** Сколько минимум нужно заработать после комиссии, чтобы советовать покупать (%). */
    val profitPct: Int = 5,
    val notify: Boolean = true,
    val everyHours: Int = 3,
    val showRub: Boolean = true,
    val rubRate: Double = 0.0,
    val rateAt: Long = 0L,
    /** Какие площадки учитывать. */
    val platforms: Set<String> = Platforms.defaultOn,
    /** Свои комиссии площадок (%), если отличаются от стандартных. */
    val fees: Map<String, Int> = emptyMap(),
    /** Не присылать уведомления ночью (с 23:00 до 8:00). */
    val quiet: Boolean = true,
    /** Тема: 0 — как в телефоне, 1 — тёмная, 2 — светлая. */
    val theme: Int = 0,
    /** Репозиторий с историей цен: «логин/название». Пусто — не используем. */
    val historyRepo: String = ""
) {
    fun fee(id: String): Int = fees[id] ?: Platforms.byId(id)?.defaultFee ?: 10
}

/** Завершённая сделка: купил за buy, на руки после продажи получил got. */
data class Trade(val name: String, val buy: Double, val got: Double, val at: Long)

data class Stored(val settings: Settings, val items: List<Tracked>, val trades: List<Trade> = emptyList())

/** Результат поиска скина (имя и картинка). */
data class Found(val name: String, val icon: String?)
