package com.example.data.hardware

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.example.data.model.CompatibilityVerdict
import com.example.data.model.ModelItem
import com.example.data.model.ModelType
import java.io.File

/**
 * Detects device hardware profile (RAM, CPU, Architecture, Storage)
 * and determines model compatibility and recommendations.
 */
data class DeviceHardwareProfile(
    val totalRamMb: Int,
    val availableRamMb: Int,
    val cpuArch: String,
    val is64Bit: Boolean,
    val androidVersion: Int,
    val availableStorageMb: Int
)

class HardwareDetector(private val context: Context) {

    fun getHardwareProfile(): DeviceHardwareProfile {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val totalRamMb = (memInfo.totalMem / (1024 * 1024)).toInt().coerceAtLeast(1024)
        val availRamMb = (memInfo.availMem / (1024 * 1024)).toInt().coerceAtLeast(512)

        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val is64Bit = primaryAbi.contains("64") || primaryAbi.contains("v8")

        val freeStorageMb = (context.filesDir.usableSpace / (1024 * 1024)).toInt()

        return DeviceHardwareProfile(
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            cpuArch = primaryAbi,
            is64Bit = is64Bit,
            androidVersion = Build.VERSION.SDK_INT,
            availableStorageMb = freeStorageMb
        )
    }

    /**
     * Evaluates compatibility of a model against host device specs.
     */
    fun evaluateCompatibility(model: ModelItem): CompatibilityVerdict {
        val profile = getHardwareProfile()

        // Storage check
        val modelStorageMb = (model.fileSizeBytes / (1024 * 1024)).toInt()
        if (profile.availableStorageMb < modelStorageMb + 50) {
            return CompatibilityVerdict.UNSUPPORTED
        }

        // RAM check
        if (profile.totalRamMb < model.minimumRamMb) {
            return CompatibilityVerdict.UNSUPPORTED
        }

        if (profile.availableRamMb < model.minimumRamMb) {
            return CompatibilityVerdict.NOT_RECOMMENDED
        }

        if (profile.totalRamMb >= model.recommendedRamMb && model.isRecommended) {
            return CompatibilityVerdict.RECOMMENDED
        }

        return CompatibilityVerdict.COMPATIBLE
    }

    /**
     * Recommends the optimal model id for a specific category based on device capability.
     */
    fun getRecommendedModelId(type: ModelType): String {
        val profile = getHardwareProfile()
        return when (type) {
            ModelType.STT -> "stt_whisper_tiny_q8"
            ModelType.LLM -> {
                if (profile.totalRamMb >= 6000) {
                    "llm_llama_3_2_1b_q4"
                } else if (profile.totalRamMb >= 3500) {
                    "llm_mobiles2s_compact_q4"
                } else {
                    "llm_smollm_135m_q4"
                }
            }
            ModelType.TTS -> "tts_kokoro_82m"
            ModelType.VOICE -> "tts_kokoro_82m"
        }
    }
}
