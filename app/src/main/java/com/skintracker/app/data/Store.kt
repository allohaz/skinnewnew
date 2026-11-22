package com.skintracker.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private fun JSONObject.dbl(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null

/** Простое хранилище в JSON-файле: без Room и лишних зависимостей. */
class Store(private val file: File) {

    fun load(): Stored = try {
        if (!file.exists()) Stored(Settings(), emptyList()) else parse(JSONObject(file.readText()))
    } catch (e: Exception) {
        Stored(Settings(), emptyList())
    }

    fun save(s: Stored) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(toJson(s).toString())
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun parse(o: JSONObject): Stored {
        val so = o.optJSONObject("settings")
        val d = Settings()
        val settings = if (so == null) d else {
            val pl = so.optJSONArray("platforms")
            val platforms = if (pl == null) d.platforms else (0 until pl.length()).map { pl.getString(it) }.toSet()
            val fo = so.optJSONObject("fees")
            val fees: Map<String, Int> =
                if (fo == null) emptyMap() else fo.keys().asSequence().associateWith { fo.getInt(it) }
            Settings(
                sellPct = so.optInt("sellPct", d.sellPct),
                profitPct = so.optInt("profitPct", d.profitPct),
                notify = so.optBoolean("notify", d.notify),
                everyHours = so.optInt("everyHours", d.everyHours),
                showRub = so.optBoolean("showRub", d.showRub),
                rubRate = so.optDouble("rubRate", 0.0),
                rateAt = so.optLong("rateAt", 0L),
                platforms = platforms,
                fees = fees,
                quiet = so.optBoolean("quiet", d.quiet),
                theme = so.optInt("theme", d.theme),
                historyRepo = so.optString("historyRepo", d.historyRepo)
            )
        }
        val arr = o.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).map { itemFrom(arr.getJSONObject(it)) }
        val ta = o.optJSONArray("trades") ?: JSONArray()
        val trades = (0 until ta.length()).map {
            val x = ta.getJSONObject(it)
            Trade(x.getString("name"), x.getDouble("buy"), x.getDouble("got"), x.optLong("at", 0L))
        }
        return Stored(settings, items, trades)
    }

    private fun itemFrom(o: JSONObject): Tracked {
        val mo = o.optJSONObject("market")
        val market = mo?.let {
            Market(it.dbl("minPrice"), it.optInt("quantity", 0), it.dbl("suggested"), it.str("itemPage"))
        }
        val so = o.optJSONObject("sales")
        val sales = so?.let {
            Sales(statFrom(it.optJSONObject("d1")), statFrom(it.optJSONObject("d7")),
                statFrom(it.optJSONObject("d30")), statFrom(it.optJSONObject("d90")))
        }
        val sa = o.optJSONArray("snaps") ?: JSONArray()
        val snaps = (0 until sa.length()).map {
            val p = sa.getJSONArray(it)
            Snap(p.getLong(0), p.getDouble(1))
        }
        val qo = o.optJSONObject("quotes")
        val quotes: Map<String, Quote> = if (qo == null) emptyMap() else {
            val m = HashMap<String, Quote>()
            for (k in qo.keys()) {
                val q = qo.getJSONObject(k)
                m[k] = Quote(q.dbl("price"), q.optInt("qty", 0))
            }
            m
        }
        return Tracked(
            name = o.getString("name"),
            icon = o.str("icon"),
            buyPrice = o.dbl("buyPrice"),
            addedAt = o.optLong("addedAt", 0L),
            market = market,
            sales = sales,
            updatedAt = o.optLong("updatedAt", 0L),
            lastSignal = o.str("lastSignal"),
            snaps = snaps,
            targetSell = o.dbl("targetSell"),
            targetBuy = o.dbl("targetBuy"),
            quotes = quotes
        )
    }

    private fun statFrom(o: JSONObject?): Stat =
        if (o == null) Stat(null, null, null, null, 0)
        else Stat(o.dbl("min"), o.dbl("max"), o.dbl("avg"), o.dbl("median"), o.optInt("volume", 0))

    private fun statTo(s: Stat): JSONObject = JSONObject()
        .put("min", s.min ?: JSONObject.NULL)
        .put("max", s.max ?: JSONObject.NULL)
        .put("avg", s.avg ?: JSONObject.NULL)
        .put("median", s.median ?: JSONObject.NULL)
        .put("volume", s.volume)

    private fun toJson(s: Stored): JSONObject {
        val items = JSONArray()
        for (t in s.items) {
            val o = JSONObject()
                .put("name", t.name)
                .put("icon", t.icon ?: JSONObject.NULL)
                .put("buyPrice", t.buyPrice ?: JSONObject.NULL)
                .put("targetSell", t.targetSell ?: JSONObject.NULL)
                .put("targetBuy", t.targetBuy ?: JSONObject.NULL)
                .put("addedAt", t.addedAt)
                .put("updatedAt", t.updatedAt)
                .put("lastSignal", t.lastSignal ?: JSONObject.NULL)
            t.market?.let {
                o.put("market", JSONObject()
                    .put("minPrice", it.minPrice ?: JSONObject.NULL)
                    .put("quantity", it.quantity)
                    .put("suggested", it.suggested ?: JSONObject.NULL)
                    .put("itemPage", it.itemPage ?: JSONObject.NULL))
            }
            t.sales?.let {
                o.put("sales", JSONObject()
                    .put("d1", statTo(it.d1))
                    .put("d7", statTo(it.d7))
                    .put("d30", statTo(it.d30))
                    .put("d90", statTo(it.d90)))
            }
            val qo = JSONObject()
            for ((k, q) in t.quotes) {
                qo.put(k, JSONObject().put("price", q.price ?: JSONObject.NULL).put("qty", q.qty))
            }
            o.put("quotes", qo)
            val sa = JSONArray()
            for (p in t.snaps) sa.put(JSONArray().put(p.t).put(p.p))
            o.put("snaps", sa)
            items.put(o)
        }
        val st = s.settings
        val pl = JSONArray()
        for (p in st.platforms) pl.put(p)
        val fo = JSONObject()
        for ((k, v) in st.fees) fo.put(k, v)
        return JSONObject()
            .put("settings", JSONObject()
                .put("sellPct", st.sellPct)
                .put("profitPct", st.profitPct)
                .put("notify", st.notify)
                .put("everyHours", st.everyHours)
                .put("showRub", st.showRub)
                .put("rubRate", st.rubRate)
                .put("rateAt", st.rateAt)
                .put("platforms", pl)
                .put("fees", fo)
                .put("quiet", st.quiet)
                .put("theme", st.theme)
                .put("historyRepo", st.historyRepo))
            .put("items", items)
            .put("trades", JSONArray().also { ja ->
                for (x in s.trades) ja.put(JSONObject().put("name", x.name).put("buy", x.buy).put("got", x.got).put("at", x.at))
            })
    }
}
