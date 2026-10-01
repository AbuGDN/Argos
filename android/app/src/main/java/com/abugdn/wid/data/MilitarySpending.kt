package com.abugdn.wid.data

// GERADO por backend/tools/gerar_dados_fixos.py a partir de https://www.sipri.org/sites/default/files/SIPRI-Milex-data-1949-2025_v1.2.xlsx
// (sha256 do arquivo usado: 6cc3a30b1064f9f02e60236667eef82e08cad42910ce630909d004ad2c398a9d)
// Não edite à mão: rode o gerador de novo.

/** Gasto militar de um país em [MILEX_YEAR]: US$ bilhões (valores correntes), % do PIB e variação real
 * em 10 anos (dólares constantes de 2024). [series]: US$ bilhões constantes de [MILEX_FROM] a [MILEX_YEAR]. */
data class MilexCountry(
    val country: String,
    val flag: String,
    val usdBn: Double?,
    val gdpPct: Double?,
    val change10y: Double?,
    val series: List<Double?> = emptyList(),
)

const val MILEX_YEAR = 2025
const val MILEX_FROM = 2000
/** Soma dos países com dado em 2025, US$ bilhões correntes. */
const val MILEX_WORLD_BN = 2857.0

val MILEX_TOP = listOf(
    MilexCountry("EUA", "🇺🇸", 954.4, 3.1, 11.0),
    MilexCountry("China", "🇨🇳", 335.5, 1.7, 71.0),
    MilexCountry("Rússia", "🇷🇺", 190.4, 7.5, 111.0),
    MilexCountry("Alemanha", "🇩🇪", 113.6, 2.3, 128.0),
    MilexCountry("Índia", "🇮🇳", 92.1, 2.3, 53.0),
    MilexCountry("Reino Unido", "🇬🇧", 89.0, 2.4, 32.0),
    MilexCountry("Ucrânia", "🇺🇦", 84.1, 39.6, 1507.0),
    MilexCountry("Arábia Saudita", "🇸🇦", 83.2, 6.5, -20.0),
    MilexCountry("França", "🇫🇷", 68.0, 2.0, 21.0),
    MilexCountry("Japão", "🇯🇵", 62.2, 1.4, 60.0),
    MilexCountry("Israel", "🇮🇱", 48.3, 7.8, 127.0),
    MilexCountry("Itália", "🇮🇹", 48.1, 1.9, 77.0),
    MilexCountry("Coreia do Sul", "🇰🇷", 47.8, 2.6, 34.0),
    MilexCountry("Polônia", "🇵🇱", 46.8, 4.5, 190.0),
    MilexCountry("Espanha", "🇪🇸", 40.2, 2.1, 106.0),
)

val MILEX_GDP_TOP = listOf(
    MilexCountry("Ucrânia", "🇺🇦", 84.1, 39.6, 1507.0),
    MilexCountry("Argélia", "🇩🇿", 25.4, 8.8, 89.0),
    MilexCountry("Israel", "🇮🇱", 48.3, 7.8, 127.0),
    MilexCountry("Rússia", "🇷🇺", 190.4, 7.5, 111.0),
    MilexCountry("Arábia Saudita", "🇸🇦", 83.2, 6.5, -20.0),
    MilexCountry("Azerbaijão", "🇦🇿", 4.9, 6.5, 45.0),
    MilexCountry("Armênia", "🇦🇲", 1.7, 6.1, 142.0),
    MilexCountry("Omã", "🇴🇲", 6.0, 5.7, -28.0),
    MilexCountry("Kuwait", "🇰🇼", 8.1, 4.7, 12.0),
    MilexCountry("Jordânia", "🇯🇴", 2.6, 4.6, 31.0),
    MilexCountry("Polônia", "🇵🇱", 46.8, 4.5, 190.0),
    MilexCountry("Mali", "🇲🇱", 1.0, 3.9, 146.0),
    MilexCountry("Letônia", "🇱🇻", 1.7, 3.6, 299.0),
    MilexCountry("Marrocos", "🇲🇦", 6.3, 3.5, 50.0),
    MilexCountry("Sudão do Sul", "🇸🇸", 0.2, 3.4, -31.0),
)

val MILEX_FOCUS = listOf(
    MilexCountry("Brasil", "🇧🇷", 23.9, 1.1, -4.0, series = listOf(16.36, 18.95, 19.21, 15.32, 15.92, 17.23, 17.84, 19.24, 20.46, 22.31, 24.78, 24.0, 24.46, 24.6, 25.09, 24.51, 23.26, 24.78, 26.42, 25.28, 24.2, 22.92, 21.49, 21.07, 20.96, 23.64)),
    MilexCountry("EUA", "🇺🇸", 954.4, 3.1, 11.0, series = listOf(583.09, 587.82, 660.01, 751.2, 818.75, 856.46, 868.8, 891.99, 956.87, 1032.17, 1061.67, 1049.1, 990.83, 914.62, 858.36, 838.87, 836.29, 827.67, 852.59, 901.03, 943.45, 933.33, 922.55, 943.03, 1004.9, 929.16)),
    MilexCountry("China", "🇨🇳", 335.5, 1.7, 71.0, series = listOf(41.86, 49.63, 57.01, 61.7, 67.96, 74.62, 85.9, 94.42, 103.31, 125.36, 131.54, 141.21, 155.71, 168.37, 181.81, 196.09, 207.38, 220.13, 233.02, 244.38, 255.89, 262.56, 274.16, 292.22, 311.9, 335.02)),
    MilexCountry("Rússia", "🇷🇺", 190.4, 7.5, 111.0, series = listOf(23.74, 25.65, 28.42, 29.8, 31.14, 35.39, 39.17, 42.64, 46.85, 49.16, 50.16, 53.53, 62.02, 65.04, 69.71, 75.13, 80.55, 65.27, 62.81, 65.62, 67.17, 68.5, 88.68, 108.96, 149.4, 158.24)),
    MilexCountry("Ucrânia", "🇺🇦", 84.1, 39.6, 1507.0, series = listOf(2.02, 1.71, 1.81, 2.1, 2.26, 2.74, 3.07, 3.74, 3.66, 3.35, 3.37, 3.1, 3.47, 3.69, 4.42, 4.83, 4.85, 4.68, 5.64, 6.54, 7.26, 6.72, 40.21, 63.34, 64.77, 77.56)),
    MilexCountry("Israel", "🇮🇱", 48.3, 7.8, 127.0, series = listOf(13.71, 14.28, 14.35, 14.07, 14.65, 14.89, 15.16, 16.94, 17.19, 16.85, 16.91, 16.64, 16.95, 17.59, 18.91, 19.25, 19.87, 21.07, 21.33, 21.58, 22.28, 23.08, 22.22, 27.69, 45.92, 43.66)),
    MilexCountry("Irã", "🇮🇷", 7.4, 2.1, 14.0, series = listOf(3.52, 3.92, 4.22, 4.92, 6.36, 7.57, 9.06, 8.33, 8.01, 8.39, 8.53, 7.36, 7.67, 6.17, 6.16, 6.55, 7.51, 8.46, 7.51, 6.29, 6.54, 7.95, 8.36, 8.83, 7.89, 7.45)),
    MilexCountry("Arábia Saudita", "🇸🇦", 83.2, 6.5, -20.0, series = listOf(35.34, 37.64, 33.04, 33.27, 36.92, 44.58, 50.79, 58.5, 57.42, 59.01, 61.41, 62.25, 70.45, 80.73, 95.15, 101.48, 72.62, 80.97, 83.75, 74.25, 70.95, 67.38, 73.8, 79.08, 80.33, 81.47)),
    MilexCountry("Índia", "🇮🇳", 92.1, 2.3, 53.0, series = listOf(32.14, 33.26, 33.16, 33.9, 39.38, 41.91, 42.25, 42.76, 48.5, 57.1, 57.33, 57.86, 57.56, 57.6, 60.41, 60.98, 67.2, 71.94, 74.47, 79.73, 80.21, 79.7, 83.22, 85.23, 85.6, 93.25)),
    MilexCountry("Paquistão", "🇵🇰", 11.9, 2.9, 23.0, series = listOf(5.1, 5.46, 5.87, 6.27, 6.53, 6.8, 6.91, 6.96, 6.56, 6.76, 7.07, 7.45, 7.9, 8.17, 8.6, 9.34, 9.66, 10.58, 11.84, 11.69, 11.5, 11.99, 11.2, 9.78, 10.3, 11.48)),
    MilexCountry("Alemanha", "🇩🇪", 113.6, 2.3, 128.0, series = listOf(48.76, 47.96, 48.09, 47.44, 45.98, 45.27, 44.46, 44.53, 45.58, 47.31, 47.42, 46.46, 47.79, 46.02, 46.08, 46.87, 48.9, 50.31, 51.69, 56.7, 60.42, 59.89, 62.5, 70.2, 86.15, 106.73)),
    MilexCountry("Polônia", "🇵🇱", 46.8, 4.5, 190.0, series = listOf(7.41, 7.65, 7.78, 8.09, 8.48, 9.05, 9.62, 10.87, 9.89, 10.43, 10.93, 11.08, 11.18, 11.09, 12.34, 14.68, 13.87, 14.18, 16.06, 16.33, 18.69, 19.63, 19.88, 29.0, 34.46, 42.53)),
)
