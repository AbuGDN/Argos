package com.abugdn.wid.data

/** Cidades e pontos citados com frequência, para o mapa "por cidade". */
data class City(val name: String, val lat: Double, val lon: Double, val tag: String, val terms: List<String>)

val CITIES = listOf(
    // Israel
    City("Tel Aviv", 32.08, 34.78, "israel", listOf("tel aviv", "תל אביב", "تل ابيب")),
    City("Jerusalém", 31.77, 35.21, "israel", listOf("jerusalem", "jerusalém", "ירושלים", "القدس")),
    City("Haifa", 32.79, 34.99, "israel", listOf("haifa", "חיפה", "حيفا")),
    City("Eilat", 29.56, 34.95, "israel", listOf("eilat", "אילת", "ايلات")),
    City("Beersheba", 31.25, 34.79, "israel", listOf("beersheba", "beer sheva", "berseba", "באר שבע")),
    City("Ashkelon", 31.67, 34.57, "israel", listOf("ashkelon", "asquelon", "אשקלון", "عسقلان")),
    City("Sderot", 31.52, 34.60, "israel", listOf("sderot", "שדרות")),
    City("Colinas de Golã", 33.00, 35.75, "israel", listOf("golan", "golã", "רמת הגולן", "الجولان")),
    // Gaza
    City("Cidade de Gaza", 31.52, 34.45, "gaza", listOf("gaza city", "cidade de gaza")),
    City("Rafah", 31.29, 34.25, "gaza", listOf("rafah", "רפיח", "رفح")),
    City("Khan Younis", 31.34, 34.30, "gaza", listOf("khan younis", "khan yunis", "חאן יונס", "خان يونس")),
    City("Deir al-Balah", 31.42, 34.35, "gaza", listOf("deir al-balah", "deir el-balah", "دير البلح")),
    City("Jabalia", 31.53, 34.48, "gaza", listOf("jabalia", "jabaliya", "ג'באליה", "جباليا")),
    City("Nuseirat", 31.45, 34.39, "gaza", listOf("nuseirat", "النصيرات")),
    // Cisjordânia
    City("Jenin", 32.46, 35.30, "cisjordania", listOf("jenin", "ג'נין", "جنين")),
    City("Nablus", 32.22, 35.25, "cisjordania", listOf("nablus", "שכם", "نابلس")),
    City("Ramallah", 31.90, 35.20, "cisjordania", listOf("ramallah", "רמאללה", "رام الله")),
    City("Tulkarm", 32.31, 35.03, "cisjordania", listOf("tulkarm", "טול כרם", "طولكرم")),
    City("Hebron", 31.53, 35.10, "cisjordania", listOf("hebron", "hebrom", "חברון", "الخليل")),
    // Líbano
    City("Beirute", 33.89, 35.50, "libano", listOf("beirut", "beirute", "ביירות", "بيروت")),
    City("Tiro", 33.27, 35.20, "libano", listOf("tyre", "cidade de tiro")),
    City("Sídon", 33.56, 35.37, "libano", listOf("sidon", "sídon", "saida", "צידון")),
    City("Nabatieh", 33.38, 35.48, "libano", listOf("nabatieh", "nabatiyeh", "النبطية")),
    City("Baalbek", 34.00, 36.20, "libano", listOf("baalbek", "בעלבכ", "بعلبك")),
    // Síria
    City("Damasco", 33.51, 36.29, "siria", listOf("damascus", "damasco", "דמשק", "دمشق")),
    City("Alepo", 36.20, 37.13, "siria", listOf("aleppo", "alepo", "חלב", "حلب")),
    City("Homs", 34.73, 36.71, "siria", listOf("homs", "حمص")),
    City("Idlib", 35.93, 36.63, "siria", listOf("idlib", "ادلب")),
    City("Latakia", 35.52, 35.78, "siria", listOf("latakia", "lattakia", "اللاذقية")),
    City("Deir ez-Zor", 35.33, 40.14, "siria", listOf("deir ez-zor", "deir ezzor", "deir el-zour", "دير الزور")),
    City("Suwayda", 32.71, 36.57, "siria", listOf("suwayda", "sweida", "sueida", "السويداء")),
    // Iraque
    City("Bagdá", 33.31, 44.37, "iraque", listOf("baghdad", "bagdá", "בגדד", "بغداد")),
    City("Erbil", 36.19, 44.01, "iraque", listOf("erbil", "irbil", "اربيل")),
    City("Mossul", 36.34, 43.13, "iraque", listOf("mosul", "mossul", "الموصل")),
    City("Basra", 30.51, 47.78, "iraque", listOf("basra", "basrah", "البصرة")),
    // Irã
    City("Teerã", 35.69, 51.39, "ira", listOf("tehran", "teerã", "טהרן", "طهران")),
    City("Isfahan", 32.65, 51.67, "ira", listOf("isfahan", "isfahã", "esfahan", "איספהאן", "اصفهان")),
    City("Natanz", 33.72, 51.93, "ira", listOf("natanz", "נתנז", "نطنز")),
    City("Fordow", 34.88, 50.99, "ira", listOf("fordow", "fordo", "פורדו", "فوردو")),
    City("Bandar Abbas", 27.18, 56.27, "ira", listOf("bandar abbas", "بندر عباس")),
    City("Tabriz", 38.08, 46.29, "ira", listOf("tabriz", "تبريز")),
    City("Shiraz", 29.59, 52.58, "ira", listOf("shiraz", "شيراز")),
    City("Bushehr", 28.97, 50.84, "ira", listOf("bushehr", "بوشهر")),
    City("Estreito de Ormuz", 26.57, 56.25, "ira", listOf("hormuz", "ormuz", "הורמוז", "هرمز")),
    // Iêmen e Mar Vermelho
    City("Sanaa", 15.37, 44.19, "iemen", listOf("sanaa", "sana'a", "saná", "צנעא", "صنعاء")),
    City("Hodeidah", 14.80, 42.95, "iemen", listOf("hodeidah", "hudaydah", "hodeida", "חודיידה", "الحديدة")),
    City("Áden", 12.79, 45.03, "iemen", listOf("aden", "áden", "عدن")),
    City("Marib", 15.46, 45.33, "iemen", listOf("marib", "مأرب")),
    City("Bab el-Mandeb", 12.60, 43.40, "iemen", listOf("bab el-mandeb", "bab al-mandab", "باب المندب")),
    // Ucrânia e Rússia
    City("Kiev", 50.45, 30.52, "ucrania_russia", listOf("kyiv", "kiev", "קייב", "كييف")),
    City("Kharkiv", 49.99, 36.23, "ucrania_russia", listOf("kharkiv", "kharkov", "carcóvia")),
    City("Odessa", 46.48, 30.72, "ucrania_russia", listOf("odesa", "odessa")),
    City("Zaporíjia", 47.84, 35.14, "ucrania_russia", listOf("zaporizhzhia", "zaporizhia", "zaporíjia", "zaporijia")),
    City("Dnipro", 48.46, 35.05, "ucrania_russia", listOf("dnipro", "dnipropetrovsk")),
    City("Kherson", 46.64, 32.61, "ucrania_russia", listOf("kherson", "khersón")),
    City("Donetsk", 48.00, 37.80, "ucrania_russia", listOf("donetsk")),
    City("Pokrovsk", 48.28, 37.18, "ucrania_russia", listOf("pokrovsk")),
    City("Sumy", 50.91, 34.80, "ucrania_russia", listOf("sumy")),
    City("Lviv", 49.84, 24.03, "ucrania_russia", listOf("lviv", "lvov")),
    City("Kursk", 51.73, 36.19, "ucrania_russia", listOf("kursk")),
    City("Belgorod", 50.60, 36.59, "ucrania_russia", listOf("belgorod")),
    City("Moscou", 55.76, 37.62, "ucrania_russia", listOf("moscow", "moscou", "מוסקבה", "موسكو")),
    City("Sebastopol", 44.60, 33.52, "ucrania_russia", listOf("sevastopol", "sebastopol")),
    // Sudão
    City("Cartum", 15.50, 32.56, "sudao", listOf("khartoum", "cartum", "الخرطوم")),
    City("El Fasher", 13.63, 25.35, "sudao", listOf("el fasher", "el-fasher", "al-fashir", "الفاشر")),
    City("Porto Sudão", 19.62, 37.22, "sudao", listOf("port sudan", "porto sudão", "بورتسودان")),
    // Egito, Jordânia, Golfo e Turquia
    City("Cairo", 30.04, 31.24, "egito", listOf("cairo", "القاهرة", "קהיר")),
    City("Passagem de Rafah", 31.25, 34.26, "egito", listOf("rafah crossing", "passagem de rafah", "معبر رفح")),
    City("Canal de Suez", 30.60, 32.33, "egito", listOf("suez canal", "canal de suez", "قناة السويس")),
    City("Amã", 31.95, 35.93, "jordania", listOf("amman", "amã")),
    City("Riad", 24.71, 46.68, "arabia", listOf("riyadh", "riad", "الرياض")),
    City("Jidá", 21.49, 39.19, "arabia", listOf("jeddah", "jidá", "jidda", "جدة")),
    City("Abu Dhabi", 24.45, 54.38, "emirados", listOf("abu dhabi", "abu dabi", "ابوظبي")),
    City("Dubai", 25.20, 55.27, "emirados", listOf("dubai", "dubái", "دبي")),
    City("Doha", 25.29, 51.53, "golfo", listOf("doha", "الدوحة")),
    City("Base de Al Udeid", 25.12, 51.32, "golfo", listOf("al udeid", "al-udeid", "العديد")),
    City("Manama", 26.23, 50.59, "golfo", listOf("manama", "المنامة")),
    City("Mascate", 23.59, 58.41, "golfo", listOf("muscat", "mascate", "مسقط")),
    City("Ancara", 39.93, 32.86, "turquia", listOf("ankara", "ancara", "انقرة")),
    City("Istambul", 41.01, 28.98, "turquia", listOf("istanbul", "istambul", "اسطنبول")),
    // Somália e Mediterrâneo
    City("Mogadíscio", 2.05, 45.32, "somalia", listOf("mogadishu", "mogadíscio", "mogadiscio", "مقديشو")),
    City("Chipre", 35.13, 33.43, "mediterraneo", listOf("cyprus", "chipre", "קפריסין", "قبرص")),
    City("Lampedusa", 35.51, 12.61, "mediterraneo", listOf("lampedusa")),
    City("Tartus", 34.89, 35.89, "mediterraneo", listOf("tartus", "طرطوس")),
    // ICE e Brasil
    City("Los Angeles", 34.05, -118.24, "ice", listOf("los angeles")),
    City("Chicago", 41.88, -87.63, "ice", listOf("chicago")),
    City("Brasília", -15.79, -47.88, "brasil", listOf("brasília", "brasilia", "itamaraty", "planalto")),
)

private val cityPatterns: List<Pair<City, List<Regex>>> by lazy {
    CITIES.map { c -> c to c.terms.map { Regex("(?<![\\p{L}\\d])" + Regex.escape(normalize(it)) + "(?![\\p{L}\\d])") } }
}

/** Cidades citadas no título/resumo (original e tradução). */
fun Cluster.cities(translated: (String) -> String): List<City> {
    val text = normalize("$title\n$summary\n${translated(title)}\n${translated(summary)}\n" + articles.joinToString("\n") { it.title })
    return cityPatterns.filter { (_, rxs) -> rxs.any { it.containsMatchIn(text) } }.map { it.first }
}
