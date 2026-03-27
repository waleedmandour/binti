package com.binti.dilink.response

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.util.Pair
import com.binti.dilink.utils.HMSUtils
import com.huawei.hms.mlsdk.tts.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Egyptian TTS - Text-to-Speech with Egyptian Female Voice
 * 
 * Provides spoken responses in Egyptian Arabic dialect.
 * 
 * Implementation:
 * - Primary: Huawei ML Kit TTS (if available)
 * - Secondary: Coqui TTS Egyptian Female model (offline - future)
 * - Fallback: Android TTS with Arabic locale
 * 
 * @author Dr. Waleed Mandour
 */
class EgyptianTTS(private val context: Context) {

    companion object {
        private const val TAG = "EgyptianTTS"
        private const val DEFAULT_SPEECH_RATE = 0.95f
        private const val DEFAULT_PITCH = 1.0f
    }

    // Android TTS
    private var androidTTS: TextToSpeech? = null
    private var isAndroidTTSReady = false
    
    // Huawei ML Kit TTS
    private var mlTtsEngine: MLTtsEngine? = null
    private var isMlTtsReady = false
    
    // State
    private var isInitialized = false
    private var preferredProvider = HMSUtils.TTSProvider.ANDROID_TTS

    /**
     * Initialize TTS engine
     */
    suspend fun initialize() = withContext(Dispatchers.Main) {
        try {
            Log.d(TAG, "Initializing Egyptian TTS...")
            
            preferredProvider = HMSUtils.getPreferredTTSProvider(context)
            Log.i(TAG, "Preferred TTS Provider: $preferredProvider")

            if (preferredProvider == HMSUtils.TTSProvider.HUAWEI_ML_KIT) {
                initializeHuaweiTTS()
            }
            
            // Always initialize Android TTS as fallback
            initializeAndroidTTS()
            
            isInitialized = true
            Log.i(TAG, "✅ Egyptian TTS initialized")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TTS", e)
            throw e
        }
    }

    /**
     * Initialize Huawei ML Kit TTS
     */
    private fun initializeHuaweiTTS() {
        try {
            val mlConfigs = MLTtsConfig()
                .setLanguage(MLTtsConstants.TTS_LAN_AR_AR)
                .setPerson(MLTtsConstants.TTS_SPEAKER_FEMALE_AR)
                .setSpeed(1.0f)
                .setVolume(1.0f)
            
            mlTtsEngine = MLTtsEngine(mlConfigs)
            
            mlTtsEngine?.setTtsCallback(object : MLTtsCallback {
                override fun onError(taskId: String, err: MLTtsError) {
                    Log.e(TAG, "Huawei TTS Error: ${err.errorMsg}")
                }

                override fun onWarn(taskId: String, warn: MLTtsWarn) {
                    Log.w(TAG, "Huawei TTS Warning: ${warn.warnMsg}")
                }

                override fun onRangeStart(taskId: String, start: Int, end: Int) {}

                override fun onAudioAvailable(taskId: String, audioData: MLTtsAudioFragment, offset: Int, range: Pair<Int, Int>, bundle: Bundle) {}

                override fun onEvent(taskId: String, eventId: Int, bundle: Bundle) {
                    if (eventId == MLTtsConstants.EVENT_PLAY_START || eventId == MLTtsConstants.EVENT_PLAY_RESUME) {
                        // Handle events
                    }
                }
            })
            
            isMlTtsReady = true
            Log.d(TAG, "Huawei ML Kit TTS initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Huawei TTS", e)
            isMlTtsReady = false
        }
    }

    /**
     * Initialize Android TTS
     */
    private suspend fun initializeAndroidTTS() = suspendCancellableCoroutine<Unit> { continuation ->
        androidTTS = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = androidTTS?.setLanguage(Locale("ar", "EG"))
                
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    androidTTS?.setLanguage(Locale("ar"))
                }
                
                androidTTS?.setSpeechRate(DEFAULT_SPEECH_RATE)
                androidTTS?.setPitch(DEFAULT_PITCH)
                
                androidTTS?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {}
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {}
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.e(TAG, "Android TTS error code: $errorCode")
                    }
                })
                
                isAndroidTTSReady = true
                if (continuation.isActive) continuation.resume(Unit)
            } else {
                if (continuation.isActive) continuation.resumeWithException(Exception("Android TTS failed"))
            }
        }
    }

    /**
     * Speak text in Egyptian Arabic
     */
    suspend fun speak(text: String): Boolean {
        if (!isInitialized || text.isBlank()) return false
        
        val normalizedText = normalizeEgyptianText(text)
        Log.d(TAG, "🗣️ Speaking: $normalizedText")
        
        return withContext(Dispatchers.Main) {
            if (isMlTtsReady && preferredProvider == HMSUtils.TTSProvider.HUAWEI_ML_KIT) {
                val taskId = mlTtsEngine?.speak(normalizedText, MLTtsEngine.QUEUE_APPEND)
                !taskId.isNullOrEmpty()
            } else if (isAndroidTTSReady) {
                val utteranceId = System.currentTimeMillis().toString()
                val result = androidTTS?.speak(normalizedText, TextToSpeech.QUEUE_ADD, null, utteranceId)
                result == TextToSpeech.SUCCESS
            } else {
                false
            }
        }
    }

    private fun normalizeEgyptianText(text: String): String {
        return text.replace("إزاي", "ازاي")
            .replace("عايز", "عيز")
            .replace("عايزة", "عيزة")
            .replace(Regex("([،.؟!])"), " $1 ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun stop() {
        androidTTS?.stop()
        mlTtsEngine?.stop()
    }

    fun release() {
        androidTTS?.shutdown()
        mlTtsEngine?.shutdown()
        isInitialized = false
    }
}
