package com.example

import com.example.data.model.ModelDownloadStatus
import com.example.data.model.ModelRegistry
import com.example.data.model.ModelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBridgeUnitTest {

    @Test
    fun testModelRegistryContainsRequiredNativeModels() {
        val models = ModelRegistry.getAllModels()
        assertTrue("Model registry must not be empty", models.isNotEmpty())

        val sttModel = models.find { it.type == ModelType.STT && it.isRecommended }
        assertNotNull("Recommended STT model must exist", sttModel)
        assertEquals("stt_whisper_tiny_q8", sttModel?.id)

        val llmModel = models.find { it.type == ModelType.LLM && it.isRecommended }
        assertNotNull("Recommended LLM model must exist", llmModel)
        assertEquals("llm_smollm_135m_q4", llmModel?.id)
        assertEquals("GGUF", llmModel?.format)

        val ttsModel = models.find { it.type == ModelType.TTS && it.isRecommended }
        assertNotNull("Recommended TTS model must exist", ttsModel)
        assertEquals("tts_kokoro_82m", ttsModel?.id)
    }

    @Test
    fun testModelDownloadStatusTransitions() {
        // Verify strict READY gate semantics
        val notDownloaded = ModelDownloadStatus.NOT_DOWNLOADED
        val verifying = ModelDownloadStatus.VERIFYING
        val verified = ModelDownloadStatus.VERIFIED
        val ready = ModelDownloadStatus.READY
        val failed = ModelDownloadStatus.FAILED

        assertFalse(notDownloaded == ready)
        assertFalse(verifying == ready)
        assertFalse(verified == ready)
        assertTrue(ready.displayLabel == "Ready")
        assertTrue(failed.displayLabel == "Failed")
    }

    @Test
    fun testModelRegistryChecksumsAreValidSha256() {
        val models = ModelRegistry.getAllModels()
        val sha256Regex = Regex("^[a-fA-F0-9]{64}$")
        models.forEach { model ->
            assertTrue(
                "Checksum for ${model.id} must be a 64-character SHA-256 hex string",
                sha256Regex.matches(model.checksumSha256)
            )
        }
    }
}
