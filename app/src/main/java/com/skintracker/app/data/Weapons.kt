package com.skintracker.app.data

/** Приводит строку к ключу для сравнения: нижний регистр, только буквы и цифры. */
fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

class Weapon(
    val name: String,
    val query: String,
    val category: String,
    aliases: List<String>
) {
    val keys: List<String> = (listOf(name, query) + aliases).map { norm(it) }.distinct()
}

object Weapons {
    val categories = listOf(
        "Винтовки", "Снайперские", "Пистолеты", "Пистолеты-пулемёты", "Тяжёлое",
        "Ножи", "Перчатки", "Другое"
    )

    val all: List<Weapon> = listOf(
        // Винтовки
        Weapon("AK-47", "AK-47", "Винтовки", listOf("ак", "ак47", "ак-47", "калаш", "калашников")),
        Weapon("M4A4", "M4A4", "Винтовки", listOf("м4а4")),
        Weapon("M4A1-S", "M4A1-S", "Винтовки", listOf("м4а1", "м4а1с", "м4а1s", "m4a1")),
        Weapon("Galil AR", "Galil AR", "Винтовки", listOf("галиль", "галил")),
        Weapon("FAMAS", "FAMAS", "Винтовки", listOf("фамас")),
        Weapon("SG 553", "SG 553", "Винтовки", listOf("сг553", "сг")),
        Weapon("AUG", "AUG", "Винтовки", listOf("ауг")),
        // Снайперские
        Weapon("AWP", "AWP", "Снайперские", listOf("авп", "снайперка")),
        Weapon("SSG 08", "SSG 08", "Снайперские", listOf("ссг", "ссг08", "скаут", "scout", "ssg")),
        Weapon("SCAR-20", "SCAR-20", "Снайперские", listOf("скар", "скар20", "scar")),
        Weapon("G3SG1", "G3SG1", "Снайперские", listOf("г3", "г3сг1", "g3")),
        // Пистолеты
        Weapon("Glock-18", "Glock-18", "Пистолеты", listOf("глок", "глок18", "glock")),
        Weapon("USP-S", "USP-S", "Пистолеты", listOf("юсп", "юспс", "usp")),
        Weapon("P2000", "P2000", "Пистолеты", listOf("п2000")),
        Weapon("P250", "P250", "Пистолеты", listOf("п250")),
        Weapon("Desert Eagle", "Desert Eagle", "Пистолеты", listOf("дигл", "дезерт", "дэгл", "deagle", "eagle")),
        Weapon("Five-SeveN", "Five-SeveN", "Пистолеты", listOf("файвсевен", "файв", "five", "57")),
        Weapon("Tec-9", "Tec-9", "Пистолеты", listOf("тек", "тек9", "tec")),
        Weapon("CZ75-Auto", "CZ75-Auto", "Пистолеты", listOf("цз", "цз75", "cz")),
        Weapon("Dual Berettas", "Dual Berettas", "Пистолеты", listOf("беретты", "дуалки", "дуал", "dual", "berettas")),
        Weapon("R8 Revolver", "R8 Revolver", "Пистолеты", listOf("р8", "револьвер", "revolver")),
        // Пистолеты-пулемёты
        Weapon("MAC-10", "MAC-10", "Пистолеты-пулемёты", listOf("мак", "мак10", "mac")),
        Weapon("MP9", "MP9", "Пистолеты-пулемёты", listOf("мп9")),
        Weapon("MP7", "MP7", "Пистолеты-пулемёты", listOf("мп7")),
        Weapon("MP5-SD", "MP5-SD", "Пистолеты-пулемёты", listOf("мп5", "mp5")),
        Weapon("UMP-45", "UMP-45", "Пистолеты-пулемёты", listOf("умп", "умп45", "ump")),
        Weapon("P90", "P90", "Пистолеты-пулемёты", listOf("п90")),
        Weapon("PP-Bizon", "PP-Bizon", "Пистолеты-пулемёты", listOf("бизон", "bizon")),
        // Тяжёлое
        Weapon("Nova", "Nova", "Тяжёлое", listOf("нова")),
        Weapon("XM1014", "XM1014", "Тяжёлое", listOf("хм1014", "хм", "xm")),
        Weapon("Sawed-Off", "Sawed-Off", "Тяжёлое", listOf("обрез", "sawed")),
        Weapon("MAG-7", "MAG-7", "Тяжёлое", listOf("маг", "маг7", "mag")),
        Weapon("M249", "M249", "Тяжёлое", listOf("м249")),
        Weapon("Negev", "Negev", "Тяжёлое", listOf("негев")),
        // Ножи
        Weapon("Karambit", "Karambit", "Ножи", listOf("керамбит", "карамбит")),
        Weapon("Butterfly Knife", "Butterfly Knife", "Ножи", listOf("бабочка", "баттерфляй", "butterfly")),
        Weapon("M9 Bayonet", "M9 Bayonet", "Ножи", listOf("м9", "м9байонет", "m9")),
        Weapon("Bayonet", "Bayonet", "Ножи", listOf("штык", "штыкнож", "байонет")),
        Weapon("Flip Knife", "Flip Knife", "Ножи", listOf("флип", "flip")),
        Weapon("Gut Knife", "Gut Knife", "Ножи", listOf("ган", "гат", "gut")),
        Weapon("Huntsman Knife", "Huntsman Knife", "Ножи", listOf("хантсман", "huntsman")),
        Weapon("Falchion Knife", "Falchion Knife", "Ножи", listOf("фальшион", "falchion")),
        Weapon("Bowie Knife", "Bowie Knife", "Ножи", listOf("боуи", "bowie")),
        Weapon("Shadow Daggers", "Shadow Daggers", "Ножи", listOf("кинжалы", "тычки", "daggers")),
        Weapon("Talon Knife", "Talon Knife", "Ножи", listOf("талон", "talon")),
        Weapon("Stiletto Knife", "Stiletto Knife", "Ножи", listOf("стилет", "stiletto")),
        Weapon("Ursus Knife", "Ursus Knife", "Ножи", listOf("урсус", "ursus")),
        Weapon("Navaja Knife", "Navaja Knife", "Ножи", listOf("навая", "navaja")),
        Weapon("Skeleton Knife", "Skeleton Knife", "Ножи", listOf("скелет", "skeleton")),
        Weapon("Nomad Knife", "Nomad Knife", "Ножи", listOf("номад", "nomad")),
        Weapon("Paracord Knife", "Paracord Knife", "Ножи", listOf("паракорд", "paracord")),
        Weapon("Survival Knife", "Survival Knife", "Ножи", listOf("выживание", "survival")),
        Weapon("Classic Knife", "Classic Knife", "Ножи", listOf("классический", "classic")),
        Weapon("Kukri Knife", "Kukri Knife", "Ножи", listOf("кукри", "kukri")),
        // Перчатки
        Weapon("Все перчатки", "Gloves", "Перчатки", listOf("перчатки", "gloves")),
        Weapon("Sport Gloves", "Sport Gloves", "Перчатки", listOf("спортивные", "sport")),
        Weapon("Specialist Gloves", "Specialist Gloves", "Перчатки", listOf("специалист", "specialist")),
        Weapon("Driver Gloves", "Driver Gloves", "Перчатки", listOf("водительские", "driver")),
        Weapon("Hand Wraps", "Hand Wraps", "Перчатки", listOf("бинты", "wraps")),
        Weapon("Moto Gloves", "Moto Gloves", "Перчатки", listOf("мото", "moto")),
        Weapon("Hydra Gloves", "Hydra Gloves", "Перчатки", listOf("гидра", "hydra")),
        Weapon("Broken Fang Gloves", "Broken Fang Gloves", "Перчатки", listOf("brokenfang", "сломанныйклык")),
        Weapon("Bloodhound Gloves", "Bloodhound Gloves", "Перчатки", listOf("бладхаунд", "bloodhound")),
        // Другое
        Weapon("Кейсы", "Case", "Другое", listOf("кейс", "case", "cases")),
        Weapon("Наклейки", "Sticker", "Другое", listOf("наклейка", "стикер", "стикеры", "sticker")),
        Weapon("Капсулы", "Capsule", "Другое", listOf("капсула", "capsule")),
        Weapon("Брелоки", "Charm", "Другое", listOf("брелок", "charm")),
        Weapon("Граффити", "Graffiti", "Другое", listOf("граффити", "graffiti"))
    )

    fun inCategory(cat: String): List<Weapon> = all.filter { it.category == cat }

    /** Подсказки по введённому тексту: «ак» → AK-47, «47» → AK-47, «м4» → M4A4 и M4A1-S. */
    fun suggest(q: String, limit: Int = 8): List<Weapon> {
        val n = norm(q)
        if (n.isEmpty()) return emptyList()
        val starts = all.filter { w -> w.keys.any { it.startsWith(n) } }
        val contains = all.filter { w -> w !in starts && w.keys.any { it.contains(n) } }
        return (starts + contains).take(limit)
    }

    private fun exact(q: String): Weapon? {
        val n = norm(q)
        if (n.isEmpty()) return null
        return all.firstOrNull { w -> w.keys.any { it == n } }
    }

    /**
     * Разобранный запрос: weapon — найденное оружие (если есть), rest — остальные слова
     * (уже переведённые на английский), text — строка для поиска.
     */
    class Resolved(val weapon: Weapon?, val rest: String, val text: String)

    /** Русские слова из названий популярных скинов → английские. */
    private val skinWords = mapOf(
        "редлайн" to "redline", "азимов" to "asiimov", "вулкан" to "vulcan", "драгон" to "dragon",
        "лор" to "lore", "фейд" to "fade", "допплер" to "doppler", "хаул" to "howl", "вой" to "howl",
        "огненный" to "fire", "змей" to "serpent", "нео" to "neo", "нуар" to "noir", "гипер" to "hyper",
        "бист" to "beast", "принтстрим" to "printstream", "хардендед" to "hardened", "тайгер" to "tiger",
        "туф" to "tooth", "слотер" to "slaughter", "мраморный" to "marble", "фантом" to "phantom",
        "спидстер" to "speedster", "ледяной" to "frost", "огонь" to "fire", "золотой" to "gold"
    )

    private fun translate(token: String): String = skinWords[norm(token)] ?: token

    /**
     * Превращает запрос в поисковый: «ак редлайн» → оружие AK-47 + «redline», «47» → AK-47.
     * null — запрос слишком короткий и подходит к нескольким видам оружия (нужно выбрать из подсказок).
     */
    fun resolve(q: String): Resolved? {
        val t = q.trim()
        if (t.isEmpty()) return Resolved(null, "", "")
        val tokens = t.split(Regex("\\s+"))
        for (k in minOf(3, tokens.size) downTo 1) {
            val head = tokens.take(k).joinToString(" ")
            val w = exact(head)
            if (w != null) {
                val rest = tokens.drop(k).joinToString(" ") { translate(it) }
                return Resolved(w, rest, (w.query + " " + rest).trim())
            }
        }
        val first = tokens[0]
        val rest = tokens.drop(1).joinToString(" ") { translate(it) }
        val s = suggest(first, 20)
        return when {
            s.size == 1 -> Resolved(s[0], rest, (s[0].query + " " + rest).trim())
            s.size > 1 && rest.isEmpty() && norm(first).length <= 3 -> null
            else -> Resolved(null, "", tokens.joinToString(" ") { translate(it) })
        }
    }

    /** Подходит ли название скина под выбранное оружие: «AK-47 | Redline», «StatTrak™ AK-47 | …», «★ Karambit | …». */
    fun matches(w: Weapon, lowerName: String): Boolean {
        if (w.category == "Другое" || w.query == "Gloves") return lowerName.contains(w.query.lowercase())
        var s = lowerName
        var changed = true
        while (changed) {
            changed = false
            for (p in listOf("★ ", "stattrak™ ", "souvenir ")) {
                if (s.startsWith(p)) {
                    s = s.removePrefix(p)
                    changed = true
                }
            }
        }
        return s.startsWith(w.query.lowercase() + " |")
    }
}
