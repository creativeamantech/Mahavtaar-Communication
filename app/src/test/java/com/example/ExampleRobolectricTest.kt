package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.engine.TextChunker
import com.example.data.hardware.HardwareDetector
import com.example.data.llm.GgufOnDeviceLLMEngine
import com.example.data.model.LatencyMetrics
import com.example.data.model.ModelRegistry
import com.example.data.model.ModelType
import com.example.data.model.S2SConfig
import com.example.data.storage.StorageManager
import com.example.data.stt.WhisperOnDeviceSTTEngine
import com.example.data.tts.KokoroPiperNeuralTTSEngine
import com.example.data.vad.VoiceActivityDetector
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Speech to Speech", appName)
    }

    @Test
    fun `test latency metrics sub-800ms target validation`() {
        val sub800Metrics = LatencyMetrics(
            sttLatencyMs = 180L,
            ttftMs = 120L,
            ttsLatencyMs = 150L,
            totalLatencyMs = 450L,
            targetMet = true,
            isReal = true
        )
        assertTrue(sub800Metrics.totalLatencyMs < LatencyMetrics.LATENCY_TARGET_MS)
        assertTrue(sub800Metrics.targetMet)
        assertTrue(sub800Metrics.isReal)
    }

    @Test
    fun `test default s2s config parameters`() {
        val config = S2SConfig()
        assertEquals(16000, config.sampleRate)
        assertTrue(config.continuousConversation)
        assertTrue(config.allowBargeIn)
    }

    @Test
    fun `test hardware detector detects ram and architecture`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val detector = HardwareDetector(context)
        val profile = detector.getHardwareProfile()

        assertTrue(profile.totalRamMb > 0)
        assertTrue(profile.cpuArch.isNotBlank())
        assertNotNull(detector.getRecommendedModelId(ModelType.LLM))
        assertNotNull(detector.getRecommendedModelId(ModelType.STT))
        assertNotNull(detector.getRecommendedModelId(ModelType.TTS))
    }

    @Test
    fun `test storage manager creates directories and validates space`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = StorageManager(context)

        val modelsDir = storage.getModelDirectory()
        assertTrue(modelsDir.exists())
        assertTrue(storage.getAvailableStorageBytes() > 0)
    }

    @Test
    fun `test model registry contains recommended models for each type`() {
        val models = ModelRegistry.getAllModels()

        val stt = models.find { it.type == ModelType.STT && it.isRecommended }
        val llm = models.find { it.type == ModelType.LLM && it.isRecommended }
        val tts = models.find { it.type == ModelType.TTS && it.isRecommended }

        assertNotNull("Recommended STT model must exist in registry", stt)
        assertNotNull("Recommended LLM model must exist in registry", llm)
        assertNotNull("Recommended TTS model must exist in registry", tts)
    }

    @Test
    fun `test text chunker clauses at punctuation marks`() {
        val chunker = TextChunker(minChunkLengthChars = 10)

        val chunk1 = chunker.appendToken("Hello there,")
        assertNotNull(chunk1)
        assertEquals("Hello there,", chunk1)

        val chunk2 = chunker.appendToken(" how can I help you today?")
        assertNotNull(chunk2)
        assertEquals("how can I help you today?", chunk2)

        val chunk3 = chunker.appendToken(" Just testing")
        val flushed = chunker.flush()
        assertEquals("Just testing", flushed)
    }

    @Test
    fun `test voice activity detector detects speech onset and silence`() = runBlocking {
        val vad = VoiceActivityDetector(sensitivity = 0.5f, silenceHangoverMs = 300L)
        val events = mutableListOf<VoiceActivityDetector.VadEvent>()

        val job = launch {
            vad.vadEvents.collect { events.add(it) }
        }
        kotlinx.coroutines.yield()

        // Send silence: should not trigger SpeechStarted
        vad.processFrame(0.01f, timestamp = 1000L)
        kotlinx.coroutines.yield()
        assertEquals(0, events.size)

        // Send loud voice frame: triggers SpeechStarted
        vad.processFrame(0.5f, timestamp = 1050L)
        kotlinx.coroutines.yield()
        assertEquals(1, events.size)
        assertTrue(events[0] is VoiceActivityDetector.VadEvent.SpeechStarted)

        // Send silence past hangover duration: triggers SpeechEnded
        vad.processFrame(0.01f, timestamp = 1400L)
        kotlinx.coroutines.yield()
        assertEquals(2, events.size)
        assertTrue(events[1] is VoiceActivityDetector.VadEvent.SpeechEnded)

        job.cancel()
    }

    @Test
    fun `test local engines loaded states enforce model requirement`() {
        val stt = WhisperOnDeviceSTTEngine()
        val llm = GgufOnDeviceLLMEngine()
        val tts = KokoroPiperNeuralTTSEngine()

        assertFalse("STT should not be loaded by default", stt.isLoaded())
        assertFalse("LLM should not be loaded by default", llm.isLoaded())
        assertFalse("TTS should not be loaded by default", tts.isLoaded())
    }
}
