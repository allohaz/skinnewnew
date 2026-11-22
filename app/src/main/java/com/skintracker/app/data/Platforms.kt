package com.skintracker.app.data

class PlatformInfo(
    val id: String,
    val title: String,
    val note: String,
    val defaultFee: Int,
    val defaultOn: Boolean
)

/** Площадки, цены на которых умеет получать приложение (бесплатно, без ключей). */
object Platforms {
    const val SKINPORT = "skinport"
    const val CSGO = "csgomarket"
    const val DMARKET = "dmarket"
    const val STEAM = "steam"

    val all: List<PlatformInfo> = listOf(
        PlatformInfo(SKINPORT, "Skinport", "Много скинов, деньги можно вывести. По ней считаю историю цен.", 12, true),
        PlatformInfo(CSGO, "Market.CSGO", "Много скинов, часто дешевле других.", 5, true),
        PlatformInfo(DMARKET, "DMarket", "Небольшая комиссия при продаже.", 5, true),
        PlatformInfo(STEAM, "Steam", "Деньги остаются на кошельке Steam, вывести нельзя.", 13, false)
    )

    val defaultOn: Set<String> = all.filter { it.defaultOn }.map { it.id }.toSet()

    fun byId(id: String): PlatformInfo? = all.firstOrNull { it.id == id }

    fun title(id: String): String = byId(id)?.title ?: id
}
