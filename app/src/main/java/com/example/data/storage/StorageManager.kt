package com.example.data.storage

import android.content.Context
import android.os.StatFs
import android.util.Log
import com.example.data.model.ModelItem
import com.example.data.model.ModelType
import java.io.File

/**
 * Detailed verdict of storage validation before downloading a model.
 */
data class StorageValidationResult(
    val isAdequate: Boolean,
    val modelSizeBytes: Long,
    val safetyMarginBytes: Long,
    val requiredBytes: Long,
    val availableBytes: Long,
    val formattedMessage: String
)

/**
 * Manages isolated on-device model directories, disk space validation, and file operations.
 * Structure:
 *   /files/models/
 *     ├── stt/
 *     ├── llm/
 *     ├── tts/
 *     └── temp/
 */
class StorageManager(private val context: Context) {

    companion object {
        private const val TAG = "StorageManager"
        const val MODELS_DIR_NAME = "models"
        const val STT_DIR_NAME = "stt"
        const val LLM_DIR_NAME = "llm"
        const val TTS_DIR_NAME = "tts"
        const val TEMP_DIR_NAME = "temp"

        const val SAFETY_MARGIN_BYTES = 50 * 1024 * 1024L // 50 MB buffer minimum
    }

    val modelsDir: File
        get() = getOrCreateDir(File(context.filesDir, MODELS_DIR_NAME))

    val sttDir: File
        get() = getOrCreateDir(File(modelsDir, STT_DIR_NAME))

    val llmDir: File
        get() = getOrCreateDir(File(modelsDir, LLM_DIR_NAME))

    val ttsDir: File
        get() = getOrCreateDir(File(modelsDir, TTS_DIR_NAME))

    val tempDir: File
        get() = getOrCreateDir(File(modelsDir, TEMP_DIR_NAME))

    private fun getOrCreateDir(dir: File): File {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getModelDirectory(): File = modelsDir

    fun getCategoryDirectory(type: ModelType): File = when (type) {
        ModelType.STT -> sttDir
        ModelType.LLM -> llmDir
        ModelType.TTS, ModelType.VOICE -> ttsDir
    }

    /**
     * Resolves the primary storage file for a model.
     * Checks isolated category folder first, then backward-compatible root models folder.
     */
    fun getModelFile(model: ModelItem): File {
        val catDir = getCategoryDirectory(model.type)
        val isolatedFile = File(catDir, model.localFileName)
        if (isolatedFile.exists() && isolatedFile.length() > 0L) {
            return isolatedFile
        }
        val legacyFile = File(modelsDir, model.localFileName)
        if (legacyFile.exists() && legacyFile.length() > 0L) {
            return legacyFile
        }
        return isolatedFile
    }

    /**
     * Simple lookup for legacy string file names.
     */
    fun getModelFile(modelFileName: String): File {
        val isolatedStt = File(sttDir, modelFileName)
        if (isolatedStt.exists()) return isolatedStt
        val isolatedLlm = File(llmDir, modelFileName)
        if (isolatedLlm.exists()) return isolatedLlm
        val isolatedTts = File(ttsDir, modelFileName)
        if (isolatedTts.exists()) return isolatedTts
        val legacy = File(modelsDir, modelFileName)
        if (legacy.exists()) return legacy
        return File(modelsDir, modelFileName)
    }

    /**
     * Partial download file in the isolated temp directory.
     * Temporary download file name: <model_filename>.download
     */
    fun getPartialDownloadFile(modelFileName: String): File {
        return File(tempDir, "$modelFileName.download")
    }

    /**
     * Atomically promotes a verified file from temp directory to the isolated category folder.
     */
    fun promoteTempToFinal(model: ModelItem, tempFile: File): File {
        val targetDir = getCategoryDirectory(model.type)
        val destinationFile = File(targetDir, model.localFileName)

        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val success = tempFile.renameTo(destinationFile)
        if (!success) {
            // Fallback byte copy if atomic rename across filesystems is needed
            tempFile.copyTo(destinationFile, overwrite = true)
            tempFile.delete()
        }
        Log.i(TAG, "Promoted verified model ${model.name} to ${destinationFile.absolutePath}")
        return destinationFile
    }

    /**
     * Checks if the model file (and all companion assets) are present in its isolated folder,
     * has genuine binary size (not a synthetic dummy/placeholder), and is valid.
     */
    fun isModelInstalled(model: ModelItem): Boolean {
        val file = getModelFile(model)
        if (!file.exists() || file.length() == 0L) {
            return false
        }

        // Detect and quarantine/purge old synthetic dummy stubs (e.g. 64KB placeholders)
        val minExpectedSize = if (model.fileSizeBytes > 0) (model.fileSizeBytes * 0.85).toLong() else 1024 * 1024L
        if (file.length() < minExpectedSize || file.length() < 1024 * 1024L) {
            Log.w(TAG, "PURGING_SYNTHETIC_OR_CORRUPT_MODEL: ${file.name} (size=${file.length()}, expected=${model.fileSizeBytes})")
            file.delete()
            return false
        }

        // For models with companion assets (e.g. Kokoro TTS), verify companion files exist with real binary size
        if (model.companionAssets.isNotEmpty()) {
            val parentDir = file.parentFile ?: return false
            for (asset in model.companionAssets) {
                if (asset.isRequired) {
                    val assetFile = File(parentDir, asset.filename)
                    if (!assetFile.exists() || assetFile.length() == 0L) {
                        return false
                    }
                    // Reject synthetic placeholder companion stubs
                    if (asset.filename == "voices.bin" && assetFile.length() < 1024 * 1024L) {
                        Log.w(TAG, "PURGING_SYNTHETIC_ASSET: ${asset.filename} (${assetFile.length()} bytes)")
                        assetFile.delete()
                        return false
                    }
                    if (asset.filename == "tokens.txt" && assetFile.length() < 500L) {
                        Log.w(TAG, "PURGING_SYNTHETIC_ASSET: ${asset.filename} (${assetFile.length()} bytes)")
                        assetFile.delete()
                        return false
                    }
                }
            }
        }

        return true
    }

    /**
     * Returns total bytes occupied by installed models in the app models directory.
     */
    fun getUsedByModelsBytes(): Long {
        if (!modelsDir.exists()) return 0L
        return modelsDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }

    /**
     * Returns available internal storage bytes on the device.
     */
    fun getAvailableStorageBytes(): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            val bytes = stat.availableBytes
            if (bytes > 0) bytes else context.filesDir.usableSpace.coerceAtLeast(4L * 1024 * 1024 * 1024)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query available storage", e)
            context.filesDir.usableSpace.coerceAtLeast(4L * 1024 * 1024 * 1024)
        }
    }

    /**
     * Returns total internal storage bytes on the device.
     */
    fun getTotalStorageBytes(): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.totalBytes
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query total storage", e)
            context.filesDir.totalSpace
        }
    }

    /**
     * Validates if there is enough free disk space to safely download and install a model.
     * Calculation: requiredStorage = modelSize + safetyMargin (50MB min)
     */
    fun checkStorage(modelSizeBytes: Long): StorageValidationResult {
        val freeBytes = getAvailableStorageBytes()
        val requiredBytes = modelSizeBytes + SAFETY_MARGIN_BYTES
        val isAdequate = freeBytes >= requiredBytes
        val msg = if (isAdequate) {
            "Storage adequate. Required: ${formatBytes(requiredBytes)}, Available: ${formatBytes(freeBytes)}"
        } else {
            "Insufficient storage. Required: ${formatBytes(requiredBytes)}, Available: ${formatBytes(freeBytes)}"
        }
        return StorageValidationResult(
            isAdequate = isAdequate,
            modelSizeBytes = modelSizeBytes,
            safetyMarginBytes = SAFETY_MARGIN_BYTES,
            requiredBytes = requiredBytes,
            availableBytes = freeBytes,
            formattedMessage = msg
        )
    }

    fun validateStorageForDownload(modelSizeBytes: Long): Boolean {
        return checkStorage(modelSizeBytes).isAdequate
    }

    /**
     * Deletes an installed model file and its partial download if present.
     */
    fun deleteModel(model: ModelItem): Boolean {
        return try {
            val file = getModelFile(model)
            val partFile = getPartialDownloadFile(model.localFileName)
            val legacyPart = File(modelsDir, "${model.localFileName}.part")

            var deleted = false
            if (file.exists()) {
                deleted = file.delete()
            }
            if (partFile.exists()) {
                partFile.delete()
            }
            if (legacyPart.exists()) {
                legacyPart.delete()
            }
            Log.i(TAG, "Deleted model: ${model.name}, success=$deleted")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete model ${model.name}", e)
            false
        }
    }

    /**
     * Deletes all files in the models directory and subdirectories.
     */
    fun deleteAllModels(): Boolean {
        return try {
            if (modelsDir.exists()) {
                modelsDir.listFiles()?.forEach { child ->
                    if (child.isDirectory) {
                        child.listFiles()?.forEach { it.delete() }
                    } else {
                        child.delete()
                    }
                }
            }
            Log.i(TAG, "All models deleted successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete all models", e)
            false
        }
    }

    fun formatBytes(bytes: Long): String {
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb >= 1024) {
            String.format("%.2f GB", mb / 1024)
        } else {
            String.format("%.1f MB", mb)
        }
    }
}
