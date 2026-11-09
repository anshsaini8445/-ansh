package com.example.ui.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

data class WorldLanguageSpec(
    val code: String,
    val displayName: String,
    val countryFlag: String,
    val regionName: String
)

/**
 * 25+ Real Working App Languages Engine (Indian + Global Languages)
 * Starts with System Default (Auto) on install, and allows instant live switching
 * across all 25 languages throughout Video, Music, Search, Me, Share, and Player screens.
 */
object WorldLanguageEngine {

    const val SYSTEM_DEFAULT_LABEL = "🌐 System Default (Auto)"

    val SUPPORTED_LANGUAGE_SPECS: List<WorldLanguageSpec> = listOf(
        WorldLanguageSpec("auto", "System Default (Auto)", "🌐", "Follows Phone System Language • फ़ोन की भाषा"),
        WorldLanguageSpec("hi", "हिन्दी (Hindi)", "🇮🇳", "भारत • पूर्ण हिन्दी इंटरफ़ेस"),
        WorldLanguageSpec("en", "English", "🇺🇸", "Global • Full English Interface"),
        WorldLanguageSpec("hinglish", "Hinglish (हिंग्लिश)", "🇮🇳", "India • Sabhi Features Hinglish Mein"),
        WorldLanguageSpec("mr", "मराठी (Marathi)", "🇮🇳", "महाराष्ट्र • संपूर्ण मराठी इंटरफेस"),
        WorldLanguageSpec("bn", "বাংলা (Bengali)", "🇮🇳", "পশ্চিমবঙ্গ ও বাংলাদেশ • সম্পূর্ণ বাংলা"),
        WorldLanguageSpec("ta", "தமிழ் (Tamil)", "🇮🇳", "தமிழ்நாடு • முழு தமிழ் இடைமுகம்"),
        WorldLanguageSpec("te", "తెలుగు (Telugu)", "🇮🇳", "ఆంధ్రప్రదేశ్ & తెలంగాణ • పూర్తి తెలుగు"),
        WorldLanguageSpec("gu", "ગુજરાતી (Gujarati)", "🇮🇳", "ગુજરાત • સંપૂર્ણ ગુજરાતી ઇન્ટરફેસ"),
        WorldLanguageSpec("pa", "ਪੰਜਾਬੀ (Punjabi)", "🇮🇳", "ਪੰਜਾਬ • ਪੂਰਾ ਪੰਜਾਬੀ ਇੰਟਰਫੇਸ"),
        WorldLanguageSpec("kn", "ಕನ್ನಡ (Kannada)", "🇮🇳", "ಕರ್ನಾಟಕ • ಸಂಪೂರ್ಣ ಕನ್ನಡ ಇಂಟರ್ಫೇಸ್"),
        WorldLanguageSpec("ml", "മലയാളം (Malayalam)", "🇮🇳", "കേരളം • പൂർണ്ണ മലയാളം ഇന്റർഫേസ്"),
        WorldLanguageSpec("or", "ଓଡ଼ିଆ (Odia)", "🇮🇳", "ଓଡ଼ିଶା • ସମ୍ପୂର୍ଣ୍ଣ ଓଡ଼ିଆ ଇଣ୍ଟରଫେସ୍"),
        WorldLanguageSpec("ur", "اردو (Urdu)", "🇮🇳", "بھارت و پاکستان • مکمل اردو انٹرفیس"),
        WorldLanguageSpec("bho", "भोजपुरी (Bhojpuri)", "🇮🇳", "बिहार आ यूपी • पूरा भोजपुरी इंटरफेस"),
        WorldLanguageSpec("ne", "नेपाली (Nepali)", "🇳🇵", "नेपाल र भारत • पूर्ण नेपाली इन्टरफेस"),
        WorldLanguageSpec("ar", "العربية (Arabic)", "🇸🇦", "الشرق الأوسط • واجهة عربية كاملة"),
        WorldLanguageSpec("es", "Español (Spanish)", "🇪🇸", "España y Latinoamérica • Interfaz en Español"),
        WorldLanguageSpec("fr", "Français (French)", "🇫🇷", "France & Monde • Interface en Français"),
        WorldLanguageSpec("pt", "Português (Portuguese)", "🇵🇹", "Brasil e Portugal • Interface em Português"),
        WorldLanguageSpec("ru", "Русский (Russian)", "🇷🇺", "Россия и СНГ • Полный русский интерфейс"),
        WorldLanguageSpec("de", "Deutsch (German)", "🇩🇪", "Deutschland • Vollständige deutsche Oberfläche"),
        WorldLanguageSpec("ja", "日本語 (Japanese)", "🇯🇵", "日本 • 完全な日本語インターフェース"),
        WorldLanguageSpec("ko", "한국어 (Korean)", "🇰🇷", "대한민국 • 한국어 인터페이스"),
        WorldLanguageSpec("zh", "中文 (Chinese)", "🇨🇳", "中国 • 完整中文界面"),
        WorldLanguageSpec("id", "Bahasa Indonesia", "🇮🇩", "Indonesia • Antarmuka Bahasa Indonesia")
    )

    val TOP_56_WORLD_LANGUAGES: List<WorldLanguageSpec> = SUPPORTED_LANGUAGE_SPECS

    val DISPLAY_NAMES: List<String> = SUPPORTED_LANGUAGE_SPECS.map { spec ->
        "${spec.countryFlag} ${spec.displayName}"
    }

    private val DISPLAY_NAME_SET: Set<String> = DISPLAY_NAMES.toSet()
    private val REGION_SUBTITLE_SET: Set<String> = SUPPORTED_LANGUAGE_SPECS.map { it.regionName }.toSet()

    // Reactive Compose state so every screen's Text composable updates immediately on language change
    var activeLanguageState by mutableStateOf(SYSTEM_DEFAULT_LABEL)

    fun resolveEffectiveLanguageCode(selectedDisplay: String): String {
        val clean = selectedDisplay.trim()
        if (clean.isEmpty() || clean.contains("System Default", ignoreCase = true) || clean.equals("auto", ignoreCase = true)) {
            val sysLang = Locale.getDefault().language.lowercase(Locale.ROOT)
            return if (SUPPORTED_LANGUAGE_SPECS.any { it.code == sysLang }) sysLang else "en"
        }
        val matched = SUPPORTED_LANGUAGE_SPECS.firstOrNull { spec ->
            clean.equals(spec.code, ignoreCase = true) ||
                clean.contains(spec.displayName, ignoreCase = true) ||
                "${spec.countryFlag} ${spec.displayName}" == clean
        }
        if (matched != null && matched.code != "auto") {
            return matched.code
        }
        return when {
            clean.contains("Hinglish", ignoreCase = true) || clean.contains("हिंग्लिश") -> "hinglish"
            clean.contains("Hindi", ignoreCase = true) || clean.contains("हिन्दी") -> "hi"
            clean.contains("Marathi", ignoreCase = true) || clean.contains("मराठी") -> "mr"
            clean.contains("Bengali", ignoreCase = true) || clean.contains("বাংলা") -> "bn"
            clean.contains("Tamil", ignoreCase = true) || clean.contains("தமிழ்") -> "ta"
            clean.contains("Telugu", ignoreCase = true) || clean.contains("తెలుగు") -> "te"
            clean.contains("Gujarati", ignoreCase = true) || clean.contains("ગુજરાતી") -> "gu"
            clean.contains("Punjabi", ignoreCase = true) || clean.contains("ਪੰਜਾਬੀ") -> "pa"
            clean.contains("Kannada", ignoreCase = true) || clean.contains("ಕನ್ನಡ") -> "kn"
            clean.contains("Malayalam", ignoreCase = true) || clean.contains("മലയാളം") -> "ml"
            clean.contains("Odia", ignoreCase = true) || clean.contains("ଓଡ଼ିଆ") -> "or"
            clean.contains("Urdu", ignoreCase = true) || clean.contains("اردو") -> "ur"
            clean.contains("Bhojpuri", ignoreCase = true) || clean.contains("भोजपुरी") -> "bho"
            clean.contains("Nepali", ignoreCase = true) || clean.contains("नेपाली") -> "ne"
            clean.contains("Arabic", ignoreCase = true) || clean.contains("العربية") -> "ar"
            clean.contains("Spanish", ignoreCase = true) || clean.contains("Español", ignoreCase = true) -> "es"
            clean.contains("French", ignoreCase = true) || clean.contains("Français", ignoreCase = true) -> "fr"
            clean.contains("Portuguese", ignoreCase = true) || clean.contains("Português", ignoreCase = true) -> "pt"
            clean.contains("Russian", ignoreCase = true) || clean.contains("Русский", ignoreCase = true) -> "ru"
            clean.contains("German", ignoreCase = true) || clean.contains("Deutsch", ignoreCase = true) -> "de"
            clean.contains("Japanese", ignoreCase = true) || clean.contains("日本語") -> "ja"
            clean.contains("Korean", ignoreCase = true) || clean.contains("한국어") -> "ko"
            clean.contains("Chinese", ignoreCase = true) || clean.contains("中文") -> "zh"
            clean.contains("Indonesia", ignoreCase = true) -> "id"
            else -> "en"
        }
    }

    fun isHindiActive(selectedDisplay: String): Boolean {
        return resolveEffectiveLanguageCode(selectedDisplay) == "hi"
    }

    fun getSpecForDisplay(display: String): WorldLanguageSpec {
        return SUPPORTED_LANGUAGE_SPECS.firstOrNull {
            "${it.countryFlag} ${it.displayName}" == display || display.contains(it.displayName, ignoreCase = true)
        } ?: SUPPORTED_LANGUAGE_SPECS.first()
    }

    fun getLanguageActivatedToast(selectedDisplay: String): String {
        val code = resolveEffectiveLanguageCode(selectedDisplay)
        return when (code) {
            "hi" -> "भाषा सेट हो गई: $selectedDisplay"
            "hinglish" -> "App language set ho gayi: $selectedDisplay"
            "mr" -> "भाषा सेट केली: $selectedDisplay"
            "bn" -> "ভাষা সেট করা হয়েছে: $selectedDisplay"
            "ta" -> "மொழி மாற்றப்பட்டது: $selectedDisplay"
            "te" -> "భాష సెట్ చేయబడింది: $selectedDisplay"
            "gu" -> "ભાષા સેટ થઈ ગઈ: $selectedDisplay"
            "pa" -> "ਭਾਸ਼ਾ ਸੈੱਟ ਕੀਤੀ ਗਈ: $selectedDisplay"
            "kn" -> "ಭಾಷೆ ಹೊಂದಿಸಲಾಗಿದೆ: $selectedDisplay"
            "ml" -> "ഭാഷ ക്രമീകരിച്ചു: $selectedDisplay"
            "or" -> "ଭାଷା ସେଟ୍ ହେଲା: $selectedDisplay"
            "ur" -> "زبان سیٹ ہو گئی: $selectedDisplay"
            "bho" -> "भाषा सेट हो गइल: $selectedDisplay"
            "ne" -> "भाषा सेट गरियो: $selectedDisplay"
            "ar" -> "تم تعيين اللغة: $selectedDisplay"
            "es" -> "Idioma configurado: $selectedDisplay"
            "fr" -> "Langue définie : $selectedDisplay"
            "pt" -> "Idioma definido: $selectedDisplay"
            "ru" -> "Язык установлен: $selectedDisplay"
            "de" -> "Sprache eingestellt: $selectedDisplay"
            "ja" -> "言語を設定しました: $selectedDisplay"
            "ko" -> "언어가 설정되었습니다: $selectedDisplay"
            "zh" -> "语言已设置为: $selectedDisplay"
            "id" -> "Bahasa diatur ke: $selectedDisplay"
            else -> "Language set to: $selectedDisplay"
        }
    }

    /**
     * Translates UI text into the active language out of all 25 supported languages.
     * Preserves language picker labels, media filenames, URLs, and timecodes untouched.
     */
    fun translateUiText(rawText: String, selectedDisplay: String = activeLanguageState): String {
        if (rawText.isBlank() || rawText.length > 260) return rawText
        // Preserve language picker rows so every language option always shows its own native title
        if (DISPLAY_NAME_SET.contains(rawText) || REGION_SUBTITLE_SET.contains(rawText.removePrefix("✓ ").trim())) {
            return rawText
        }
        // Skip URLs, file paths, or pure numbers/timecodes
        if (rawText.startsWith("http://") || rawText.startsWith("https://") || rawText.startsWith("/storage/")) {
            return rawText
        }

        val code = resolveEffectiveLanguageCode(selectedDisplay)
        if (code == "en") return rawText

        val dictionary = LANGUAGE_DICTIONARIES[code] ?: return rawText

        // 1. Fast Exact Match
        dictionary[rawText]?.let { return it }

        // 2. Sorted Phrase & Word Replacement (Longest phrases first)
        var result = rawText
        val orderedRules = COMPILED_PATTERNS.getOrPut(code) {
            dictionary.entries
                .sortedByDescending { it.key.length }
                .map { (enPhrase, translated) ->
                    val escaped = Regex.escape(enPhrase)
                    val regex = if (enPhrase.first().isLetterOrDigit() && enPhrase.last().isLetterOrDigit()) {
                        Regex("\\b$escaped\\b", RegexOption.IGNORE_CASE)
                    } else {
                        Regex(escaped, RegexOption.IGNORE_CASE)
                    }
                    regex to translated
                }
        }

        for ((regex, replacement) in orderedRules) {
            if (regex.containsMatchIn(result)) {
                result = regex.replace(result, replacement)
            }
        }
        return result
    }

    private val COMPILED_PATTERNS = mutableMapOf<String, List<Pair<Regex, String>>>()

    private fun buildDict(
        video: String, videos: String, music: String, audio: String, songs: String, song: String,
        search: String, me: String, folders: String, folder: String, playlists: String, history: String,
        favourites: String, albums: String, artists: String, all: String,
        hybridShare: String, sendReceiveQr: String, privateVault: String, pinLockedMedia: String,
        themes: String, visualizer3d: String, mascots: String, mascotGalleryTitle: String,
        savedActive: String, size: String, language: String, pdfReader: String, fullScreenZoom: String,
        videoToAudio: String, extractMp3: String, storageCleaner: String, duplicateJunk: String,
        networkStream: String, statusSaver: String, equalizer: String, sleepTimer: String, recycleBin: String,
        save: String, saved: String, cancel: String, close: String, delete: String, rename: String,
        details: String, share: String, send: String, receive: String, scan: String, connect: String,
        paste: String, clear: String, selectAll: String, selected: String, sort: String, refresh: String,
        play: String, pause: String, speed: String, volume: String, subtitles: String, loop: String,
        shuffle: String, next: String, previous: String, backgroundAudio: String, lyrics: String,
        settings: String, active: String, empty: String, live: String, camera: String,
        wallpaper: String, gallery: String, custom: String, defaultWord: String, back: String
    ): Map<String, String> = mapOf(
        "3D Lion, Pets & Teddy Mascots" to mascotGalleryTitle,
        "Send & Receive QR" to sendReceiveQr,
        "PIN Locked Media" to pinLockedMedia,
        "Full-Screen Zoom" to fullScreenZoom,
        "Extract MP3 Audio" to extractMp3,
        "Duplicate & Junk" to duplicateJunk,
        "Video to Audio" to videoToAudio,
        "Storage Cleaner" to storageCleaner,
        "Private Vault" to privateVault,
        "Private Folder" to privateVault,
        "Hybrid Share" to hybridShare,
        "Network Stream" to networkStream,
        "Status Saver" to statusSaver,
        "Sleep Timer" to sleepTimer,
        "Recycle Bin" to recycleBin,
        "PDF Reader" to pdfReader,
        "Saved Active" to savedActive,
        "Background Audio" to backgroundAudio,
        "BG Audio" to backgroundAudio,
        "Select All" to selectAll,
        "3D Visua..." to visualizer3d,
        "3D Visualizer" to visualizer3d,
        "SAVED ✓" to "$saved ✓",
        "Saved" to saved,
        "SAVE" to save,
        "Save" to save,
        "Videos" to videos,
        "Video" to video,
        "Music" to music,
        "Audio" to audio,
        "Songs" to songs,
        "Song" to song,
        "Search" to search,
        "Me" to me,
        "Folders" to folders,
        "Folder" to folder,
        "Playlists" to playlists,
        "History" to history,
        "Favourites" to favourites,
        "Favorites" to favourites,
        "Favourite" to favourites,
        "Albums" to albums,
        "Artists" to artists,
        "All" to all,
        "Themes" to themes,
        "Mascots" to mascots,
        "Size" to size,
        "Language" to language,
        "Equalizer" to equalizer,
        "Cancel" to cancel,
        "Close" to close,
        "Delete" to delete,
        "Rename" to rename,
        "Details" to details,
        "Share" to share,
        "Send" to send,
        "Receive" to receive,
        "Scan" to scan,
        "Connect" to connect,
        "Paste" to paste,
        "Clear" to clear,
        "Selected" to selected,
        "Sort" to sort,
        "Refresh" to refresh,
        "Play" to play,
        "Pause" to pause,
        "Speed" to speed,
        "Volume" to volume,
        "Subtitles" to subtitles,
        "Loop" to loop,
        "Shuffle" to shuffle,
        "Next" to next,
        "Previous" to previous,
        "Lyrics" to lyrics,
        "Settings" to settings,
        "Active" to active,
        "Empty" to empty,
        "Live" to live,
        "Camera" to camera,
        "Wallpaper" to wallpaper,
        "Gallery" to gallery,
        "Custom" to custom,
        "Default" to defaultWord,
        "Back" to back
    )

    private val LANGUAGE_DICTIONARIES: Map<String, Map<String, String>> = mapOf(
        "hi" to buildDict(
            video = "वीडियो", videos = "वीडियो", music = "संगीत", audio = "ऑडियो", songs = "गाने", song = "गाना",
            search = "खोजें", me = "मेरा", folders = "फ़ोल्डर", folder = "फ़ोल्डर", playlists = "प्लेलिस्ट", history = "इतिहास",
            favourites = "पसंदीदा", albums = "एल्बम", artists = "कलाकार", all = "सभी",
            hybridShare = "हाइब्रिड शेयर", sendReceiveQr = "QR से भेजें और पाएं", privateVault = "प्राइवेट तिजोरी", pinLockedMedia = "पिन लॉक मीडिया",
            themes = "थीम्स", visualizer3d = "3D विज़ुअलाइज़र", mascots = "मैस्कॉट्स", mascotGalleryTitle = "3D शेर, पेट्स और टेडी मैस्कॉट्स",
            savedActive = "सक्रिय मैस्कॉट", size = "आकार", language = "भाषा", pdfReader = "पीडीएफ रीडर", fullScreenZoom = "फुल-स्क्रीन ज़ूम",
            videoToAudio = "वीडियो से ऑडियो", extractMp3 = "MP3 ऑडियो निकालें", storageCleaner = "स्टोरेज क्लीनर", duplicateJunk = "डुप्लीकेट और जंक",
            networkStream = "नेटवर्क स्ट्रीम", statusSaver = "स्टेटस सेवर", equalizer = "इक्वलाइज़र", sleepTimer = "स्लीप टाइमर", recycleBin = "रीसायकल बिन",
            save = "सेव", saved = "सेव है", cancel = "रद्द करें", close = "बंद करें", delete = "हटाएं", rename = "नाम बदलें",
            details = "जानकारी", share = "शेयर", send = "भेजें", receive = "प्राप्त करें", scan = "स्कैन", connect = "कनेक्ट",
            paste = "पेस्ट", clear = "साफ़ करें", selectAll = "सभी चुनें", selected = "चुना गया", sort = "क्रम", refresh = "रीफ़्रेश",
            play = "चलाएं", pause = "रोकें", speed = "गति", volume = "आवाज़", subtitles = "सबटाइटल", loop = "लूप",
            shuffle = "शफ़ल", next = "अगला", previous = "पिछला", backgroundAudio = "बैकग्राउंड ऑडियो", lyrics = "बोल (Lyrics)",
            settings = "सेटिंग्स", active = "सक्रिय", empty = "खाली", live = "लाइव", camera = "कैमरा",
            wallpaper = "वॉलपेपर", gallery = "गैलरी", custom = "कस्टम", defaultWord = "डिफ़ॉल्ट", back = "वापस"
        ),
        "hinglish" to buildDict(
            video = "Video", videos = "Videos", music = "Gaane", audio = "Audio", songs = "Gaane", song = "Gaana",
            search = "Khojein", me = "Apna Hub", folders = "Folders", folder = "Folder", playlists = "Playlists", history = "Purana History",
            favourites = "Pasandida", albums = "Albums", artists = "Kalakar", all = "Sabhi",
            hybridShare = "Tez Hybrid Share", sendReceiveQr = "Bhejein & Paayein QR", privateVault = "Secret Tijori", pinLockedMedia = "PIN Se Chhupayein",
            themes = "Rang & Themes", visualizer3d = "3D DJ Light", mascots = "3D Gudde", mascotGalleryTitle = "3D Sher, Puppy & Teddy Gudde",
            savedActive = "Abhi Chalu Gudda", size = "Size", language = "Bhasha", pdfReader = "PDF Padhein", fullScreenZoom = "Full Zoom View",
            videoToAudio = "Video Se MP3", extractMp3 = "MP3 Gaana Nikalein", storageCleaner = "Phone Safai", duplicateJunk = "Kachra & Duplicate",
            networkStream = "Online Stream", statusSaver = "WA Status Save", equalizer = "Bass & DJ Sound", sleepTimer = "Neend Timer", recycleBin = "Kachra पेटी (Bin)",
            save = "Lagayein", saved = "Laga Hua", cancel = "Radd Karein", close = "Band Karein", delete = "Hatayein", rename = "Naam Badlein",
            details = "Jankari", share = "Bhejein", send = "Bhejein", receive = "Lein (Receive)", scan = "Scan Karein", connect = "Jodein",
            paste = "Chipkayein", clear = "Saaf Karein", selectAll = "Sab Chunein", selected = "Chuna Gaya", sort = "Sajayein", refresh = "Taza Karein",
            play = "Chalayein", pause = "Rokein", speed = "Speed", volume = "Awaaz", subtitles = "Subtitles", loop = "Baar-Baar",
            shuffle = "Mix Shuffle", next = "Agla", previous = "Pichhla", backgroundAudio = "Background Gaana", lyrics = "Gaane Ke Bol",
            settings = "Settings", active = "Chalu", empty = "Khali", live = "Live", camera = "Camera",
            wallpaper = "Apna Photo", gallery = "Gallery", custom = "Apna", defaultWord = "Default", back = "Wapas"
        ),
        "mr" to buildDict(
            video = "व्हिडिओ", videos = "व्हिडिओ", music = "संगीत", audio = "ऑडिओ", songs = "गाणी", song = "गाणे",
            search = "शोधा", me = "माझे", folders = "फोल्डर्स", folder = "फोल्डर", playlists = "प्लेलिस्ट", history = "इतिहास",
            favourites = "आवडते", albums = "अल्बम", artists = "कलाकार", all = "सर्व",
            hybridShare = "हायब्रिड शेअर", sendReceiveQr = "QR पाठवा आणि मिळवा", privateVault = "खाजगी तिजोरी", pinLockedMedia = "पिन लॉक मीडिया",
            themes = "थीम्स", visualizer3d = "3D व्हिज्युअलायझर", mascots = "मॅस्कॉट्स", mascotGalleryTitle = "3D सिंह, पाळीव प्राणी आणि टेडी",
            savedActive = "सक्रिय मॅस्कॉट", size = "आकार", language = "भाषा", pdfReader = "पीडीएफ रीडर", fullScreenZoom = "फुल-स्क्रीन झूम",
            videoToAudio = "व्हिडिओ ते ऑडिओ", extractMp3 = "MP3 ऑडिओ काढा", storageCleaner = "स्टोरेज क्लीनर", duplicateJunk = "डुप्लिकेट आणि जंक",
            networkStream = "नेटवर्क स्ट्रीम", statusSaver = "स्टेटस सेव्हर", equalizer = "इक्वलायझर", sleepTimer = "स्लीप टायमर", recycleBin = "रिसायकल बिन",
            save = "जतन करा", saved = "जतन केले", cancel = "रद्द करा", close = "बंद करा", delete = "हटवा", rename = "नाव बदला",
            details = "तपशील", share = "शेअर करा", send = "पाठवा", receive = "स्वीकारा", scan = "स्कॅन करा", connect = "जोडा",
            paste = "पेस्ट करा", clear = "साफ करा", selectAll = "सर्व निवडा", selected = "निवडलेले", sort = "क्रमवारी", refresh = "रिफ्रेश",
            play = "चालवा", pause = "थांबवा", speed = "वेग", volume = "आवाज", subtitles = "सबटायटल", loop = "लूप",
            shuffle = "शफल", next = "पुढील", previous = "मागील", backgroundAudio = "बॅकग्राउंड ऑडिओ", lyrics = "गाण्याचे बोल",
            settings = "सेटिंग्ज", active = "सक्रिय", empty = "रिकामे", live = "थेट (Live)", camera = "कॅमेरा",
            wallpaper = "वॉलपेपर", gallery = "गॅलरी", custom = "सानुकूल", defaultWord = "डिफॉल्ट", back = "मागे"
        ),
        "bn" to buildDict(
            video = "ভিডিও", videos = "ভিডিও", music = "সঙ্গীত", audio = "অডিও", songs = "গান", song = "গান",
            search = "খুঁজুন", me = "আমার", folders = "ফোল্ডার", folder = "ফোল্ডার", playlists = "প্লেলিস্ট", history = "ইতিহাস",
            favourites = "প্রিয়", albums = "অ্যালবাম", artists = "শিল্পী", all = "সব",
            hybridShare = "হাইব্রিড শেয়ার", sendReceiveQr = "QR পাঠান ও গ্রহণ করুন", privateVault = "প্রাইভেট ভল্ট", pinLockedMedia = "পিন লক মিডিয়া",
            themes = "থিম", visualizer3d = "3D ভিজ্যুয়ালাইজার", mascots = "ম্যাসকট", mascotGalleryTitle = "3D সিংহ, পেট ও টেডি ম্যাসকট",
            savedActive = "সক্রিয় ম্যাসকট", size = "আকার", language = "ভাষা", pdfReader = "পিডিএফ রিডার", fullScreenZoom = "ফুল-স্ক্রিন জুম",
            videoToAudio = "ভিডিও থেকে অডিও", extractMp3 = "MP3 অডিও বের করুন", storageCleaner = "স্টোরেজ ক্লিনার", duplicateJunk = "ডুপ্লিকেট ও জাঙ্ক",
            networkStream = "নেটওয়ার্ক স্ট্রিম", statusSaver = "স্ট্যাটাস সেভার", equalizer = "ইকুয়ালাইজার", sleepTimer = "স্লিপ টাইমার", recycleBin = "রিসাইকেল বিন",
            save = "সেভ করুন", saved = "সেভ করা", cancel = "বাতিল", close = "বন্ধ করুন", delete = "মুছুন", rename = "নাম বদলান",
            details = "বিস্তারিত", share = "শেয়ার", send = "পাঠান", receive = "গ্রহণ করুন", scan = "স্ক্যান", connect = "সংযোগ",
            paste = "পেস্ট", clear = "মুছে ফেলুন", selectAll = "সব নির্বাচন", selected = "নির্বাচিত", sort = "সাজান", refresh = "রিফ্রেশ",
            play = "চালান", pause = "থামান", speed = "গতি", volume = "শব্দ", subtitles = "সাবটাইটেল", loop = "লুপ",
            shuffle = "শাফল", next = "পরবর্তী", previous = "পূর্ববর্তী", backgroundAudio = "ব্যাকগ্রাউন্ড অডিও", lyrics = "গানের কথা",
            settings = "সেটিংস", active = "সক্রিয়", empty = "খালি", live = "লাইভ", camera = "ক্যামেরা",
            wallpaper = "ওয়ালপেপার", gallery = "গ্যালারি", custom = "কাস্টম", defaultWord = "ডিফল্ট", back = "পিছনে"
        ),
        "ta" to buildDict(
            video = "வீடியோ", videos = "வீடியோக்கள்", music = "இசை", audio = "ஆடியோ", songs = "பாடல்கள்", song = "பாடல்",
            search = "தேடு", me = "நான்", folders = "கோப்புறைகள்", folder = "கோப்புறை", playlists = "பிளேலிஸ்ட்", history = "வரலாறு",
            favourites = "பிடித்தவை", albums = "ஆல்பங்கள்", artists = "கலைஞர்கள்", all = "அனைத்தும்",
            hybridShare = "ஹைப்ரிட் பகிர்வு", sendReceiveQr = "QR அனுப்பு & பெறு", privateVault = "தனிப்பட்ட பெட்டகம்", pinLockedMedia = "PIN பூட்டிய மீடியா",
            themes = "தீம்கள்", visualizer3d = "3D விஷுவலைசர்", mascots = "பொம்மைகள்", mascotGalleryTitle = "3D சிங்கம் & டெடி பொம்மைகள்",
            savedActive = "செயலில் உள்ள பொம்மை", size = "அளவு", language = "மொழி", pdfReader = "PDF ரீடர்", fullScreenZoom = "முழுத்திரை ஜூம்",
            videoToAudio = "வீடியோ -> ஆடியோ", extractMp3 = "MP3 ஆடியோ எடு", storageCleaner = "स्टोरेज கிளீனர்", duplicateJunk = "நகல் & குப்பை",
            networkStream = "நெட்வொர்க் ஸ்ட்ரீம்", statusSaver = "ஸ்டேட்டஸ் சேவர்", equalizer = "ஈக்வலைசர்", sleepTimer = "ஸ்லீப் டைமர்", recycleBin = "மறுசுழற்சி தொட்டி",
            save = "சேமி", saved = "சேமிக்கப்பட்டது", cancel = "ரத்து", close = "மூடு", delete = "நீக்கு", rename = "பெயர் மாற்று",
            details = "விவரங்கள்", share = "பகிர்", send = "அனுப்பு", receive = "பெறு", scan = "ஸ்கேன்", connect = "இணை",
            paste = "ஒட்டு", clear = "அழி", selectAll = "அனைத்தும் தேர்ந்தெடு", selected = "தேர்ந்தெடுக்கப்பட்டது", sort = "வரிசை", refresh = "புதுப்பி",
            play = "இயக்கு", pause = "நிறுத்து", speed = "வேகம்", volume = "ஒலி", subtitles = "வசனம்", loop = "சுழற்சி",
            shuffle = "கலப்பு", next = "அடுத்தது", previous = "முந்தையது", backgroundAudio = "பின்னணி ஆடியோ", lyrics = "பாடல் வரிகள்",
            settings = "அமைப்புகள்", active = "செயலில்", empty = "காலி", live = "நேரலை", camera = "கேமரா",
            wallpaper = "வால்பேப்பர்", gallery = "கேலரி", custom = "தனிப்பயன்", defaultWord = "இயல்புநிலை", back = "பின்னால்"
        ),
        "te" to buildDict(
            video = "వీడియో", videos = "వీడియోలు", music = "సంగీతం", audio = "ఆడియో", songs = "పాటలు", song = "పాట",
            search = "వెతకండి", me = "నా విభాగం", folders = "ఫోల్డర్లు", folder = "ఫోల్డర్", playlists = "ప్లేజాబితాలు", history = "చరిత్ర",
            favourites = "ఇష్టమైనవి", albums = "ఆల్బమ్‌లు", artists = "కళాకారులు", all = "అన్నీ",
            hybridShare = "హైబ్రిడ్ షేర్", sendReceiveQr = "QR పంపండి & పొందండి", privateVault = "ప్రైవేట్ వాల్ట్", pinLockedMedia = "PIN లాక్ మీడియా",
            themes = "థీమ్‌లు", visualizer3d = "3D విజువలైజర్", mascots = "మాస్కాట్‌లు", mascotGalleryTitle = "3D సింహం & టెడ్డీ బొమ్మలు",
            savedActive = "యాక్టివ్ మాస్కాట్", size = "పరిమాణం", language = "భాష", pdfReader = "PDF రీడర్", fullScreenZoom = "ఫుల్-స్క్రీన్ జూమ్",
            videoToAudio = "వీడియో నుండి ఆడియో", extractMp3 = "MP3 ఆడియో తీయండి", storageCleaner = "స్టోరేజ్ క్లీనర్", duplicateJunk = "డూప్లికేట్ & జంక్",
            networkStream = "నెట్‌వర్క్ స్ట్రీమ్", statusSaver = "స్టేటస్ సేవర్", equalizer = "ఈక్వలైజర్", sleepTimer = "స్లీప్ టైమర్", recycleBin = "రీసైకిల్ బిన్",
            save = "సేవ్", saved = "సేవ్ చేయబడింది", cancel = "రద్దు", close = "మూసివేయి", delete = "తొలగించు", rename = "పేరు మార్చు",
            details = "వివరాలు", share = "షేర్", send = "పంపు", receive = "స్వీకరించు", scan = "స్కాన్", connect = "కనెక్ట్",
            paste = "పేస్ట్", clear = "క్లియర్", selectAll = "అన్నీ ఎంచుకోండి", selected = "ఎంచుకోబడింది", sort = "క్రమం", refresh = "రిఫ్రెష్",
            play = "ప్లే", pause = "పాజ్", speed = "వేగం", volume = "వాల్యూమ్", subtitles = "సబ్‌టైటిల్స్", loop = "లూప్",
            shuffle = "షఫుల్", next = "తదుపరి", previous = "మునుపటి", backgroundAudio = "బ్యాక్‌గ్రౌండ్ ఆడియో", lyrics = "లిరిక్స్",
            settings = "సెట్టింగ్‌లు", active = "సక్రియం", empty = "ఖాళీ", live = "లైవ్", camera = "కెమెరా",
            wallpaper = "వాల్‌పేపర్", gallery = "గ్యాలరీ", custom = "కస్టమ్", defaultWord = "డిఫాల్ట్", back = "వెనుకకు"
        ),
        "gu" to buildDict(
            video = "વિડિયો", videos = "વિડિયો", music = "સંગીત", audio = "ઓડિયો", songs = "ગીતો", song = "ગીત",
            search = "શોધો", me = "મારું", folders = "ફોલ્ડર્સ", folder = "ફોલ્ડર", playlists = "પ્લેલિસ્ટ", history = "ઇતિહાસ",
            favourites = "મનપસંદ", albums = "આલ્બમ્સ", artists = "કલાકારો", all = "બધા",
            hybridShare = "હાઇબ્રિડ શેર", sendReceiveQr = "QR મોકલો અને મેળવો", privateVault = "પ્રાઇવેટ તિજોરી", pinLockedMedia = "પિન લોક મીડિયા",
            themes = "થીમ્સ", visualizer3d = "3D વિઝ્યુઅલાઇઝર", mascots = "માસ્કોટ્સ", mascotGalleryTitle = "3D સિંહ, પેટ્સ અને ટેડી",
            savedActive = "સક્રિય માસ્કોટ", size = "કદ", language = "ભાષા", pdfReader = "PDF રીડર", fullScreenZoom = "ફુલ-સ્ક્રીન ઝૂમ",
            videoToAudio = "વિડિયો થી ઓડિયો", extractMp3 = "MP3 ઓડિયો કાઢો", storageCleaner = "સ્ટોરેજ ક્લીનર", duplicateJunk = "ડુપ્લિકેટ અને જંક",
            networkStream = "નેટવર્ક સ્ટ્રીમ", statusSaver = "સ્ટેટસ સેવર", equalizer = "ઇક્વલાઇઝર", sleepTimer = "સ્લીપ ટાઇમર", recycleBin = "રિસાયકલ બિન",
            save = "સેવ કરો", saved = "સેવ થયું", cancel = "રદ કરો", close = "બંધ કરો", delete = "કાઢી નાખો", rename = "નામ બદલો",
            details = "વિગતો", share = "શેર", send = "મોકલો", receive = "મેળવો", scan = "સ્કેન", connect = "જોડો",
            paste = "પેસ્ટ", clear = "સાફ કરો", selectAll = "બધા પસંદ કરો", selected = "પસંદ કરેલ", sort = "ક્રમ", refresh = "રિફ્રેશ",
            play = "ચલાવો", pause = "રોકો", speed = "ઝડપ", volume = "અવાજ", subtitles = "સબટાઇટલ", loop = "લૂપ",
            shuffle = "શફલ", next = "આગળ", previous = "પાછળ", backgroundAudio = "બેકગ્રાઉન્ડ ઓડિયો", lyrics = "ગીતના બોલ",
            settings = "સેટિંગ્સ", active = "સક્રિય", empty = "ખાલી", live = "લાઇવ", camera = "કેમેરા",
            wallpaper = "વોલપેપર", gallery = "ગેલેરી", custom = "કસ્ટમ", defaultWord = "ડિફોલ્ટ", back = "પાછા"
        ),
        "pa" to buildDict(
            video = "ਵੀਡੀਓ", videos = "ਵੀਡੀਓ", music = "ਸੰਗੀਤ", audio = "ਆਡੀਓ", songs = "ਗਾਣੇ", song = "ਗਾਣਾ",
            search = "ਖੋਜੋ", me = "ਮੇਰਾ", folders = "ਫੋਲਡਰ", folder = "ਫੋਲਡਰ", playlists = "ਪਲੇਲਿਸਟ", history = "ਇਤਿਹਾਸ",
            favourites = "ਮਨਪਸੰਦ", albums = "ਐਲਬਮ", artists = "ਕਲਾਕਾਰ", all = "ਸਾਰੇ",
            hybridShare = "ਹਾਈਬ੍ਰਿਡ ਸ਼ੇਅਰ", sendReceiveQr = "QR ਭੇਜੋ ਅਤੇ ਪ੍ਰਾਪਤ ਕਰੋ", privateVault = "ਪ੍ਰਾਈਵੇਟ ਵੌਲਟ", pinLockedMedia = "ਪਿੰਨ ਲੌਕ ਮੀਡੀਆ",
            themes = "ਥੀਮ", visualizer3d = "3D ਵਿਜ਼ੂਅਲਾਈਜ਼ਰ", mascots = "ਮੈਸਕੌਟ", mascotGalleryTitle = "3D ਸ਼ੇਰ, ਪਪੀ ਅਤੇ ਟੈਡੀ",
            savedActive = "ਸਰਗਰਮ ਮੈਸਕੌਟ", size = "ਆਕਾਰ", language = "ਭਾਸ਼ਾ", pdfReader = "PDF ਰੀਡਰ", fullScreenZoom = "ਫੁੱਲ-ਸਕ੍ਰੀਨ ਜ਼ੂਮ",
            videoToAudio = "ਵੀਡੀਓ ਤੋਂ ਆਡੀਓ", extractMp3 = "MP3 ਆਡੀਓ ਕੱਢੋ", storageCleaner = "ਸਟੋਰੇਜ ਕਲੀਨਰ", duplicateJunk = "ਡੁਪਲੀਕੇਟ ਅਤੇ ਜੰਕ",
            networkStream = "ਨੈੱਟਵਰਕ ਸਟ੍ਰੀਮ", statusSaver = "ਸਟੇਟਸ ਸੇਵਰ", equalizer = "ਇਕੁਅਲਾਈਜ਼ਰ", sleepTimer = "ਸਲੀਪ ਟਾਈਮਰ", recycleBin = "ਰੀਸਾਈਕਲ ਬਿਨ",
            save = "ਸੇਵ ਕਰੋ", saved = "ਸੇਵ ਕੀਤਾ", cancel = "ਰੱਦ ਕਰੋ", close = "ਬੰਦ ਕਰੋ", delete = "ਮਿਟਾਓ", rename = "ਨਾਮ ਬਦਲੋ",
            details = "ਵੇਰਵਾ", share = "ਸ਼ੇਅਰ", send = "ਭੇਜੋ", receive = "ਪ੍ਰਾਪਤ ਕਰੋ", scan = "ਸਕੈਨ", connect = "ਕਨੈਕਟ",
            paste = "ਪੇਸਟ", clear = "ਸਾਫ਼ ਕਰੋ", selectAll = "ਸਭ ਚੁਣੋ", selected = "ਚੁਣਿਆ ਗਿਆ", sort = "ਕ੍ਰਮ", refresh = "ਰੀਫ੍ਰੈਸ਼",
            play = "ਚਲਾਓ", pause = "ਰੋਕੋ", speed = "ਗਤੀ", volume = "ਆਵਾਜ਼", subtitles = "ਸਬਟਾਈਟਲ", loop = "ਲੂਪ",
            shuffle = "ਸ਼ਫਲ", next = "ਅਗਲਾ", previous = "ਪਿਛਲਾ", backgroundAudio = "ਬੈਕਗ੍ਰਾਊਂਡ ਆਡੀਓ", lyrics = "ਬੋਲ",
            settings = "ਸੈਟਿੰਗਾਂ", active = "ਸਰਗਰਮ", empty = "ਖਾਲੀ", live = "ਲਾਈਵ", camera = "ਕੈਮਰਾ",
            wallpaper = "ਵਾਲਪੇਪਰ", gallery = "ਗੈਲਰੀ", custom = "ਕਸਟਮ", defaultWord = "ਡਿਫੌਲਟ", back = "ਵਾਪਸ"
        ),
        "kn" to buildDict(
            video = "ವೀಡಿಯೊ", videos = "ವೀಡಿಯೊಗಳು", music = "ಸಂಗೀತ", audio = "ಆಡಿಯೋ", songs = "ಹಾಡುಗಳು", song = "ಹಾಡು",
            search = "ಹುಡುಕಿ", me = "ನನ್ನ", folders = "ಫೋಲ್ಡರ್‌ಗಳು", folder = "ಫೋಲ್ಡರ್", playlists = "ಪ್ಲೇಲಿಸ್ಟ್", history = "ಇತಿಹಾಸ",
            favourites = "ಮೆಚ್ಚಿನವು", albums = "ಆಲ್ಬಮ್‌ಗಳು", artists = "ಕಲಾವಿದರು", all = "ಎಲ್ಲಾ",
            hybridShare = "ಹೈಬ್ರಿಡ್ ಶೇರ್", sendReceiveQr = "QR ಕಳುಹಿಸಿ ಮತ್ತು ಪಡೆಯಿರಿ", privateVault = "ಖಾಸಗಿ ವಾಲ್ಟ್", pinLockedMedia = "ಪಿನ್ ಲಾಕ್ ಮೀಡಿಯಾ",
            themes = "ಥೀಮ್‌ಗಳು", visualizer3d = "3D ವಿಶುವಲೈಜರ್", mascots = "ಮ್ಯಾಸ್ಕಾಟ್‌ಗಳು", mascotGalleryTitle = "3D ಸಿಂಹ ಮತ್ತು ಟೆಡ್ಡಿ ಗೊಂಬೆಗಳು",
            savedActive = "ಸಕ್ರಿಯ ಮ್ಯಾಸ್ಕಾಟ್", size = "ಗಾತ್ರ", language = "ಭಾಷೆ", pdfReader = "PDF ರೀಡರ್", fullScreenZoom = "ಪೂರ್ಣ-ಸ್ಕ್ರೀನ್ ಜೂಮ್",
            videoToAudio = "ವೀಡಿಯೊದಿಂದ ಆಡಿಯೋ", extractMp3 = "MP3 ಆಡಿಯೋ ತೆಗೆಯಿರಿ", storageCleaner = "ಸ್ಟೋರೇಜ್ ಕ್ಲೀನರ್", duplicateJunk = "ನಕಲಿ ಮತ್ತು ಜಂಕ್",
            networkStream = "ನೆಟ್‌ವರ್ಕ್ ಸ್ಟ್ರೀಮ್", statusSaver = "ಸ್ಟೇಟಸ್ ಸೇವರ್", equalizer = "ಈಕ್ವಲೈಜರ್", sleepTimer = "ಸ್ಲೀಪ್ ಟೈಮರ್", recycleBin = "ರಿಸೈಕಲ್ ಬಿನ್",
            save = "ಉಳಿಸಿ", saved = "ಉಳಿಸಲಾಗಿದೆ", cancel = "ರದ್ದು", close = "ಮುಚ್ಚಿ", delete = "ಅಳಿಸಿ", rename = "ಹೆಸರು ಬದಲಿಸಿ",
            details = "ವಿವರಗಳು", share = "ಹಂಚಿಕೊಳ್ಳಿ", send = "ಕಳುಹಿಸಿ", receive = "ಸ್ವೀಕರಿಸಿ", scan = "ಸ್ಕ್ಯಾನ್", connect = "ಸಂಪರ್ಕಿಸಿ",
            paste = "ಪೇಸ್ಟ್", clear = "ತೆರವುಗೊಳಿಸಿ", selectAll = "ಎಲ್ಲಾ ಆಯ್ಕೆಮಾಡಿ", selected = "ಆಯ್ಕೆಮಾಡಲಾಗಿದೆ", sort = "ವಿಂಗಡಿಸಿ", refresh = "ರಿಫ್ರೆಶ್",
            play = "ಪ್ಲೇ", pause = "ವಿರಾಮ", speed = "ವೇಗ", volume = "ಧ್ವನಿ", subtitles = "ಉಪಶೀರ್ಷಿಕೆ", loop = "ಲೂಪ್",
            shuffle = "ಶಫಲ್", next = "ಮುಂದಿನ", previous = "ಹಿಂದಿನ", backgroundAudio = "ಹಿನ್ನೆಲೆ ಆಡಿಯೋ", lyrics = "ಸಾಹಿತ್ಯ",
            settings = "ಸೆಟ್ಟಿಂಗ್‌ಗಳು", active = "ಸಕ್ರಿಯ", empty = "ಖಾಲಿ", live = "ಲೈವ್", camera = "ಕ್ಯಾಮೆರಾ",
            wallpaper = "ವಾಲ್‌ಪೇಪರ್", gallery = "ಗ್ಯಾಲರಿ", custom = "ಕಸ್ಟಮ್", defaultWord = "ಡೀಫಾಲ್ಟ್", back = "ಹಿಂದೆ"
        ),
        "ml" to buildDict(
            video = "വീഡിയോ", videos = "വീഡിയോകൾ", music = "സംഗീതം", audio = "ഓഡിയോ", songs = "ഗാനങ്ങൾ", song = "ഗാനം",
            search = "തിരയുക", me = "എന്റെ", folders = "ഫോൾഡറുകൾ", folder = "ഫോൾഡർ", playlists = "പ്ലേലിസ്റ്റ്", history = "ചരിത്രം",
            favourites = "പ്രിയപ്പെട്ടവ", albums = "ആൽബങ്ങൾ", artists = "കലാകാരന്മാർ", all = "എല്ലാം",
            hybridShare = "ഹൈബ്രിഡ് ഷെയർ", sendReceiveQr = "QR അയയ്ക്കുക & സ്വീകരിക്കുക", privateVault = "പ്രൈവറ്റ് വോൾട്ട്", pinLockedMedia = "PIN ലോക്ക് മീഡിയ",
            themes = "തീമുകൾ", visualizer3d = "3D വിഷ്വലൈസർ", mascots = "മാസ്കോട്ടുകൾ", mascotGalleryTitle = "3D സിംഹം & ടെഡി പാവകൾ",
            savedActive = "സജീവ മാസ്കോട്ട്", size = "വലിപ്പം", language = "ഭാഷ", pdfReader = "PDF റീഡർ", fullScreenZoom = "ഫുൾ-സ്ക്രീൻ സൂം",
            videoToAudio = "വീഡിയോ -> ഓഡിയോ", extractMp3 = "MP3 ഓഡിയോ എടുക്കുക", storageCleaner = "സ്റ്റോറേജ് ക്ലീനർ", duplicateJunk = "ഡ്യൂപ്ലിക്കേറ്റ് & ജങ്ക്",
            networkStream = "നെറ്റ്‌വർക്ക് സ്ട്രീം", statusSaver = "സ്റ്റാറ്റസ് സേവർ", equalizer = "ഈക്വലൈസർ", sleepTimer = "സ്ലീപ്പ് ടൈമർ", recycleBin = "റീസൈക്കിൾ ബിൻ",
            save = "സേവ്", saved = "സേവ് ചെയ്തു", cancel = "റദ്ദാക്കുക", close = "അടയ്ക്കുക", delete = "നീക്കം ചെയ്യുക", rename = "പേര് മാറ്റുക",
            details = "വിവരങ്ങൾ", share = "ഷെയർ", send = "അയയ്ക്കുക", receive = "സ്വീകരിക്കുക", scan = "സ്കാൻ", connect = "ബന്ധിപ്പിക്കുക",
            paste = "പേസ്റ്റ്", clear = "മായ്ക്കുക", selectAll = "എല്ലാം തിരഞ്ഞെടുക്കുക", selected = "തിരഞ്ഞെടുത്തു", sort = "ക്രമം", refresh = "പുതുക്കുക",
            play = "പ്ലേ", pause = "പോസ്", speed = "വേഗത", volume = "ശബ്ദം", subtitles = "സബ്ടൈറ്റിൽ", loop = "ലൂപ്പ്",
            shuffle = "ഷഫിൾ", next = "അടുത്തത്", previous = "മുമ്പത്തേത്", backgroundAudio = "പശ്ചാത്തല ഓഡിയോ", lyrics = "വരികൾ",
            settings = "ക്രമീകരണങ്ങൾ", active = "സജീവം", empty = "ശൂന്യം", live = "ലൈവ്", camera = "ക്യാമറ",
            wallpaper = "വാൾപേപ്പർ", gallery = "ഗാലറി", custom = "കസ്റ്റം", defaultWord = "ഡിഫോൾട്ട്", back = "തിരികെ"
        ),
        "or" to buildDict(
            video = "ଭିଡିଓ", videos = "ଭିଡିଓଗୁଡ଼ିକ", music = "ସଙ୍ଗୀତ", audio = "ଅଡିଓ", songs = "ଗୀତଗୁଡ଼ିକ", song = "ଗୀତ",
            search = "ଖୋଜନ୍ତୁ", me = "ମୋର", folders = "ଫୋଲ୍ଡର", folder = "ଫୋଲ୍ଡର", playlists = "ପ୍ଲେଲିଷ୍ଟ", history = "ଇତିହାସ",
            favourites = "ପସନ୍ଦ", albums = "ଆଲବମ୍", artists = "କଳାକାର", all = "ସମସ୍ତ",
            hybridShare = "ହାଇବ୍ରିଡ୍ ସେୟାର୍", sendReceiveQr = "QR ପଠାନ୍ତୁ ଓ ପାଆନ୍ତୁ", privateVault = "ପ୍ରାଇଭେଟ୍ ଭଲ୍ଟ", pinLockedMedia = "ପିନ୍ ଲକ୍ ମିଡିଆ",
            themes = "ଥିମ୍", visualizer3d = "3D ଭିଜୁଆଲାଇଜର୍", mascots = "ମାସ୍କଟ୍", mascotGalleryTitle = "3D ସିଂହ ଓ ଟେଡି ମାସ୍କଟ୍",
            savedActive = "ସକ୍ରିୟ ମାସ୍କଟ୍", size = "ଆକାର", language = "ଭାଷା", pdfReader = "PDF ରିଡର୍", fullScreenZoom = "ଫୁଲ୍-ସ୍କ୍ରିନ୍ ଜୁମ୍",
            videoToAudio = "ଭିଡିଓ ରୁ ଅଡିଓ", extractMp3 = "MP3 ଅଡିଓ ବାହାର କରନ୍ତୁ", storageCleaner = "ଷ୍ଟୋରେଜ୍ କ୍ଲିନର୍", duplicateJunk = "ଡୁପ୍ଲିକେଟ୍ ଓ ଜଙ୍କ୍",
            networkStream = "ନେଟୱାର୍କ ଷ୍ଟ୍ରିମ୍", statusSaver = "ଷ୍ଟାଟସ୍ ସେଭର୍", equalizer = "ଇକ୍ୱାଲାଇଜର୍", sleepTimer = "ସ୍ଲିପ୍ ଟାଇମର୍", recycleBin = "ରିସାଇκଲ୍ ବିନ୍",
            save = "ସେଭ୍", saved = "ସେଭ୍ ହୋଇଛି", cancel = "ବାତିଲ୍", close = "ବନ୍ଦ କରନ୍ତୁ", delete = "ଡିଲିଟ୍", rename = "ନାମ ବଦଳାନ୍ତୁ",
            details = "ବିବରଣୀ", share = "ସେୟାର୍", send = "ପଠାନ୍ତୁ", receive = "ଗ୍ରହଣ କରନ୍ତୁ", scan = "ସ୍କାନ୍", connect = "ସଂଯୋଗ",
            paste = "ପେଷ୍ଟ", clear = "ସଫା କରନ୍ତୁ", selectAll = "ସବୁ ବାଛନ୍ତୁ", selected = "ବଛାଯାଇଛି", sort = "ସଜାନ୍ତୁ", refresh = "ରିଫ୍ରେସ୍",
            play = "ଚଲାନ୍ତୁ", pause = "ରୋକନ୍ତୁ", speed = "ଗତି", volume = "ଭଲ୍ୟୁମ୍", subtitles = "ସବଟାଇଟଲ୍", loop = "ଲୁପ୍",
            shuffle = "ସଫଲ୍", next = "ପରବର୍ତ୍ତୀ", previous = "ପୂର୍ବବର୍ତ୍ତୀ", backgroundAudio = "ବ୍ୟାକଗ୍ରାଉଣ୍ଡ ଅଡିଓ", lyrics = "ଗୀତର ପଦ",
            settings = "ସେଟିଂସ୍", active = "ସକ୍ରିୟ", empty = "ଖାଲି", live = "ଲାଇଭ୍", camera = "କ୍ୟାମେରା",
            wallpaper = "ୱାଲପେପର୍", gallery = "ଗ୍ୟାଲେରୀ", custom = "କଷ୍ଟମ୍", defaultWord = "ଡିଫଲ୍ଟ", back = "ପଛକୁ"
        ),
        "ur" to buildDict(
            video = "ویڈیو", videos = "ویڈیوز", music = "موسیقی", audio = "آڈیو", songs = "گانے", song = "گانا",
            search = "تلاش کریں", me = "میرا", folders = "فولڈرز", folder = "فولڈر", playlists = "پلے لسٹ", history = "تاریخچہ",
            favourites = "پسندیدہ", albums = "البمز", artists = "فنکار", all = "تمام",
            hybridShare = "ہائبرڈ شیئر", sendReceiveQr = "QR بھیجیں اور حاصل کریں", privateVault = "پرائیویٹ والٹ", pinLockedMedia = "پن لاک میڈیا",
            themes = "تھیمز", visualizer3d = "3D ویژولائزر", mascots = "ماسکوٹس", mascotGalleryTitle = "3D شیر اور ٹیڈی ماسکوٹس",
            savedActive = "فعال ماسکوٹ", size = "سائز", language = "زبان", pdfReader = "پی ڈی ایف ریڈر", fullScreenZoom = "فل اسکرین زوم",
            videoToAudio = "ویڈیو سے آڈیو", extractMp3 = "MP3 آڈیو نکالیں", storageCleaner = "اسٹوریج کلینر", duplicateJunk = "ڈپلیکیٹ اور جنک",
            networkStream = "نیٹ ورک اسٹریم", statusSaver = "اسٹیٹس سیور", equalizer = "ایکولائزر", sleepTimer = "سلیپ ٹائمر", recycleBin = "ری سائیکل بن",
            save = "محفوظ کریں", saved = "محفوظ شدہ", cancel = "منسوخ", close = "بند کریں", delete = "حذف کریں", rename = "نام بدلیں",
            details = "تفصیلات", share = "شیئر", send = "بھیجیں", receive = "وصول کریں", scan = "اسکین", connect = "جڑیں",
            paste = "پیسٹ", clear = "صاف کریں", selectAll = "سب منتخب کریں", selected = "منتخب شدہ", sort = "ترتیب", refresh = "ریفریش",
            play = "چلائیں", pause = "روکیں", speed = "رفتار", volume = "آواز", subtitles = "سب ٹائٹلز", loop = "لوپ",
            shuffle = "شفل", next = "اگلا", previous = "پچھلا", backgroundAudio = "بیک گراؤنڈ آڈیو", lyrics = "بول",
            settings = "ترتیبات", active = "فعال", empty = "خالی", live = "لائیو", camera = "کیمرہ",
            wallpaper = "وال پیپر", gallery = "گیلری", custom = "کسٹم", defaultWord = "ڈیفالٹ", back = "واپس"
        ),
        "bho" to buildDict(
            video = "वीडियो", videos = "वीडियो कुल", music = "गाना-बजाना", audio = "ऑडियो", songs = "गीत कुल", song = "गीत",
            search = "खोजीं", me = "हमार", folders = "फ़ोल्डर", folder = "फ़ोल्डर", playlists = "प्लेलिस्ट", history = "पुरान देखल",
            favourites = "मनपसंद", albums = "एल्बम", artists = "कलाकार", all = "सभे",
            hybridShare = "हाइब्रिड शेयर", sendReceiveQr = "QR से भेजीं आ मँगाईं", privateVault = "छुपावल तिजोरी", pinLockedMedia = "पिन लॉक मीडिया",
            themes = "थीम आ रंग", visualizer3d = "3D विज़ुअलाइज़र", mascots = "गुड्डा-गुड्डी", mascotGalleryTitle = "3D शेर, पपी आ टेडी गुड्डा",
            savedActive = "चालू गुड्डा", size = "नाप", language = "भाषा", pdfReader = "पीडीएफ पढ़ीं", fullScreenZoom = "पूरा स्क्रीन ज़ूम",
            videoToAudio = "वीडियो से MP3", extractMp3 = "MP3 गाना निकालीं", storageCleaner = "फ़ोन सफ़ाई", duplicateJunk = "फालतू कचरा हटाईं",
            networkStream = "ऑनलाइन स्ट्रीम", statusSaver = "स्टेटस सेवर", equalizer = "बास आ साउंड", sleepTimer = "नींद टाइमर", recycleBin = "रीसायकल बिन",
            save = "सेव करीं", saved = "सेव बा", cancel = "छोड़ीं", close = "बंद करीं", delete = "मिटाईं", rename = "नाम बदलीं",
            details = "जानकारी", share = "भेजीं", send = "भेजीं", receive = "लीं (Receive)", scan = "स्कैन करीं", connect = "जोड़ीं",
            paste = "चिपकाईं", clear = "साफ़ करीं", selectAll = "सभे चुनीं", selected = "चुनल गइल", sort = "सजाईं", refresh = "ताज़ा करीं",
            play = "चलाईं", pause = "रोकीं", speed = "रफ़्तार", volume = "आवाज़", subtitles = "सबटाइटल", loop = "लगातार",
            shuffle = "मिला के", next = "अगिला", previous = "पछिला", backgroundAudio = "पर्दा के पीछे गाना", lyrics = "गीत के बोल",
            settings = "सेटिंग्स", active = "चालू बा", empty = "खाली बा", live = "लाइव", camera = "कैमरा",
            wallpaper = "वॉलपेपर", gallery = "गैलरी", custom = "अपन फोटो", defaultWord = "डिफ़ॉल्ट", back = "पाछे"
        ),
        "ne" to buildDict(
            video = "भिडियो", videos = "भिडियोहरू", music = "संगीत", audio = "अडियो", songs = "गीतहरू", song = "गीत",
            search = "खोज्नुहोस्", me = "मेरो", folders = "फोल्डरहरू", folder = "फोल्डर", playlists = "प्लेलिस्ट", history = "इतिहास",
            favourites = "मनपर्ने", albums = "एल्बम", artists = "कलाकार", all = "सबै",
            hybridShare = "हाइब्रिड सेयर", sendReceiveQr = "QR पठाउनुहोस् र पाउनुहोस्", privateVault = "गोप्य भल्ट", pinLockedMedia = "पिन लक मिडिया",
            themes = "थिमहरू", visualizer3d = "3D भिजुअलाइजर", mascots = "म्यास्कटहरू", mascotGalleryTitle = "3D सिंह र टेडी म्यास्कट",
            savedActive = "सक्रिय म्यास्कट", size = "आकार", language = "भाषा", pdfReader = "PDF रिडर", fullScreenZoom = "फुल-स्क्रिन जुम",
            videoToAudio = "भिडियो बाट अडियो", extractMp3 = "MP3 अडियो निकाल्नुहोस्", storageCleaner = "स्टोरेज क्लिनर", duplicateJunk = "डुप्लिकेट र जंक",
            networkStream = "नेटवर्क स्ट्रिम", statusSaver = "स्टेटस सेभर", equalizer = "इक्वेलाइजर", sleepTimer = "स्लिप टाइमर", recycleBin = "रिसाइकल बिन",
            save = "सेभ गर्नुहोस्", saved = "सेभ गरियो", cancel = "रद्द गर्नुहोस्", close = "बन्द गर्नुहोस्", delete = "हटाउनुहोस्", rename = "नाम फेर्नुहोस्",
            details = "विवरण", share = "सेयर", send = "पठाउनुहोस्", receive = "प्राप्त गर्नुहोस्", scan = "स्क्यान", connect = "जोड्नुहोस्",
            paste = "पेस्ट", clear = "सफा गर्नुहोस्", selectAll = "सबै छान्नुहोस्", selected = "छानिएको", sort = "क्रम", refresh = "रिफ्रेस",
            play = "चलाउनुहोस्", pause = "रोक्नुहोस्", speed = "गति", volume = "आवाज", subtitles = "सबटाइटल", loop = "लुप",
            shuffle = "सफल", next = "अर्को", previous = "अघिल्लो", backgroundAudio = "ब्याकग्राउन्ड अडियो", lyrics = "गीतका शब्द",
            settings = "सेटिङहरू", active = "सक्रिय", empty = "खाली", live = "लाइभ", camera = "क्यामेरा",
            wallpaper = "वालपेपर", gallery = "ग्यालरी", custom = "कस्टम", defaultWord = "डिफल्ट", back = "पछाडि"
        ),
        "ar" to buildDict(
            video = "فيديو", videos = "مقاطع الفيديو", music = "موسيقى", audio = "صوتيات", songs = "أغاني", song = "أغنية",
            search = "بحث", me = "حسابي", folders = "المجلدات", folder = "مجلد", playlists = "قوائم التشغيل", history = "السجل",
            favourites = "المفضلة", albums = "الألبومات", artists = "الفنانون", all = "الكل",
            hybridShare = "مشاركة سريعة", sendReceiveQr = "إرسال واستلام QR", privateVault = "الخزنة السرية", pinLockedMedia = "وسائط محمية برمز",
            themes = "السمات", visualizer3d = "مؤثرات 3D", mascots = "الدمى ثلاثية الأبعاد", mascotGalleryTitle = "دمى الأسد والدب 3D",
            savedActive = "الدمية النشطة", size = "الحجم", language = "اللغة", pdfReader = "قارئ PDF", fullScreenZoom = "تكبير كامل الشاشة",
            videoToAudio = "فيديو إلى صوت", extractMp3 = "استخراج صوت MP3", storageCleaner = "منظف الذاكرة", duplicateJunk = "الملفات المكررة والمؤقتة",
            networkStream = "بث الشبكة", statusSaver = "حافظ الحالات", equalizer = "معادل الصوت", sleepTimer = "مؤقت النوم", recycleBin = "سلة المحذوفات",
            save = "حفظ", saved = "تم الحفظ", cancel = "إلغاء", close = "إغلاق", delete = "حذف", rename = "إعادة تسمية",
            details = "التفاصيل", share = "مشاركة", send = "إرسال", receive = "استلام", scan = "مسح الكود", connect = "اتصال",
            paste = "لصق", clear = "مسح", selectAll = "تحديد الكل", selected = "محدد", sort = "ترتيب", refresh = "تحديث",
            play = "تشغيل", pause = "إيقاف مؤقت", speed = "السرعة", volume = "الصوت", subtitles = "الترجمة", loop = "تكرار",
            shuffle = "عشوائي", next = "التالي", previous = "السابق", backgroundAudio = "صوت في الخلفية", lyrics = "كلمات الأغنية",
            settings = "الإعدادات", active = "نشط", empty = "فارغ", live = "مباشر", camera = "الكاميرا",
            wallpaper = "خلفية الشاشة", gallery = "المعرض", custom = "مخصص", defaultWord = "افتراضي", back = "رجوع"
        ),
        "es" to buildDict(
            video = "Vídeo", videos = "Vídeos", music = "Música", audio = "Audio", songs = "Canciones", song = "Canción",
            search = "Buscar", me = "Mi Hub", folders = "Carpetas", folder = "Carpeta", playlists = "Listas", history = "Historial",
            favourites = "Favoritos", albums = "Álbumes", artists = "Artistas", all = "Todo",
            hybridShare = "Compartir Híbrido", sendReceiveQr = "Enviar y Recibir QR", privateVault = "Bóveda Privada", pinLockedMedia = "Multimedia con PIN",
            themes = "Temas", visualizer3d = "Visualizador 3D", mascots = "Mascotas 3D", mascotGalleryTitle = "Mascotas 3D León y Teddy",
            savedActive = "Mascota Activa", size = "Tamaño", language = "Idioma", pdfReader = "Lector PDF", fullScreenZoom = "Zoom Pantalla Completa",
            videoToAudio = "Vídeo a Audio", extractMp3 = "Extraer Audio MP3", storageCleaner = "Limpiador", duplicateJunk = "Duplicados y Basura",
            networkStream = "Transmisión en Red", statusSaver = "Guardar Estados", equalizer = "Ecualizador", sleepTimer = "Temporizador", recycleBin = "Papelera",
            save = "Guardar", saved = "Guardado", cancel = "Cancelar", close = "Cerrar", delete = "Eliminar", rename = "Renombrar",
            details = "Detalles", share = "Compartir", send = "Enviar", receive = "Recibir", scan = "Escanear", connect = "Conectar",
            paste = "Pegar", clear = "Limpiar", selectAll = "Seleccionar Todo", selected = "Seleccionado", sort = "Ordenar", refresh = "Actualizar",
            play = "Reproducir", pause = "Pausar", speed = "Velocidad", volume = "Volumen", subtitles = "Subtítulos", loop = "Repetir",
            shuffle = "Aleatorio", next = "Siguiente", previous = "Anterior", backgroundAudio = "Audio de Fondo", lyrics = "Letras",
            settings = "Ajustes", active = "Activo", empty = "Vacío", live = "En Vivo", camera = "Cámara",
            wallpaper = "Fondo", gallery = "Galería", custom = "Personalizado", defaultWord = "Predeterminado", back = "Atrás"
        ),
        "fr" to buildDict(
            video = "Vidéo", videos = "Vidéos", music = "Musique", audio = "Audio", songs = "Chansons", song = "Chanson",
            search = "Rechercher", me = "Mon Espace", folders = "Dossiers", folder = "Dossier", playlists = "Playlists", history = "Historique",
            favourites = "Favoris", albums = "Albums", artists = "Artistes", all = "Tout",
            hybridShare = "Partage Hybride", sendReceiveQr = "Envoyer & Recevoir QR", privateVault = "Coffre Privé", pinLockedMedia = "Médias Verrouillés PIN",
            themes = "Thèmes", visualizer3d = "Visualiseur 3D", mascots = "Mascottes 3D", mascotGalleryTitle = "Mascottes 3D Lion & Teddy",
            savedActive = "Mascotte Active", size = "Taille", language = "Langue", pdfReader = "Lecteur PDF", fullScreenZoom = "Zoom Plein Écran",
            videoToAudio = "Vidéo en Audio", extractMp3 = "Extraire Audio MP3", storageCleaner = "Nettoyeur", duplicateJunk = "Doublons & Inutiles",
            networkStream = "Flux Réseau", statusSaver = "Sauver Statuts", equalizer = "Égaliseur", sleepTimer = "Minuteur", recycleBin = "Corbeille",
            save = "Enregistrer", saved = "Enregistré", cancel = "Annuler", close = "Fermer", delete = "Supprimer", rename = "Renommer",
            details = "Détails", share = "Partager", send = "Envoyer", receive = "Recevoir", scan = "Scanner", connect = "Connecter",
            paste = "Coller", clear = "Effacer", selectAll = "Tout Sélectionner", selected = "Sélectionné", sort = "Trier", refresh = "Actualiser",
            play = "Lire", pause = "Pause", speed = "Vitesse", volume = "Volume", subtitles = "Sous-titres", loop = "Boucle",
            shuffle = "Aléatoire", next = "Suivant", previous = "Précédent", backgroundAudio = "Audio en Arrière-plan", lyrics = "Paroles",
            settings = "Paramètres", active = "Actif", empty = "Vide", live = "En Direct", camera = "Caméra",
            wallpaper = "Fond d'écran", gallery = "Galerie", custom = "Personnalisé", defaultWord = "Par défaut", back = "Retour"
        ),
        "pt" to buildDict(
            video = "Vídeo", videos = "Vídeos", music = "Música", audio = "Áudio", songs = "Músicas", song = "Música",
            search = "Buscar", me = "Meu Hub", folders = "Pastas", folder = "Pasta", playlists = "Playlists", history = "Histórico",
            favourites = "Favoritos", albums = "Álbuns", artists = "Artistas", all = "Tudo",
            hybridShare = "Partilha Híbrida", sendReceiveQr = "Enviar e Receber QR", privateVault = "Cofre Privado", pinLockedMedia = "Mídia com PIN",
            themes = "Temas", visualizer3d = "Visualizador 3D", mascots = "Mascotes 3D", mascotGalleryTitle = "Mascotes 3D Leão e Teddy",
            savedActive = "Mascote Ativo", size = "Tamanho", language = "Idioma", pdfReader = "Leitor PDF", fullScreenZoom = "Zoom Tela Cheia",
            videoToAudio = "Vídeo para Áudio", extractMp3 = "Extrair Áudio MP3", storageCleaner = "Limpador", duplicateJunk = "Duplicados e Lixo",
            networkStream = "Stream de Rede", statusSaver = "Salvar Status", equalizer = "Equalizador", sleepTimer = "Temporizador", recycleBin = "Lixeira",
            save = "Salvar", saved = "Salvo", cancel = "Cancelar", close = "Fechar", delete = "Excluir", rename = "Renomear",
            details = "Detalhes", share = "Compartilhar", send = "Enviar", receive = "Receber", scan = "Escanear", connect = "Conectar",
            paste = "Colar", clear = "Limpar", selectAll = "Selecionar Tudo", selected = "Selecionado", sort = "Ordenar", refresh = "Atualizar",
            play = "Reproduzir", pause = "Pausar", speed = "Velocidade", volume = "Volume", subtitles = "Legendas", loop = "Repetir",
            shuffle = "Aleatório", next = "Próximo", previous = "Anterior", backgroundAudio = "Áudio em Segundo Plano", lyrics = "Letras",
            settings = "Configurações", active = "Ativo", empty = "Vazio", live = "Ao Vivo", camera = "Câmera",
            wallpaper = "Papel de Parede", gallery = "Galeria", custom = "Personalizado", defaultWord = "Padrão", back = "Voltar"
        ),
        "ru" to buildDict(
            video = "Видео", videos = "Видео", music = "Музыка", audio = "Аудио", songs = "Песни", song = "Песня",
            search = "Поиск", me = "Мой Хаб", folders = "Папки", folder = "Папка", playlists = "Плейлисты", history = "История",
            favourites = "Избранное", albums = "Альбомы", artists = "Артисты", all = "Все",
            hybridShare = "Гибрид Шер", sendReceiveQr = "Отправка и приём QR", privateVault = "Личный сейф", pinLockedMedia = "Медиа под PIN-кодом",
            themes = "Темы", visualizer3d = "3D Визуализатор", mascots = "3D Маскоты", mascotGalleryTitle = "3D Лев и Плюшевые Маскоты",
            savedActive = "Активный маскот", size = "Размер", language = "Язык", pdfReader = "PDF Ридер", fullScreenZoom = "Полный зум",
            videoToAudio = "Видео в Аудио", extractMp3 = "Извлечь MP3", storageCleaner = "Очистка памяти", duplicateJunk = "Дубликаты и кэш",
            networkStream = "Сетевой поток", statusSaver = "Сохранение статусов", equalizer = "Эквалайзер", sleepTimer = "Таймер сна", recycleBin = "Корзина",
            save = "Сохранить", saved = "Сохранено", cancel = "Отмена", close = "Закрыть", delete = "Удалить", rename = "Переименовать",
            details = "Сведения", share = "Поделиться", send = "Отправить", receive = "Принять", scan = "Сканировать", connect = "Подключить",
            paste = "Вставить", clear = "Очистить", selectAll = "Выбрать все", selected = "Выбрано", sort = "Сортировка", refresh = "Обновить",
            play = "Играть", pause = "Пауза", speed = "Скорость", volume = "Громкость", subtitles = "Субтитры", loop = "Повтор",
            shuffle = "Перемешать", next = "Далее", previous = "Назад", backgroundAudio = "Фоновое аудио", lyrics = "Текст песни",
            settings = "Настройки", active = "Активно", empty = "Пусто", live = "Эфир", camera = "Камера",
            wallpaper = "Обои", gallery = "Галерея", custom = "Свои", defaultWord = "По умолчанию", back = "Назад"
        ),
        "de" to buildDict(
            video = "Video", videos = "Videos", music = "Musik", audio = "Audio", songs = "Lieder", song = "Lied",
            search = "Suche", me = "Mein Hub", folders = "Ordner", folder = "Ordner", playlists = "Playlists", history = "Verlauf",
            favourites = "Favoriten", albums = "Alben", artists = "Künstler", all = "Alle",
            hybridShare = "Hybrid Teilen", sendReceiveQr = "Senden & Empfangen QR", privateVault = "Privater Tresor", pinLockedMedia = "PIN-geschützte Medien",
            themes = "Designs", visualizer3d = "3D-Visualizer", mascots = "3D-Maskottchen", mascotGalleryTitle = "3D Löwe & Teddy Maskottchen",
            savedActive = "Aktives Maskottchen", size = "Größe", language = "Sprache", pdfReader = "PDF-Leser", fullScreenZoom = "Vollbild-Zoom",
            videoToAudio = "Video zu Audio", extractMp3 = "MP3 extrahieren", storageCleaner = "Speicher-Reiniger", duplicateJunk = "Duplikate & Müll",
            networkStream = "Netzwerk-Stream", statusSaver = "Status Speichern", equalizer = "Equalizer", sleepTimer = "Schlaf-Timer", recycleBin = "Papierkorb",
            save = "Speichern", saved = "Gespeichert", cancel = "Abbrechen", close = "Schließen", delete = "Löschen", rename = "Umbenennen",
            details = "Details", share = "Teilen", send = "Senden", receive = "Empfangen", scan = "Scannen", connect = "Verbinden",
            paste = "Einfügen", clear = "Leeren", selectAll = "Alle auswählen", selected = "Ausgewählt", sort = "Sortieren", refresh = "Aktualisieren",
            play = "Abspielen", pause = "Pause", speed = "Tempo", volume = "Lautstärke", subtitles = "Untertitel", loop = "Schleife",
            shuffle = "Zufall", next = "Weiter", previous = "Zurück", backgroundAudio = "Hintergrund-Audio", lyrics = "Songtext",
            settings = "Einstellungen", active = "Aktiv", empty = "Leer", live = "Live", camera = "Kamera",
            wallpaper = "Hintergrundbild", gallery = "Galerie", custom = "Eigene", defaultWord = "Standard", back = "Zurück"
        ),
        "ja" to buildDict(
            video = "動画", videos = "動画一覧", music = "音楽", audio = "オーディオ", songs = "曲", song = "曲",
            search = "検索", me = "マイハブ", folders = "フォルダ", folder = "フォルダ", playlists = "プレイリスト", history = "再生履歴",
            favourites = "お気に入り", albums = "アルバム", artists = "アーティスト", all = "すべて",
            hybridShare = "ハイブリッド共有", sendReceiveQr = "QRで送受信", privateVault = "秘密の金庫", pinLockedMedia = "PINロックメディア",
            themes = "テーマ", visualizer3d = "3Dビジュアライザー", mascots = "3Dマスコット", mascotGalleryTitle = "3Dライオン＆テディマスコット",
            savedActive = "選択中のマスコット", size = "サイズ", language = "言語", pdfReader = "PDFリーダー", fullScreenZoom = "全画面ズーム",
            videoToAudio = "動画を音声へ", extractMp3 = "MP3音声を抽出", storageCleaner = "クリーナー", duplicateJunk = "重複と不要ファイル",
            networkStream = "ネットワーク配信", statusSaver = "ステータス保存", equalizer = "イコライザー", sleepTimer = "スリープタイマー", recycleBin = "ゴミ箱",
            save = "保存", saved = "保存済み", cancel = "キャンセル", close = "閉じる", delete = "削除", rename = "名前変更",
            details = "詳細", share = "共有", send = "送信", receive = "受信", scan = "スキャン", connect = "接続",
            paste = "貼り付け", clear = "クリア", selectAll = "すべて選択", selected = "選択中", sort = "並べ替え", refresh = "更新",
            play = "再生", pause = "一時停止", speed = "再生速度", volume = "音量", subtitles = "字幕", loop = "ループ",
            shuffle = "シャッフル", next = "次へ", previous = "前へ", backgroundAudio = "バックグラウンド再生", lyrics = "歌詞",
            settings = "設定", active = "有効", empty = "空です", live = "ライブ", camera = "カメラ",
            wallpaper = "壁紙", gallery = "ギャラリー", custom = "カスタム", defaultWord = "標準", back = "戻る"
        ),
        "ko" to buildDict(
            video = "비디오", videos = "비디오", music = "음악", audio = "오디오", songs = "노래", song = "곡",
            search = "검색", me = "내 허브", folders = "폴더", folder = "폴더", playlists = "재생목록", history = "재생 기록",
            favourites = "즐겨찾기", albums = "앨범", artists = "아티스트", all = "전체",
            hybridShare = "하이브리드 공유", sendReceiveQr = "QR 보내기 및 받기", privateVault = "비밀 금고", pinLockedMedia = "PIN 잠금 미디어",
            themes = "테마", visualizer3d = "3D 비주얼라이저", mascots = "3D 마스코트", mascotGalleryTitle = "3D 라이온 & 테디 마스코트",
            savedActive = "활성 마스코트", size = "크기", language = "언어", pdfReader = "PDF 리더", fullScreenZoom = "전체 화면 줌",
            videoToAudio = "비디오 -> 오디오", extractMp3 = "MP3 오디오 추출", storageCleaner = "저장소 정리", duplicateJunk = "중복 및 정크 파일",
            networkStream = "네트워크 스트림", statusSaver = "상태 저장기", equalizer = "이퀄라이저", sleepTimer = "취침 타이머", recycleBin = "휴지통",
            save = "저장", saved = "저장됨", cancel = "취소", close = "닫기", delete = "삭제", rename = "이름 변경",
            details = "상세정보", share = "공유", send = "보내기", receive = "받기", scan = "스캔", connect = "연결",
            paste = "붙여넣기", clear = "지우기", selectAll = "전체 선택", selected = "선택됨", sort = "정렬", refresh = "새로고침",
            play = "재생", pause = "일시정지", speed = "속도", volume = "볼륨", subtitles = "자막", loop = "반복",
            shuffle = "셔플", next = "다음", previous = "이전", backgroundAudio = "백그라운드 오디오", lyrics = "가사",
            settings = "설정", active = "활성", empty = "비어 있음", live = "라이브", camera = "카메라",
            wallpaper = "배경화면", gallery = "갤러리", custom = "사용자 지정", defaultWord = "기본값", back = "뒤로"
        ),
        "zh" to buildDict(
            video = "视频", videos = "视频", music = "音乐", audio = "音频", songs = "歌曲", song = "歌曲",
            search = "搜索", me = "我的", folders = "文件夹", folder = "文件夹", playlists = "播放列表", history = "播放历史",
            favourites = "收藏", albums = "专辑", artists = "歌手", all = "全部",
            hybridShare = "极速快传", sendReceiveQr = "扫码发送与接收", privateVault = "私密保险箱", pinLockedMedia = "PIN码加密媒体",
            themes = "主题", visualizer3d = "3D律动视效", mascots = "3D萌宠挂件", mascotGalleryTitle = "3D狮子与泰迪萌宠挂件",
            savedActive = "当前挂件", size = "尺寸", language = "语言", pdfReader = "PDF阅读器", fullScreenZoom = "全屏缩放阅读",
            videoToAudio = "视频转音频", extractMp3 = "提取MP3音频", storageCleaner = "空间清理", duplicateJunk = "重复与缓存清理",
            networkStream = "网络串流", statusSaver = "状态保存", equalizer = "均衡器", sleepTimer = "睡眠定时", recycleBin = "回收站",
            save = "保存", saved = "已保存", cancel = "取消", close = "关闭", delete = "删除", rename = "重命名",
            details = "详情", share = "分享", send = "发送", receive = "接收", scan = "扫码", connect = "连接",
            paste = "粘贴", clear = "清空", selectAll = "全选", selected = "已选", sort = "排序", refresh = "刷新",
            play = "播放", pause = "暂停", speed = "倍速", volume = "音量", subtitles = "字幕", loop = "循环",
            shuffle = "随机", next = "下一首", previous = "上一首", backgroundAudio = "后台播放", lyrics = "歌词",
            settings = "设置", active = "已开启", empty = "空", live = "直播", camera = "相机",
            wallpaper = "壁纸", gallery = "相册", custom = "自定义", defaultWord = "默认", back = "返回"
        ),
        "id" to buildDict(
            video = "Video", videos = "Video", music = "Musik", audio = "Audio", songs = "Lagu", song = "Lagu",
            search = "Cari", me = "Hub Saya", folders = "Folder", folder = "Folder", playlists = "Daftar Putar", history = "Riwayat",
            favourites = "Favorit", albums = "Album", artists = "Artis", all = "Semua",
            hybridShare = "Bagikan Hibrida", sendReceiveQr = "Kirim & Terima QR", privateVault = "Brankas Pribadi", pinLockedMedia = "Media Terkunci PIN",
            themes = "Tema", visualizer3d = "Visualiser 3D", mascots = "Maskot 3D", mascotGalleryTitle = "Maskot 3D Singa & Teddy",
            savedActive = "Maskot Aktif", size = "Ukuran", language = "Bahasa", pdfReader = "Pembaca PDF", fullScreenZoom = "Zoom Layar Penuh",
            videoToAudio = "Video ke Audio", extractMp3 = "Ekstrak Audio MP3", storageCleaner = "Pembersih", duplicateJunk = "Duplikat & Sampah",
            networkStream = "Stream Jaringan", statusSaver = "Pengunduh Status", equalizer = "Ekualiser", sleepTimer = "Pengatur Tidur", recycleBin = "Tempat Sampah",
            save = "Simpan", saved = "Tersimpan", cancel = "Batal", close = "Tutup", delete = "Hapus", rename = "Ganti Nama",
            details = "Detail", share = "Bagikan", send = "Kirim", receive = "Terima", scan = "Pindai", connect = "Hubungkan",
            paste = "Tempel", clear = "Bersihkan", selectAll = "Pilih Semua", selected = "Dipilih", sort = "Urutkan", refresh = "Segarkan",
            play = "Putar", pause = "Jeda", speed = "Kecepatan", volume = "Volume", subtitles = "Subtitel", loop = "Ulangi",
            shuffle = "Acak", next = "Berikutnya", previous = "Sebelumnya", backgroundAudio = "Audio Latar", lyrics = "Lirik",
            settings = "Pengaturan", active = "Aktif", empty = "Kosong", live = "Langsung", camera = "Kamera",
            wallpaper = "Wallpaper", gallery = "Galeri", custom = "Kustom", defaultWord = "Default", back = "Kembali"
        )
    )
}
