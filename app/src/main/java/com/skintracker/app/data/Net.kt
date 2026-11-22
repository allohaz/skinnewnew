package com.skintracker.app.data

import android.util.JsonReader
import android.util.JsonToken
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object Net {
    // Skinport требует Accept-Encoding: br — BrotliInterceptor добавляет его и распаковывает ответ.
    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(BrotliInterceptor)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
}

private fun JsonReader.strOrNull(): String? =
    if (peek() == JsonToken.NULL) { nextNull(); null } else nextString()

private fun JsonReader.dblOrNull(): Double? =
    if (peek() == JsonToken.NULL) { nextNull(); null } else nextDouble()

private fun JsonReader.intOrZero(): Int =
    if (peek() == JsonToken.NULL) { nextNull(); 0 } else nextInt()

/** Число, которое может прийти и числом, и строкой ("9.820"). */
private fun JsonReader.numOrNull(): Double? = when (peek()) {
    JsonToken.NULL -> { nextNull(); null }
    JsonToken.STRING -> nextString().toDoubleOrNull()
    JsonToken.NUMBER -> nextDouble()
    else -> { skipValue(); null }
}

private fun JSONObject.dbl(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null

private fun userAgent(b: Request.Builder): Request.Builder = b.header("User-Agent", "SkinTracker/1.0")

// ───────────────────────── Skinport ─────────────────────────

class SkinportApi(private val client: OkHttpClient) {

    private fun get(url: HttpUrl): Response {
        val req = userAgent(Request.Builder().url(url)).build()
        val r = client.newCall(req).execute()
        if (!r.isSuccessful) {
            val c = r.code
            r.close()
            throw IOException(
                if (c == 429) "Skinport: слишком много запросов, попробуй через пару минут"
                else "Skinport: ошибка сервера ($c)"
            )
        }
        return r
    }

    /** Весь каталог Skinport (имя → цена и число предложений). Читается потоком. */
    fun catalog(): Map<String, Market> {
        val url = "https://api.skinport.com/v1/items".toHttpUrl().newBuilder()
            .addQueryParameter("app_id", "730")
            .addQueryParameter("currency", "USD")
            .build()
        val out = HashMap<String, Market>(32768)
        get(url).use { resp ->
            JsonReader(resp.body!!.charStream()).use { jr ->
                jr.beginArray()
                while (jr.hasNext()) {
                    jr.beginObject()
                    var name: String? = null
                    var min: Double? = null
                    var qty = 0
                    var sug: Double? = null
                    var page: String? = null
                    while (jr.hasNext()) {
                        when (jr.nextName()) {
                            "market_hash_name" -> name = jr.strOrNull()
                            "min_price" -> min = jr.dblOrNull()
                            "quantity" -> qty = jr.intOrZero()
                            "suggested_price" -> sug = jr.dblOrNull()
                            "item_page" -> page = jr.strOrNull()
                            else -> jr.skipValue()
                        }
                    }
                    jr.endObject()
                    val n = name
                    if (n != null) out[n] = Market(min, qty, sug, page)
                }
            }
        }
        return out
    }

    /** История продаж за 24ч / 7д / 30д / 90д. */
    fun sales(names: List<String>): Map<String, Sales> {
        val out = HashMap<String, Sales>()
        for (chunk in names.chunked(20)) {
            val url = "https://api.skinport.com/v1/sales/history".toHttpUrl().newBuilder()
                .addQueryParameter("app_id", "730")
                .addQueryParameter("currency", "USD")
                .addQueryParameter("market_hash_name", chunk.joinToString(","))
                .build()
            val body = get(url).use { it.body!!.string() }
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out[o.getString("market_hash_name")] = Sales(
                    stat(o.optJSONObject("last_24_hours")),
                    stat(o.optJSONObject("last_7_days")),
                    stat(o.optJSONObject("last_30_days")),
                    stat(o.optJSONObject("last_90_days"))
                )
            }
        }
        return out
    }

    private fun stat(o: JSONObject?): Stat =
        if (o == null) Stat(null, null, null, null, 0)
        else Stat(o.dbl("min"), o.dbl("max"), o.dbl("avg"), o.dbl("median"), o.optInt("volume", 0))
}

// ───────────────────────── Market.CSGO ─────────────────────────

/** Открытый список цен Market.CSGO: все скины одним запросом, ключ не нужен. */
class CsgoMarketApi(private val client: OkHttpClient) {

    fun catalog(): Map<String, Quote> {
        val req = userAgent(Request.Builder().url("https://market.csgo.com/api/v2/prices/USD.json")).build()
        val out = HashMap<String, Quote>(32768)
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Market.CSGO: ошибка сервера (${r.code})")
            JsonReader(r.body!!.charStream()).use { jr ->
                jr.beginObject()
                while (jr.hasNext()) {
                    if (jr.nextName() == "items") {
                        jr.beginArray()
                        while (jr.hasNext()) {
                            jr.beginObject()
                            var n: String? = null
                            var price: Double? = null
                            var vol = 0
                            while (jr.hasNext()) {
                                when (jr.nextName()) {
                                    "market_hash_name" -> n = jr.strOrNull()
                                    "price" -> price = jr.numOrNull()
                                    "volume" -> vol = (jr.numOrNull() ?: 0.0).toInt()
                                    else -> jr.skipValue()
                                }
                            }
                            jr.endObject()
                            val nn = n
                            if (nn != null) out[nn] = Quote(price?.takeIf { it > 0.0 }, vol)
                        }
                        jr.endArray()
                    } else {
                        jr.skipValue()
                    }
                }
            }
        }
        return out
    }
}

// ───────────────────────── DMarket ─────────────────────────

/** DMarket: открытый список предложений, ключ не нужен (лимит — 2 запроса в секунду). */
class DMarketApi(private val client: OkHttpClient) {

    fun ask(name: String): Quote {
        val url = "https://api.dmarket.com/exchange/v1/market/items".toHttpUrl().newBuilder()
            .addQueryParameter("gameId", "a8db")
            .addQueryParameter("title", name)
            .addQueryParameter("currency", "USD")
            .addQueryParameter("orderBy", "price")
            .addQueryParameter("orderDir", "asc")
            .addQueryParameter("limit", "20")
            .build()
        val req = userAgent(Request.Builder().url(url)).build()
        val body = client.newCall(req).execute().use { r ->
            if (r.code == 429) throw IOException("DMarket: слишком много запросов, попробуй позже")
            if (!r.isSuccessful) throw IOException("DMarket: ошибка сервера (${r.code})")
            r.body!!.string()
        }
        val arr = JSONObject(body).optJSONArray("objects") ?: return Quote(null, 0)
        var best: Double? = null
        var count = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("title") != name) continue
            val cents = o.optJSONObject("price")?.optString("USD")?.toDoubleOrNull() ?: continue
            val price = cents / 100.0
            count++
            if (best == null || price < best) best = price
        }
        return Quote(best, count)
    }
}

// ───────────────────────── Steam ─────────────────────────

/** Steam Community Market: поиск с картинками и цена по названию. */
class SteamApi(private val client: OkHttpClient) {

    fun search(query: String, start: Int = 0): List<Found> {
        val url = "https://steamcommunity.com/market/search/render/".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("start", start.toString())
            .addQueryParameter("count", PAGE.toString())
            .addQueryParameter("search_descriptions", "0")
            .addQueryParameter("sort_column", "popular")
            .addQueryParameter("sort_dir", "desc")
            .addQueryParameter("appid", "730")
            .addQueryParameter("norender", "1")
            .build()
        val req = userAgent(Request.Builder().url(url)).build()
        val body = client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Steam: ошибка ${r.code}")
            r.body!!.string()
        }
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<Found>()
        for (i in 0 until results.length()) {
            val o = results.getJSONObject(i)
            val name = o.optString("hash_name", "")
            if (name.isEmpty()) continue
            val icon = o.optJSONObject("asset_description")?.optString("icon_url", "")
            val full = if (icon.isNullOrEmpty()) null
            else "https://community.fastly.steamstatic.com/economy/image/$icon/256fx256f"
            out.add(Found(name, full))
        }
        return out
    }

    /** Самая низкая цена в Steam (в долларах). У Steam жёсткий лимит запросов. */
    fun ask(name: String): Quote {
        val url = "https://steamcommunity.com/market/priceoverview/".toHttpUrl().newBuilder()
            .addQueryParameter("appid", "730")
            .addQueryParameter("currency", "1")
            .addQueryParameter("market_hash_name", name)
            .build()
        val req = userAgent(Request.Builder().url(url)).build()
        val body = client.newCall(req).execute().use { r ->
            if (r.code == 429) throw IOException("Steam: слишком много запросов, попробуй позже")
            if (!r.isSuccessful) throw IOException("Steam: ошибка ${r.code}")
            r.body!!.string()
        }
        val o = JSONObject(body)
        if (!o.optBoolean("success", false)) return Quote(null, 0)
        val price = money(o.optString("lowest_price", ""))
        val vol = o.optString("volume", "").replace(",", "").trim().toIntOrNull() ?: 0
        return Quote(price, vol)
    }

    private fun money(s: String): Double? {
        val cleaned = s.filter { it.isDigit() || it == '.' || it == ',' }.replace(",", "")
        return cleaned.toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    companion object {
        const val PAGE = 40
    }
}

// ───────────────────────── Курс валют ─────────────────────────

/** Курс доллара к рублю по данным ЦБ РФ (бесплатно, без ключа). */
class RateApi(private val client: OkHttpClient) {
    fun usdRub(): Double {
        val req = userAgent(Request.Builder().url("https://www.cbr-xml-daily.ru/daily_json.js")).build()
        val body = client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Курс валют: ошибка ${r.code}")
            r.body!!.string()
        }
        val usd = JSONObject(body).getJSONObject("Valute").getJSONObject("USD")
        val nominal = usd.optDouble("Nominal", 1.0)
        return usd.getDouble("Value") / (if (nominal > 0) nominal else 1.0)
    }
}


// ───────────────────────── История цен с GitHub ─────────────────────────

/** Читает файлы истории, которые собирает GitHub Actions (tools/collect.py). */
class HistoryApi(private val client: OkHttpClient) {

    fun shardOf(name: String): String {
        val md = java.security.MessageDigest.getInstance("MD5").digest(name.toByteArray(Charsets.UTF_8))
        return String.format(java.util.Locale.US, "%02x", md[0].toInt() and 0xff)
    }

    /** Все скины из одного файла: название → цены по времени (Skinport, а если нет — Market.CSGO). */
    fun shard(repo: String, name: String): Map<String, List<Snap>> {
        val url = "https://raw.githubusercontent.com/" + repo.trim().trim('/') + "/history/h/" + shardOf(name) + ".json"
        val req = userAgent(Request.Builder().url(url)).build()
        client.newCall(req).execute().use { r ->
            if (r.code == 404) throw IOException("История: в репозитории ещё нет данных (или он закрытый)")
            if (!r.isSuccessful) throw IOException("История: ошибка ${r.code}")
            val o = JSONObject(r.body!!.string())
            val res = HashMap<String, List<Snap>>()
            for (k in o.keys()) {
                val a = o.getJSONArray(k)
                val list = ArrayList<Snap>(a.length())
                for (i in 0 until a.length()) {
                    val p = a.getJSONArray(i)
                    val price = if (!p.isNull(1)) p.getDouble(1) else if (p.length() > 2 && !p.isNull(2)) p.getDouble(2) else null
                    if (price != null) list.add(Snap(p.getLong(0) * 60_000L, price))
                }
                res[k] = list
            }
            return res
        }
    }
}
