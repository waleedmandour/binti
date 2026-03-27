package com.binti.dilink.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Model Download Manager
 *
 * Manages downloading and verification of AI models from Google Drive.
 *
 * Models (Total: ~1.4GB):
 * - Wake Word: ya_binti_detector.tflite (~5MB)
 * - ASR: vosk-model-ar-mgb2 (~1.2GB) - Modern Standard Arabic
 * - NLU: egybert_tiny_int8.onnx (~25MB)
 * - TTS: ar-eg-female voice (~80MB)
 *
 * @author Dr. Waleed Mandour
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"

        // Google Drive Configuration
        private const val GOOGLE_DRIVE_BASE_URL = "https://drive.google.com/uc?export=download"

        // Google Drive File IDs
        private const val ASR_MODEL_FILE_ID = "1bK1-pUCH5xykvKdB7nB7mZ_1wBKH05qZ"
        // The user provided the same URL for both, which is likely a placeholder or they are in the same zip/folder.
        // However, for dilink_intent_map.json specifically, we'll use the ID from the provided URL.
        private const val INTENT_MAP_FILE_ID = "1bK1-pUCH5xykvKdB7nB7mZ_1wBKH05qZ" 

        // Preferences
        private const val PREFS_NAME = "binti_model_prefs"
        private const val KEY_LOCAL_MODEL_PATH = "local_model_path"
        private const val KEY_USE_LOCAL_MODELS = "use_local_models"

        // Model definitions
        val MODELS = listOf(
            ModelDefinition(
                name = "Arabic ASR (Vosk MGB2)",
                fileName = "vosk-model-ar-mgb2-0.4.zip",
                googleDriveId = ASR_MODEL_FILE_ID,
                downloadUrl = "$GOOGLE_DRIVE_BASE_URL&id=$ASR_MODEL_FILE_ID",
                relativePath = "models",
                sizeMB = 1247,
                sha256 = "",
                required = true,
                extract = true,
                description = "Arabic speech recognition model (Vosk MGB2 v0.4)"
            ),
            ModelDefinition(
                name = "Intent Patterns",
                fileName = "dilink_intent_map.json",
                googleDriveId = INTENT_MAP_FILE_ID,
                downloadUrl = "$GOOGLE_DRIVE_BASE_URL&id=$INTENT_MAP_FILE_ID",
                relativePath = "assets/commands",
                sizeMB = 0,
                sha256 = "",
                required = true,
                description = "Intent patterns for command matching"
            ),
            ModelDefinition(
                name = "Wake Word Detector",
                fileName = "ya_binti_detector.tflite",
                googleDriveId = "",
                downloadUrl = "",
                relativePath = "models/wake",
                sizeMB = 5,
                sha256 = "",
                required = false,
                description = "Detects 'يا بنتي' wake word (optional)"
            ),
            ModelDefinition(
                name = "Intent Classifier (TFLite)",
                fileName = "intent_classifier_eg.tflite",
                googleDriveId = "",
                downloadUrl = "",
                relativePath = "models/nlu",
                sizeMB = 10,
                sha256 = "",
                required = false,
                description = "Egyptian Arabic intent classification (TFLite)"
            ),
            ModelDefinition(
                name = "Egyptian TTS Voice (Coqui)",
                fileName = "ar-eg-female.zip",
                googleDriveId = "",
                downloadUrl = "",
                relativePath = "voices",
                sizeMB = 80,
                sha256 = "",
                required = false,
                extract = true,
                description = "Egyptian female voice for Coqui TTS (Embedded/Offline)"
            )
        )
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val modelsDir = File(context.filesDir, "models")
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setLocalModelPath(path: String) {
        prefs.edit().putString(KEY_LOCAL_MODEL_PATH, path).apply()
    }

    fun setUseLocalModels(useLocal: Boolean) {
        prefs.edit().putBoolean(KEY_USE_LOCAL_MODELS, useLocal).apply()
    }

    fun shouldUseLocalModels(): Boolean = prefs.getBoolean(KEY_USE_LOCAL_MODELS, false)

    suspend fun checkModelsStatus(): ModelStatus = withContext(Dispatchers.IO) {
        var readyCount = 0
        var totalSize = 0L

        for (model in MODELS) {
            val modelFile = File(context.filesDir, "${model.relativePath}/${model.fileName}")
            // Special check for extracted directories
            val isReady = if (model.extract) {
                val extractDir = File(context.filesDir, model.relativePath)
                extractDir.exists() && extractDir.isDirectory && extractDir.list()?.isNotEmpty() == true
            } else {
                modelFile.exists() && modelFile.length() > 0
            }

            if (isReady) {
                readyCount++
                if (modelFile.exists()) totalSize += modelFile.length()
            }
        }

        val requiredMissing = MODELS.filter { it.required }.any { model ->
            val modelFile = File(context.filesDir, "${model.relativePath}/${model.fileName}")
            if (model.extract) {
                val extractDir = File(context.filesDir, model.relativePath)
                !(extractDir.exists() && extractDir.isDirectory)
            } else {
                !modelFile.exists()
            }
        }

        ModelStatus(
            readyCount = readyCount,
            totalCount = MODELS.size,
            totalSizeMB = (totalSize / (1024 * 1024)).toInt(),
            allModelsReady = readyCount == MODELS.size,
            partialModelsReady = !requiredMissing,
            missingModels = MODELS.filter { model ->
                val modelFile = File(context.filesDir, "${model.relativePath}/${model.fileName}")
                if (model.extract) {
                    val extractDir = File(context.filesDir, model.relativePath)
                    !(extractDir.exists() && extractDir.isDirectory)
                } else {
                    !modelFile.exists()
                }
            }
        )
    }

    suspend fun downloadModels(
        onProgress: (Int, String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            modelsDir.mkdirs()
            val missingModels = checkModelsStatus().missingModels
            
            for ((index, model) in missingModels.withIndex()) {
                if (model.googleDriveId.isEmpty()) continue

                onProgress((index * 100) / missingModels.size, model.name)
                val modelFile = File(context.filesDir, "${model.relativePath}/${model.fileName}")
                modelFile.parentFile?.mkdirs()

                try {
                    downloadFromGoogleDrive(model, modelFile) { progress ->
                        val overallProgress = ((index * 100) + progress) / missingModels.size
                        onProgress(overallProgress, model.name)
                    }
                    if (model.extract) {
                        extractModel(model, modelFile)
                    }
                } catch (e: Exception) {
                    if (model.required) throw e
                    else Log.e(TAG, "Failed to download optional model ${model.name}", e)
                }
            }
            onComplete()
        } catch (e: Exception) {
            onError(e.message ?: "Unknown error")
        }
    }

    private suspend fun downloadFromGoogleDrive(
        model: ModelDefinition,
        targetFile: File,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        var downloadUrl = model.downloadUrl
        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ModelDownloadException("HTTP ${response.code}")

            val contentType = response.header("Content-Type", "") ?: ""
            if (contentType.contains("text/html")) {
                val html = response.body?.string() ?: ""
                val confirmToken = extractConfirmToken(html)
                if (confirmToken != null) {
                    val confirmedUrl = "$downloadUrl&confirm=$confirmToken"
                    val confirmRequest = Request.Builder().url(confirmedUrl).build()
                    httpClient.newCall(confirmRequest).execute().use { confirmedResponse ->
                        saveBody(confirmedResponse, targetFile, onProgress)
                    }
                } else {
                    throw ModelDownloadException("Could not bypass Google Drive warning")
                }
            } else {
                saveBody(response, targetFile, onProgress)
            }
        }
    }

    private fun saveBody(response: okhttp3.Response, targetFile: File, onProgress: (Int) -> Unit) {
        val body = response.body ?: throw ModelDownloadException("Empty body")
        val length = body.contentLength()
        val buffer = ByteArray(8192)
        var total = 0L
        FileOutputStream(targetFile).use { out ->
            body.byteStream().use { inp ->
                var read: Int
                while (inp.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                    total += read
                    if (length > 0) onProgress(((total * 100) / length).toInt())
                }
            }
        }
    }

    private fun extractConfirmToken(html: String): String? {
        val patterns = listOf(
            Regex("confirm=([0-9A-Za-z_]+)"),
            Regex("\"confirm\"\\s*:\\s*\"([^\"]+)\""),
            Regex("name=\"confirm\"\\s+value=\"([^\"]+)\"")
        )
        return patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1) }
    }

    private fun extractModel(model: ModelDefinition, zipFile: File) {
        val targetDir = zipFile.parentFile ?: return
        java.util.zip.ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry: java.util.zip.ZipEntry?
            while (zis.nextEntry.also { entry = it } != null) {
                val file = File(targetDir, entry!!.name)
                if (entry!!.isDirectory) file.mkdirs()
                else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { zis.copyTo(it) }
                }
                zis.closeEntry()
            }
        }
        zipFile.delete()
    }

    private fun verifyModel(model: ModelDefinition, file: File): Boolean {
        return file.exists() && file.length() > 0
    }
}

data class ModelDefinition(
    val name: String,
    val fileName: String,
    val googleDriveId: String = "",
    val downloadUrl: String,
    val relativePath: String,
    val sizeMB: Int,
    val sha256: String,
    val required: Boolean = false,
    val extract: Boolean = false,
    val description: String = ""
)

data class ModelStatus(
    val readyCount: Int,
    val totalCount: Int,
    val totalSizeMB: Int,
    val allModelsReady: Boolean,
    val partialModelsReady: Boolean,
    val missingModels: List<ModelDefinition>
)

class ModelDownloadException(message: String) : Exception(message)
