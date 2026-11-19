package com.example.helpers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

/**
 * Connects directly to Google Text-to-Speech (`com.google.android.tts`) with fallback to
 * the device's default TTS engine, plus crisp acoustic voice chimes ("Dhwani").
 */
class GoogleTtsVoiceEngine(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingUtterance: Triple<String, Boolean, (() -> Unit)?>? = null
    private val utteranceCallbacks = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()

    init {
        initGoogleTts()
    }

    private fun attachProgressListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                if (utteranceId != null) {
                    val cb = utteranceCallbacks.remove(utteranceId)
                    if (cb != null) mainHandler.post { cb() }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId != null) {
                    val cb = utteranceCallbacks.remove(utteranceId)
                    if (cb != null) mainHandler.post { cb() }
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId != null) {
                    val cb = utteranceCallbacks.remove(utteranceId)
                    if (cb != null) mainHandler.post { cb() }
                }
            }
        })
    }

    private fun initGoogleTts() {
        try {
            // Connect to Google Text-to-Speech ("com.google.android.tts") first
            var primaryEngine: TextToSpeech? = null
            primaryEngine = TextToSpeech(
                appContext,
                { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        isReady = true
                        primaryEngine?.let { attachProgressListener(it) }
                        configureLanguage(isHindi = false)
                        pendingUtterance?.let { (text, hindi, onDone) ->
                            pendingUtterance = null
                            speakDhwani(text, hindi, playChime = false, onDone = onDone)
                        }
                    } else {
                        // Shut down failed primary instance cleanly before fallback
                        runCatching { primaryEngine?.shutdown() }
                        runCatching {
                            var fallbackEngine: TextToSpeech? = null
                            fallbackEngine = TextToSpeech(appContext) { fallbackStatus ->
                                if (fallbackStatus == TextToSpeech.SUCCESS) {
                                    isReady = true
                                    fallbackEngine?.let { attachProgressListener(it) }
                                    pendingUtterance?.let { (text, hindi, onDone) ->
                                        pendingUtterance = null
                                        speakDhwani(text, hindi, playChime = false, onDone = onDone)
                                    }
                                } else {
                                    pendingUtterance?.third?.let { cb -> mainHandler.post { cb() } }
                                    pendingUtterance = null
                                }
                            }
                            tts = fallbackEngine
                        }
                    }
                },
                "com.google.android.tts"
            )
            tts = primaryEngine
        } catch (_: Exception) {
            runCatching {
                var fallbackEngine: TextToSpeech? = null
                fallbackEngine = TextToSpeech(appContext) { status ->
                    isReady = (status == TextToSpeech.SUCCESS)
                    if (isReady) fallbackEngine?.let { attachProgressListener(it) }
                }
                tts = fallbackEngine
            }
        }
    }

    private fun configureLanguage(isHindi: Boolean) {
        val engine = tts ?: return
        runCatching {
            val targetLocale = if (isHindi) Locale("hi", "IN") else Locale("en", "IN")
            val result = engine.setLanguage(targetLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.US)
            }
            engine.setPitch(1.03f)
            engine.setSpeechRate(0.98f)
        }
    }

    /**
     * Plays a crisp voice assistant chime ("Dhwani") and always releases ToneGenerator
     * native audio handles once the tone completes.
     */
    fun playVoiceChime(isStartPrompt: Boolean = true) {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
            val toneType = if (isStartPrompt) {
                ToneGenerator.TONE_PROP_BEEP
            } else {
                ToneGenerator.TONE_PROP_ACK
            }
            toneGen.startTone(toneType, 140)
            mainHandler.postDelayed({
                runCatching { toneGen.release() }
            }, 220L)
        } catch (_: Exception) {
        }
    }

    /**
     * Speaks out the given text clearly using Google Text-to-Speech ("Dhwani").
     * Optionally invokes [onDone] on the main thread when speech finishes (or fails/times out).
     */
    fun speakDhwani(
        text: String,
        isHindi: Boolean = false,
        playChime: Boolean = true,
        onDone: (() -> Unit)? = null
    ) {
        if (text.isBlank()) {
            onDone?.let { mainHandler.post(it) }
            return
        }
        if (playChime) {
            playVoiceChime(isStartPrompt = false)
        }
        val engine = tts
        if (!isReady || engine == null) {
            pendingUtterance = Triple(text, isHindi, onDone)
            if (onDone != null) {
                // Safety cap if engine takes too long to initialize so mic/UI never stalls
                mainHandler.postDelayed({
                    if (pendingUtterance?.third === onDone) {
                        pendingUtterance = null
                        onDone()
                    }
                }, 1400L)
            }
            return
        }
        try {
            configureLanguage(isHindi)
            val utteranceId = "hybrid_tts_${System.currentTimeMillis()}_${text.hashCode()}"
            if (onDone != null) {
                utteranceCallbacks[utteranceId] = onDone
                // Safety cap in case an OEM TTS engine never fires onDone
                mainHandler.postDelayed({
                    val remaining = utteranceCallbacks.remove(utteranceId)
                    if (remaining != null) remaining()
                }, 4200L)
            }
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            val res = engine.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                params,
                utteranceId
            )
            if (res != TextToSpeech.SUCCESS && onDone != null) {
                val cb = utteranceCallbacks.remove(utteranceId)
                if (cb != null) mainHandler.post(cb)
            }
        } catch (_: Exception) {
            onDone?.let { mainHandler.post(it) }
        }
    }

    fun stopSpeaking() {
        runCatching { tts?.stop() }
        val pending = utteranceCallbacks.values.toList()
        utteranceCallbacks.clear()
        pending.forEach { cb -> mainHandler.post { cb() } }
    }

    fun shutdown() {
        utteranceCallbacks.clear()
        pendingUtterance = null
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        isReady = false
    }
}

@Composable
fun rememberGoogleTtsVoiceEngine(): GoogleTtsVoiceEngine {
    val context = LocalContext.current
    val engine = remember(context) { GoogleTtsVoiceEngine(context) }
    DisposableEffect(engine) {
        onDispose {
            engine.shutdown()
        }
    }
    return engine
}

/**
 * Right-Hand Side Search Bar Action Buttons ("Bolkar Search" Mic + Google Text-to-Speech "Dhwani" Speaker + Clear):
 * Drop this directly into the `trailingIcon` slot of any `OutlinedTextField` search bar!
 */
@Composable
fun VoiceAndTtsSearchBarActions(
    currentQuery: String,
    onQueryChange: (String) -> Unit,
    onSearchConfirmed: (String) -> Unit = {},
    isHindi: Boolean = false,
    matchingCount: Int = -1,
    topMatchTitle: String? = null,
    suggestedVoiceTerms: List<String> = emptyList(),
    accentColor: Color = Color(0xFF00E5FF)
) {
    val context = LocalContext.current
    val ttsEngine = rememberGoogleTtsVoiceEngine()
    var showLiveVoiceSheet by remember { mutableStateOf(false) }
    var isSpeakingPulse by remember { mutableStateOf(false) }

    // 1. System Google Voice Search Activity Launcher (Fallback / Direct Google Popup)
    val googleSpeechIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenList = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val bestSpoken = spokenList?.firstOrNull()?.trim().orEmpty()
            if (bestSpoken.isNotEmpty()) {
                onQueryChange(bestSpoken)
                onSearchConfirmed(bestSpoken)
                showLiveVoiceSheet = false
                val spokenFeedback = if (isHindi) {
                    "$bestSpoken खोजा गया"
                } else {
                    "Searching for $bestSpoken"
                }
                ttsEngine.speakDhwani(spokenFeedback, isHindi = isHindi, playChime = true)
            }
        }
    }

    fun launchSystemGoogleVoiceDialog() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isHindi) "hi-IN" else Locale.getDefault().toLanguageTag())
                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    if (isHindi) "🎤 बोलकर वीडियो या गाना खोजें..." else "🎤 Speak to search videos or songs..."
                )
            }
            googleSpeechIntentLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                if (isHindi) "इन-ऐप लाइव वॉइस सर्च सक्रिय है" else "In-app Live Voice Search is active",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 2. Runtime RECORD_AUDIO Permission Launcher for Live In-App SpeechRecognizer
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        ttsEngine.playVoiceChime(isStartPrompt = true)
        if (granted) {
            // LiveBolkarVoiceSearchDialog sequences the TTS prompt BEFORE starting SpeechRecognizer
            showLiveVoiceSheet = true
        } else {
            showLiveVoiceSheet = true
            launchSystemGoogleVoiceDialog()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "voice_mic_glow")
    val micGlowScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_pulse"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(end = 6.dp)
    ) {
        // A. Clear ('X') Button when query is not empty
        if (currentQuery.isNotEmpty()) {
            IconButton(
                onClick = {
                    onQueryChange("")
                    ttsEngine.playVoiceChime(isStartPrompt = false)
                },
                modifier = Modifier
                    .size(32.dp)
                    .testTag("search_clear_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear Search",
                    tint = Color.White.copy(alpha = 0.80f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // B. Google Text-to-Speech "Dhwani" (Speak Aloud) Button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF00E676).copy(alpha = 0.25f),
                            Color(0xFF00B0FF).copy(alpha = 0.22f)
                        )
                    )
                )
                .border(
                    1.dp,
                    if (isSpeakingPulse) Color(0xFF00E676) else Color(0xFF00E676).copy(alpha = 0.65f),
                    CircleShape
                )
                .clickable {
                    isSpeakingPulse = true
                    val speechMessage = when {
                        currentQuery.isNotBlank() && matchingCount >= 0 -> {
                            val topHint = if (!topMatchTitle.isNullOrBlank()) ". Top match: $topMatchTitle" else ""
                            if (isHindi) {
                                "आपने खोजा है: $currentQuery। कुल $matchingCount परिणाम मिले हैं$topHint"
                            } else {
                                "Searching for $currentQuery. Found $matchingCount matching files$topHint"
                            }
                        }
                        currentQuery.isNotBlank() -> {
                            if (isHindi) "खोजा जा रहा है: $currentQuery" else "Searching for $currentQuery"
                        }
                        matchingCount > 0 -> {
                            if (isHindi) {
                                "बोलकर खोजने के लिए माइक दबाएं। अभी $matchingCount मीडिया फ़ाइलें उपलब्ध हैं।"
                            } else {
                                "Tap the microphone to search by voice. $matchingCount media files ready."
                            }
                        }
                        else -> {
                            if (isHindi) "गूगल वॉइस और ध्वनि सर्च तैयार है। माइक दबाकर बोलें।"
                            else "Google Voice and Text to Speech ready. Tap mic to speak."
                        }
                    }
                    ttsEngine.speakDhwani(
                        text = speechMessage,
                        isHindi = isHindi,
                        playChime = true,
                        onDone = { isSpeakingPulse = false }
                    )
                    Toast.makeText(
                        context,
                        "🔊 $speechMessage",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .testTag("search_google_tts_dhwani_btn"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Google Text-to-Speech Dhwani",
                tint = Color(0xFF00E676),
                modifier = Modifier.size(18.dp)
            )
        }

        // C. Right-Hand Side "Bolkar Search" (Voice Search Mic) Button
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            accentColor,
                            Color(0xFF2979FF)
                        )
                    )
                )
                .border(1.2.dp, Color.White.copy(alpha = 0.75f * micGlowScale.coerceIn(0.8f, 1f)), CircleShape)
                .clickable {
                    val hasMicPerm = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasMicPerm) {
                        ttsEngine.playVoiceChime(isStartPrompt = true)
                        showLiveVoiceSheet = true
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                .testTag("search_voice_mic_btn"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Bolkar Voice Search",
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (showLiveVoiceSheet) {
        LiveBolkarVoiceSearchDialog(
            isHindi = isHindi,
            accentColor = accentColor,
            suggestedTerms = suggestedVoiceTerms,
            ttsEngine = ttsEngine,
            onDismiss = { showLiveVoiceSheet = false },
            onOpenSystemGooglePopup = { launchSystemGoogleVoiceDialog() },
            onVoiceResultSelected = { recognizedText ->
                val clean = recognizedText.trim()
                if (clean.isNotEmpty()) {
                    onQueryChange(clean)
                    onSearchConfirmed(clean)
                    val dhwaniReply = if (isHindi) {
                        "ध्वनि खोज: $clean"
                    } else {
                        "Voice search: $clean"
                    }
                    ttsEngine.speakDhwani(dhwaniReply, isHindi = isHindi, playChime = true)
                }
                showLiveVoiceSheet = false
            }
        )
    }
}

/**
 * Interactive Live "Bolkar Search" + Google SpeechRecognizer & Google Text-to-Speech Dialog:
 * - Streams real-time microphone audio into Android's `SpeechRecognizer`
 * - Shows a 15-bar live voice waveform reacting to the user's voice (`onRmsChanged`)
 * - Speaks out confirmation via Google Text-to-Speech ("Dhwani")
 * - Includes a 1-tap button for the native Google Voice Popup (`RecognizerIntent`) and quick voice chips!
 */
@Composable
private fun LiveBolkarVoiceSearchDialog(
    isHindi: Boolean,
    accentColor: Color,
    suggestedTerms: List<String>,
    ttsEngine: GoogleTtsVoiceEngine,
    onDismiss: () -> Unit,
    onOpenSystemGooglePopup: () -> Unit,
    onVoiceResultSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var liveTranscript by remember { mutableStateOf("") }
    var statusLabel by remember {
        mutableStateOf(
            if (isHindi) "🎤 सुन रहा हूँ... अभी बोलें (Google Voice + TTS)"
            else "🎤 Listening... Speak now (Google Voice + TTS)"
        )
    }
    var rmsLevel by remember { mutableFloatStateOf(2.5f) }
    var isListeningActive by remember { mutableStateOf(true) }
    var recognizerRef by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun startInAppListening() {
        try {
            recognizerRef?.destroy()
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                statusLabel = if (isHindi) {
                    "गूगल पॉपअप या नीचे दिए गए वॉइस विकल्प चुनें"
                } else {
                    "Tap 'Google Voice Popup' or speak/pick below"
                }
                isListeningActive = false
                return
            }
            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            recognizerRef = sr
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListeningActive = true
                    statusLabel = if (isHindi) "🎤 अब बोलिए... (Listening Live)" else "🎤 Speak now... (Listening Live)"
                }

                override fun onBeginningOfSpeech() {
                    isListeningActive = true
                }

                override fun onRmsChanged(rmsdB: Float) {
                    rmsLevel = rmsdB.coerceIn(0.5f, 12f)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListeningActive = false
                    statusLabel = if (isHindi) "प्रोसेस हो रहा है..." else "Processing voice..."
                }

                override fun onError(error: Int) {
                    isListeningActive = false
                    if (liveTranscript.isNotBlank()) {
                        onVoiceResultSelected(liveTranscript)
                    } else {
                        statusLabel = if (isHindi) {
                            "फिर से बोलने के लिए माइक टैप करें या 'Google Voice' दबाएं"
                        } else {
                            "Tap Mic to speak again or use 'Google Voice Popup'"
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListeningActive = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val topMatch = matches?.firstOrNull()?.trim().orEmpty()
                    if (topMatch.isNotEmpty()) {
                        liveTranscript = topMatch
                        onVoiceResultSelected(topMatch)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isNotEmpty()) {
                        liveTranscript = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isHindi) "hi-IN" else Locale.getDefault().toLanguageTag())
            }
            sr.startListening(recognizerIntent)
        } catch (_: Exception) {
            isListeningActive = false
        }
    }

    DisposableEffect(Unit) {
        var disposed = false
        statusLabel = if (isHindi) "🔊 तैयार हो रहा है..." else "🔊 Preparing mic..."
        isListeningActive = false
        // Sequence Google TTS prompt completion BEFORE opening the microphone so SpeechRecognizer never hears the app's own voice
        ttsEngine.speakDhwani(
            text = if (isHindi) "बोलिए" else "Speak now",
            isHindi = isHindi,
            playChime = false,
            onDone = {
                if (!disposed) {
                    startInAppListening()
                }
            }
        )
        onDispose {
            disposed = true
            ttsEngine.stopSpeaking()
            runCatching {
                recognizerRef?.stopListening()
                recognizerRef?.destroy()
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "voice_wave_anim")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF0A1222),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.6.dp,
                    brush = Brush.linearGradient(listOf(accentColor, Color(0xFF00E676), Color(0xFF2979FF))),
                    shape = RoundedCornerShape(26.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isHindi) "🎤 बोलकर खोजें (Google Voice + ध्वनि)" else "🎤 Bolkar Search (Google Voice + TTS)",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isHindi) "Google Text-to-Speech ध्वनि से जुड़ा हुआ" else "Connected with Google Text-to-Speech Dhwani",
                                color = Color(0xFF00E676),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
                    }
                }

                // Pulsing 3D Mic Orb + 15-Bar Live Voice Waveform
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF101C34))
                        .clickable {
                            ttsEngine.stopSpeaking()
                            ttsEngine.playVoiceChime(isStartPrompt = true)
                            startInAppListening()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val barCount = 15
                        val totalW = w * 0.82f
                        val startX = (w - totalW) * 0.5f
                        val gap = totalW / barCount
                        val activeGain = if (isListeningActive) (0.45f + (rmsLevel / 12f) * 0.65f) else 0.22f

                        for (i in 0 until barCount) {
                            val centerWeight = 1f - (abs(i - barCount / 2).toFloat() / (barCount / 2 + 1))
                            val wave = abs(sin(wavePhase + i * 0.55f))
                            val barH = (h * 0.72f * wave * centerWeight * activeGain).coerceAtLeast(8.dp.toPx())
                            val bx = startX + i * gap + gap * 0.5f
                            drawLine(
                                color = if (i % 2 == 0) accentColor else Color(0xFF00E676),
                                start = Offset(bx, h * 0.5f - barH * 0.5f),
                                end = Offset(bx, h * 0.5f + barH * 0.5f),
                                strokeWidth = 4.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(accentColor, Color(0xFF2979FF))
                                )
                            )
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Tap to Speak",
                            tint = Color.Black,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Live Recognized Speech Transcript Box
                Text(
                    text = if (liveTranscript.isNotBlank()) "\"$liveTranscript\"" else statusLabel,
                    color = if (liveTranscript.isNotBlank()) Color(0xFF00E676) else Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Quick Voice Suggestions from actual library so user can also 1-tap test TTS + Search
                if (suggestedTerms.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isHindi) "⚡ तुरंत बोलें या टैप करें (ध्वनि के साथ):" else "⚡ Quick Voice & Dhwani Picks:",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            suggestedTerms.take(8).forEach { term ->
                                Surface(
                                    color = accentColor.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(50))
                                        .clickable {
                                            onVoiceResultSelected(term)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = null,
                                            tint = Color(0xFF00E676),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = term,
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Action Buttons: [Google Voice Popup] + [Test Google TTS Dhwani]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onOpenSystemGooglePopup,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHindi) "गूगल वॉइस पॉपअप" else "Google Voice",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.5.sp
                        )
                    }

                    Button(
                        onClick = {
                            val sampleText = if (liveTranscript.isNotBlank()) {
                                if (isHindi) "खोजा जा रहा है: $liveTranscript" else "Searching for $liveTranscript"
                            } else {
                                if (isHindi) "हाइब्रिड प्लेयर गूगल टेक्स्ट टू स्पीच ध्वनि चालू है। कोई भी गाना या वीडियो बोलकर खोजें।"
                                else "Hybrid Player Google Text to Speech voice is connected. Speak any video or song name."
                            }
                            ttsEngine.speakDhwani(sampleText, isHindi = isHindi, playChime = true)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E676),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHindi) "ध्वनि सुनें (TTS)" else "Hear TTS Voice",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }
    }
}
