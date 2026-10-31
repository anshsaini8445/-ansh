package com.example.ui.i18n

import java.util.Locale

data class WorldLanguageSpec(
    val code: String,
    val displayName: String,
    val countryFlag: String,
    val regionName: String
)

/**
 * Top 56 World Languages Engine covering 50+ countries globally.
 */
object WorldLanguageEngine {

    val TOP_56_WORLD_LANGUAGES: List<WorldLanguageSpec> = listOf(
        WorldLanguageSpec("auto", "Auto-Detect System Locale (🌐)", "🌐", "Global"),
        WorldLanguageSpec("hi", "हिन्दी (Hindi)", "🇮🇳", "India"),
        WorldLanguageSpec("en", "English (Global)", "🇺🇸", "USA / UK / Global"),
        WorldLanguageSpec("es", "Español (Spanish)", "🇪🇸", "Spain / LatAm"),
        WorldLanguageSpec("ar", "العربية (Arabic)", "🇸🇦", "Saudi / UAE / MENA"),
        WorldLanguageSpec("pt", "Português (Portuguese)", "🇧🇷", "Brazil / Portugal"),
        WorldLanguageSpec("ru", "Русский (Russian)", "🇷🇺", "Russia / CIS"),
        WorldLanguageSpec("ja", "日本語 (Japanese)", "🇯🇵", "Japan"),
        WorldLanguageSpec("ko", "한국어 (Korean)", "🇰🇷", "South Korea"),
        WorldLanguageSpec("de", "Deutsch (German)", "🇩🇪", "Germany / Austria"),
        WorldLanguageSpec("fr", "Français (French)", "🇫🇷", "France / Canada"),
        WorldLanguageSpec("zh-CN", "简体中文 (Chinese Simplified)", "🇨🇳", "China / Singapore"),
        WorldLanguageSpec("zh-TW", "繁體中文 (Chinese Traditional)", "🇹🇼", "Taiwan / HK"),
        WorldLanguageSpec("id", "Bahasa Indonesia", "🇮🇩", "Indonesia"),
        WorldLanguageSpec("tr", "Türkçe (Turkish)", "🇹🇷", "Turkey"),
        WorldLanguageSpec("vi", "Tiếng Việt (Vietnamese)", "🇻🇳", "Vietnam"),
        WorldLanguageSpec("th", "ไทย (Thai)", "🇹🇭", "Thailand"),
        WorldLanguageSpec("bn", "বাংলা (Bengali)", "🇧🇩", "Bangladesh / India"),
        WorldLanguageSpec("ur", "اردو (Urdu)", "🇵🇰", "Pakistan / India"),
        WorldLanguageSpec("ta", "தமிழ் (Tamil)", "🇮🇳", "India / Sri Lanka"),
        WorldLanguageSpec("te", "తెలుగు (Telugu)", "🇮🇳", "India"),
        WorldLanguageSpec("mr", "मराठी (Marathi)", "🇮🇳", "India"),
        WorldLanguageSpec("gu", "ગુજરાતી (Gujarati)", "🇮🇳", "India"),
        WorldLanguageSpec("pa", "ਪੰਜਾਬੀ (Punjabi)", "🇮🇳", "India / Global"),
        WorldLanguageSpec("ml", "മലയാളം (Malayalam)", "🇮🇳", "India"),
        WorldLanguageSpec("kn", "ಕನ್ನಡ (Kannada)", "🇮🇳", "India"),
        WorldLanguageSpec("or", "ଓଡ଼ିଆ (Odia)", "🇮🇳", "India"),
        WorldLanguageSpec("as", "অসমীয়া (Assamese)", "🇮🇳", "India"),
        WorldLanguageSpec("ne", "नेपाली (Nepali)", "🇳🇵", "Nepal"),
        WorldLanguageSpec("si", "සිංහල (Sinhala)", "🇱🇰", "Sri Lanka"),
        WorldLanguageSpec("it", "Italiano (Italian)", "🇮🇹", "Italy"),
        WorldLanguageSpec("pl", "Polski (Polish)", "🇵🇱", "Poland"),
        WorldLanguageSpec("nl", "Nederlands (Dutch)", "🇳🇱", "Netherlands / Belgium"),
        WorldLanguageSpec("uk", "Українська (Ukrainian)", "🇺🇦", "Ukraine"),
        WorldLanguageSpec("fa", "فارسی (Persian / Farsi)", "🇮🇷", "Iran"),
        WorldLanguageSpec("ms", "Bahasa Melayu (Malay)", "🇲🇾", "Malaysia"),
        WorldLanguageSpec("tl", "Filipino (Tagalog)", "🇵🇭", "Philippines"),
        WorldLanguageSpec("sw", "Kiswahili (Swahili)", "🇰🇪", "Kenya / Tanzania"),
        WorldLanguageSpec("ro", "Română (Romanian)", "🇷🇴", "Romania"),
        WorldLanguageSpec("el", "Ελληνικά (Greek)", "🇬🇷", "Greece"),
        WorldLanguageSpec("cs", "Čeština (Czech)", "🇨🇿", "Czechia"),
        WorldLanguageSpec("sv", "Svenska (Swedish)", "🇸🇪", "Sweden"),
        WorldLanguageSpec("hu", "Magyar (Hungarian)", "🇭🇺", "Hungary"),
        WorldLanguageSpec("he", "עברית (Hebrew)", "🇮🇱", "Israel"),
        WorldLanguageSpec("da", "Dansk (Danish)", "🇩🇰", "Denmark"),
        WorldLanguageSpec("fi", "Suomi (Finnish)", "🇫🇮", "Finland"),
        WorldLanguageSpec("no", "Norsk (Norwegian)", "🇳🇴", "Norway"),
        WorldLanguageSpec("sk", "Slovenčina (Slovak)", "🇸🇰", "Slovakia"),
        WorldLanguageSpec("bg", "Български (Bulgarian)", "🇧🇬", "Bulgaria"),
        WorldLanguageSpec("hr", "Hrvatski (Croatian)", "🇭🇷", "Croatia"),
        WorldLanguageSpec("sr", "Српски (Serbian)", "🇷🇸", "Serbia"),
        WorldLanguageSpec("uz", "Oʻzbekcha (Uzbek)", "🇺🇿", "Uzbekistan"),
        WorldLanguageSpec("kk", "Қазақша (Kazakh)", "🇰🇿", "Kazakhstan"),
        WorldLanguageSpec("az", "Azərbaycanca (Azerbaijani)", "🇦🇿", "Azerbaijan"),
        WorldLanguageSpec("my", "မြန်မာဘာသာ (Burmese)", "🇲🇲", "Myanmar"),
        WorldLanguageSpec("km", "ភាសាខ្មែរ (Khmer)", "🇰🇭", "Cambodia"),
        WorldLanguageSpec("am", "አማርኛ (Amharic)", "🇪🇹", "Ethiopia")
    )

    val DISPLAY_NAMES: List<String> = TOP_56_WORLD_LANGUAGES.map { "${it.countryFlag} ${it.displayName}" }

    fun resolveEffectiveLanguageCode(selectedDisplay: String): String {
        val found = TOP_56_WORLD_LANGUAGES.find { selectedDisplay.contains(it.displayName) || selectedDisplay.contains(it.code) }
        if (found != null && found.code != "auto") return found.code
        return Locale.getDefault().language.lowercase(Locale.US)
    }
}
