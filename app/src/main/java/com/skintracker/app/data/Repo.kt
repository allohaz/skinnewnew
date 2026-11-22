package com.skintracker.app.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Полные списки цен с площадок, где их можно скачать одним запросом (Skinport и Market.CSGO). */
class Catalogs(val skinport: Map<String, Market>, val csgo: Map<String, Quote>) {
    fun isEmpty(): Boolean = skinport.isEmpty() && csgo.isEmpty()

    fun has(name: String): Boolean = skinport.containsKey(name) || csgo.containsKey(name)

    /** Самая низкая известная цена на Skinport и Market.CSGO. */
    fun price(name: String): Double? {
        val a = skinport[name]?.minPrice
        val b = csgo[name]?.price
        return when {
            a != null && b != null -> if (a < b) a else b
            a != null -> a
            else -> b
        }
    }

    fun quotes(name: String): Map<String, Quote> {
        val m = HashMap<String, Quote>()
        skinport[name]?.let { m[Platforms.SKINPORT] = Quote(it.minPrice, it.quantity) }
        csgo[name]?.let { m[Platforms.CSGO] = it }
        return m
    }
}

class Repo(ctx: Context) {
    private val store = Store(File(ctx.filesDir, "state.json"))
    private val catalogFile = File(ctx.filesDir, "catalogs.cache")
    private val iconFile = File(ctx.filesDir, "icons.cache")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val catalogLock = Mutex()
    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<Stored> = _state.asStateFlow()

    /** Все скины, известные Skinport и Market.CSGO (обновляется не чаще раза в 5 минут). */
    private val _catalogs = MutableStateFlow(Catalogs(emptyMap(), emptyMap()))
    val catalogs: StateFlow<Catalogs> = _catalogs.asStateFlow()

    /** История продаж для скинов, на которые смотрели до добавления. */
    private val _previewSales = MutableStateFlow<Map<String, Sales>>(emptyMap())
    val previewSales: StateFlow<Map<String, Sales>> = _previewSales.asStateFlow()

    private val skin = SkinportApi(Net.client)
    private val csgo = CsgoMarketApi(Net.client)
    private val dmarket = DMarketApi(Net.client)
    private val steam = SteamApi(Net.client)
    private val rates = RateApi(Net.client)
    private val historyApi = HistoryApi(Net.client)

    /** Готовая история цен с GitHub: название скина → точки. */
    private val _history = MutableStateFlow<Map<String, List<Snap>>>(emptyMap())
    val history: StateFlow<Map<String, List<Snap>>> = _history.asStateFlow()
    private val triedShards = HashSet<String>()

    @Volatile
    private var lastRefresh = 0L

    @Volatile
    private var catalogAt = 0L

    // индекс для быстрого поиска: названия по убыванию популярности
    @Volatile
    private var index: List<String> = emptyList()

    @Volatile
    private var indexLower: List<String> = emptyList()

    init {
        // кэш на диске: список скинов появляется сразу, а свежие цены подгружаются в фоне
        scope.launch { readCatalogCache() }
    }

    private fun readCatalogCache() {
        try {
            if (!catalogFile.exists() || !_catalogs.value.isEmpty()) return
            val sp = HashMap<String, Market>(32768)
            val cs = HashMap<String, Quote>(32768)
            var at = 0L
            catalogFile.bufferedReader().useLines { lines ->
                for ((i, line) in lines.withIndex()) {
                    if (i == 0) {
                        at = line.toLongOrNull() ?: 0L
                        continue
                    }
                    val p = line.split('\t')
                    if (p.size < 4) continue
                    val price = p[2].toDoubleOrNull()
                    val qty = p[3].toIntOrNull() ?: 0
                    if (p[0] == "S") sp[p[1]] = Market(price, qty, null, null)
                    else if (p[0] == "C") cs[p[1]] = Quote(price, qty)
                }
            }
            if (sp.isEmpty() && cs.isEmpty()) return
            if (!_catalogs.value.isEmpty()) return // уже скачали свежее
            val c = Catalogs(sp, cs)
            catalogAt = at
            rebuildIndex(c)
            _catalogs.value = c
        } catch (e: Exception) {
        }
    }

    private fun writeCatalogCache(c: Catalogs, at: Long) {
        try {
            val tmp = File(catalogFile.path + ".tmp")
            tmp.bufferedWriter().use { w ->
                w.write(at.toString())
                w.newLine()
                for ((k, v) in c.skinport) {
                    w.write("S\t$k\t${v.minPrice ?: ""}\t${v.quantity}")
                    w.newLine()
                }
                for ((k, v) in c.csgo) {
                    w.write("C\t$k\t${v.price ?: ""}\t${v.qty}")
                    w.newLine()
                }
            }
            if (!tmp.renameTo(catalogFile)) tmp.delete()
        } catch (e: Exception) {
        }
    }

    /** Картинки скинов, которые уже находили (основа названия → ссылка). */
    fun cachedIcons(): Map<String, String> = try {
        if (!iconFile.exists()) emptyMap()
        else iconFile.readLines().mapNotNull {
            val p = it.split('\t')
            if (p.size == 2) p[0] to p[1] else null
        }.toMap()
    } catch (e: Exception) {
        emptyMap()
    }

    fun saveIcons(m: Map<String, String>) {
        scope.launch {
            try {
                iconFile.writeText(m.entries.joinToString("\n") { it.key + "\t" + it.value })
            } catch (e: Exception) {
            }
        }
    }

    private suspend fun update(block: (Stored) -> Stored) = mutex.withLock {
        val n = block(_state.value)
        store.save(n)
        _state.value = n
    }

    suspend fun setSettings(s: Settings) = update { it.copy(settings = s) }

    suspend fun setPosition(name: String, buy: Double?, sell: Double?, buyTarget: Double?) = update { st ->
        st.copy(items = st.items.map {
            if (it.name == name) it.copy(buyPrice = buy, targetSell = sell, targetBuy = buyTarget) else it
        })
    }

    /** Убирает скин и возвращает его, чтобы можно было отменить удаление. */
    suspend fun remove(name: String): Tracked? {
        var removed: Tracked? = null
        update { st ->
            removed = st.items.firstOrNull { it.name == name }
            st.copy(items = st.items.filter { it.name != name })
        }
        return removed
    }

    suspend fun restore(t: Tracked) = update { st ->
        if (st.items.any { it.name == t.name }) st else st.copy(items = st.items + t)
    }

    suspend fun add(f: Found) {
        val icon: String? = f.icon ?: try {
            withContext(Dispatchers.IO) { steam.search(f.name).firstOrNull { it.name == f.name }?.icon }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val now = System.currentTimeMillis()
        val cat = _catalogs.value
        val m = cat.skinport[f.name]
        val sl = _previewSales.value[f.name]
        val first = m?.minPrice?.let { listOf(Snap(now, it)) } ?: emptyList()
        update { st ->
            if (st.items.any { it.name == f.name }) st
            else st.copy(
                items = st.items + Tracked(
                    name = f.name, icon = icon, buyPrice = null, addedAt = now,
                    market = m, sales = sl, updatedAt = if (cat.isEmpty()) 0L else now,
                    lastSignal = null, snaps = first, quotes = cat.quotes(f.name)
                )
            )
        }
    }

    // ───────────── поиск по всем скинам ─────────────

    /** Все названия, подходящие под запрос, самые популярные сверху. */
    suspend fun searchLocal(r: Weapons.Resolved): List<String> = withContext(Dispatchers.Default) {
        val idx = index
        val low = indexLower
        val w = r.weapon
        val tokens = (if (w != null) r.rest else r.text).lowercase().split(" ").filter { it.isNotBlank() }
        val out = ArrayList<String>()
        for (i in idx.indices) {
            val l = low[i]
            if (w != null && !Weapons.matches(w, l)) continue
            if (tokens.all { l.contains(it) }) out.add(idx[i])
        }
        out
    }

    /** Картинки и названия из Steam: страница результатов поиска (пустой список при ошибке). */
    suspend fun steamPage(text: String, start: Int): List<Found> = withContext(Dispatchers.IO) {
        try {
            steam.search(text, start)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ───────────── каталоги и история продаж ─────────────

    /** Возвращает текст ошибки или null. */
    suspend fun loadCatalogs(force: Boolean): String? = withContext(Dispatchers.IO) {
        catalogLock.withLock {
            val now = System.currentTimeMillis()
            if (!force && !_catalogs.value.isEmpty() && now - catalogAt < 5 * 60_000L) return@withLock null
            val cur = _catalogs.value
            val errs = ArrayList<String>()
            // обе площадки качаем одновременно
            val (spRes, csRes) = coroutineScope {
                val x = async(Dispatchers.IO) { tryLoad("Skinport") { skin.catalog() } }
                val y = async(Dispatchers.IO) { tryLoad("Market.CSGO") { csgo.catalog() } }
                x.await() to y.await()
            }
            val sp: Map<String, Market>? = spRes.first
            val cs: Map<String, Quote>? = csRes.first
            spRes.second?.let { errs.add(it) }
            csRes.second?.let { errs.add(it) }
            val next = Catalogs(sp ?: cur.skinport, cs ?: cur.csgo)
            _catalogs.value = next
            if (sp != null || cs != null) {
                catalogAt = now
                rebuildIndex(next)
                writeCatalogCache(next, now)
            }
            errs.firstOrNull()
        }
    }

    private fun <T> tryLoad(who: String, block: () -> T): Pair<T?, String?> = try {
        Pair(block(), null)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Pair(null, msg(e, who))
    }

    private fun rebuildIndex(c: Catalogs) {
        val pop = HashMap<String, Int>(c.skinport.size + c.csgo.size)
        for ((k, v) in c.skinport) pop[k] = v.quantity
        for ((k, v) in c.csgo) pop[k] = (pop[k] ?: 0) + v.qty
        val sorted = pop.entries.sortedByDescending { it.value }.map { it.key }
        index = sorted
        indexLower = sorted.map { it.lowercase() }
    }

    suspend fun loadPreviewSales(names: List<String>): String? = withContext(Dispatchers.IO) {
        val missing = names.filter { it !in _previewSales.value }
        if (missing.isEmpty()) null
        else try {
            val s = skin.sales(missing)
            _previewSales.value = _previewSales.value + s
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            msg(e, "Skinport")
        }
    }

    /** Подгружает историю цен для скина из репозитория GitHub. Возвращает текст ошибки или null. */
    suspend fun loadHistory(name: String): String? = withContext(Dispatchers.IO) {
        val repo = _state.value.settings.historyRepo.trim()
        if (repo.isEmpty() || !repo.contains('/')) return@withContext null
        val key = repo + "#" + historyApi.shardOf(name)
        if (!triedShards.add(key)) return@withContext null
        try {
            val m = historyApi.shard(repo, name)
            _history.value = _history.value + m
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            triedShards.remove(key)
            when (e) {
                is UnknownHostException, is SocketTimeoutException, is ConnectException ->
                    "GitHub не отвечает. Если сайт не открывается из твоей страны — попробуй VPN."
                else -> e.message ?: "Не удалось загрузить историю цен"
            }
        }
    }

    suspend fun refreshRate(force: Boolean) = withContext(Dispatchers.IO) {
        val s = _state.value.settings
        val now = System.currentTimeMillis()
        if (!force && s.rubRate > 0 && now - s.rateAt < 6 * 3_600_000L) return@withContext
        try {
            val r = rates.usdRub()
            update { it.copy(settings = it.settings.copy(rubRate = r, rateAt = now)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // курс не критичен: останется прошлое значение
        }
    }

    private suspend fun setQuote(name: String, pid: String, q: Quote) = update { st ->
        st.copy(items = st.items.map {
            if (it.name == name) it.copy(quotes = it.quotes + (pid to q)) else it
        })
    }

    /** Опрашивает площадку по одному скину с паузой (у DMarket и Steam есть лимиты). */
    private suspend fun pollEach(names: Set<String>, pid: String, gapMs: Long, fetch: (String) -> Quote): String? {
        for (n in names) {
            val q = try {
                fetch(n)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return msg(e, Platforms.title(pid))
            }
            setQuote(n, pid, q)
            delay(gapMs)
        }
        return null
    }

    /** Возвращает текст ошибки или null, если всё прошло успешно. */
    suspend fun refresh(force: Boolean): String? = withContext(Dispatchers.IO) {
        val names = _state.value.items.map { it.name }.toSet()
        val now = System.currentTimeMillis()
        refreshRate(false)
        if (names.isEmpty()) return@withContext null
        if (!force && now - lastRefresh < 45_000) return@withContext null

        val errors = ArrayList<String>()
        loadCatalogs(false)?.let { errors.add(it) }
        val cat = _catalogs.value
        // старый кэш с диска не считаем свежими ценами
        val fresh = System.currentTimeMillis() - catalogAt < 30 * 60_000L

        val sales: Map<String, Sales> = try {
            skin.sales(names.toList())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.add(msg(e, "Skinport"))
            emptyMap()
        }

        val ok = (fresh && !cat.isEmpty()) || sales.isNotEmpty()
        update { st ->
            st.copy(items = st.items.map { t ->
                val m: Market? =
                    if (fresh && cat.skinport.isNotEmpty()) (cat.skinport[t.name] ?: Market(null, 0, null, null)) else t.market
                val price = m?.minPrice
                val q = HashMap<String, Quote>(t.quotes)
                if (fresh && cat.skinport.isNotEmpty()) q[Platforms.SKINPORT] = Quote(price, m?.quantity ?: 0)
                if (fresh && cat.csgo.isNotEmpty()) q[Platforms.CSGO] = cat.csgo[t.name] ?: Quote(null, 0)
                t.copy(
                    market = m,
                    sales = sales[t.name] ?: t.sales,
                    updatedAt = if (ok) now else t.updatedAt,
                    snaps = if (fresh && cat.skinport.isNotEmpty() && price != null) addSnap(t.snaps, now, price) else t.snaps,
                    quotes = q
                )
            })
        }

        // медленные площадки: по одному скину с паузой, цены появляются по мере получения
        val on = _state.value.settings.platforms
        if (Platforms.DMARKET in on) {
            pollEach(names, Platforms.DMARKET, 650L) { dmarket.ask(it) }?.let { errors.add(it) }
        }
        if (Platforms.STEAM in on) {
            pollEach(names, Platforms.STEAM, 3300L) { steam.ask(it) }?.let { errors.add(it) }
        }

        if (errors.isEmpty()) lastRefresh = now
        errors.firstOrNull()
    }

    /** Какие сигналы нужно показать уведомлением (только новые смены сигнала). */
    suspend fun pendingNotifications(): List<Pair<Tracked, Analysis>> {
        val st = _state.value
        if (!st.settings.notify) return emptyList()
        if (st.settings.quiet) {
            val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            // ночью молчим; сигналы не пропадут — придут утром при следующей проверке
            if (h >= 23 || h < 8) return emptyList()
        }
        val now = System.currentTimeMillis()
        val res = ArrayList<Pair<Tracked, Analysis>>()
        val updated = st.items.map { t ->
            val a = analyze(t, st.settings, now)
            val strong = a.signal == Signal.SELL || a.signal == Signal.BUY || a.signal == Signal.FLIP
            when {
                strong && t.lastSignal != a.signal.name -> {
                    res.add(t to a)
                    t.copy(lastSignal = a.signal.name)
                }
                !strong && t.lastSignal != null -> t.copy(lastSignal = null)
                else -> t
            }
        }
        update { cur ->
            cur.copy(items = cur.items.map { c ->
                val n = updated.firstOrNull { it.name == c.name }
                if (n != null) c.copy(lastSignal = n.lastSignal) else c
            })
        }
        return res
    }

    private fun addSnap(list: List<Snap>, t: Long, p: Double): List<Snap> {
        val day = 24 * 3_600_000L
        val out = ArrayList(list)
        val last = out.lastOrNull()
        if (last != null && t - last.t < 30 * 60_000L) out[out.lastIndex] = Snap(t, p) else out.add(Snap(t, p))
        // свежие снимки (7 дней) храним все, старше — по одному в сутки
        val cutoff = t - 7 * day
        val res = ArrayList<Snap>()
        var lastDay = Long.MIN_VALUE
        for (s in out) {
            if (s.t < t - 400 * day) continue
            if (s.t >= cutoff) res.add(s)
            else {
                val d = s.t / day
                if (d != lastDay) {
                    res.add(s)
                    lastDay = d
                }
            }
        }
        return res
    }

    private fun msg(e: Exception, who: String): String {
        val hint = " Если сайт не открывается из твоей страны — попробуй VPN."
        return when (e) {
            is UnknownHostException, is SocketTimeoutException, is ConnectException ->
                "$who не отвечает. Проверь интернет." + hint
            else -> {
                val m = e.message
                val base = if (m == null) "$who: не удалось загрузить цены" else if (m.startsWith(who)) m else "$who: $m"
                if (m != null && (m.contains("403") || m.contains("451") || m.contains("ошибка сервера"))) base + hint else base
            }
        }
    }

    /** Записывает сделку: скин продан, на руки получено got. Цена покупки у скина сбрасывается. */
    suspend fun sold(name: String, got: Double) = update { st ->
        val t = st.items.firstOrNull { it.name == name }
        val buy = t?.buyPrice
        if (buy == null) st
        else st.copy(
            items = st.items.map { if (it.name == name) it.copy(buyPrice = null) else it },
            trades = st.trades + Trade(name, buy, got, System.currentTimeMillis())
        )
    }

    suspend fun removeTrade(at: Long) = update { st -> st.copy(trades = st.trades.filter { it.at != at }) }
}
