package com.example.data.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.example.data.model.ModelItem
import java.io.File

/**
 * Manages model storage, disk space validation, and file cleanup for local AI models.
 */
class StorageManager(private val context: Context) {

    companion object {
        private const val TAG = "StorageManager"
        private const val MODELS_DIR_NAME = "models"
        private const val SAFETY_MARGIN_BYTES = 50 * 1024 * 1024L // 50 MB buffer
    }

    private val modelsDir: File
        get() {
            val dir = File(context.filesDir, MODELS_DIR_NAME)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    fun getModelDirectory(): File = modelsDir

    fun getModelFile(modelFileName: String): File = File(modelsDir, modelFileName)

    fun getPartialDownloadFile(modelFileName: String): File = File(modelsDir, "$modelFileName.part")

    /**
     * Checks if the model file is present and has non-zero size.
     */
    fun isModelInstalled(model: ModelItem): Boolean {
        val file = getModelFile(model.localFileName)
        return file.exists() && file.length() > 0L
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
     */
    fun validateStorageForDownload(modelSizeBytes: Long): Boolean {
        val freeBytes = getAvailableStorageBytes()
        val requiredBytes = modelSizeBytes + SAFETY_MARGIN_BYTES
        val isAdequate = freeBytes >= requiredBytes
        if (!isAdequate) {
            Log.w(TAG, "Insufficient storage for model: required=$requiredBytes, available=$freeBytes")
        }
        return isAdequate
    }

    /**
     * Deletes an installed model file and its partial download if present.
     */
    fun deleteModel(model: ModelItem): Boolean {
        return try {
            val file = getModelFile(model.localFileName)
            val partFile = getPartialDownloadFile(model.localFileName)
            var deleted = false
            if (file.exists()) {
                deleted = file.delete()
            }
            if (partFile.exists()) {
                partFile.delete()
            }
            Log.i(TAG, "Deleted model: ${model.name}, success=$deleted")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete model ${model.name}", e)
            false
        }
    }

    /**
     * Deletes all files in the models directory.
     */
    fun deleteAllModels(): Boolean {
        return try {
            if (modelsDir.exists()) {
                modelsDir.listFiles()?.forEach { it.delete() }
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
