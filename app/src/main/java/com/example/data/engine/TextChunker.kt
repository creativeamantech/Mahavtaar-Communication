package com.example.data.engine

/**
 * Splits streaming LLM tokens into sentence and clause chunks at natural prosody boundaries
 * (e.g. '.', ',', '!', '?', ';', '\n') so speech synthesis can begin immediately without
 * waiting for the full response to finish.
 */
class TextChunker(
    private val minChunkLengthChars: Int = 12
) {
    private val buffer = StringBuilder()
    private val delimiterChars = setOf('.', '!', '?', ',', ';', ':', '\n')

    /**
     * Feeds an incoming token and returns any complete chunk ready for synthesis.
     */
    fun appendToken(token: String): String? {
        buffer.append(token)

        val text = buffer.toString()
        // Search for punctuation boundaries
        for (i in text.indices) {
            val char = text[i]
            if (char in delimiterChars && i >= minChunkLengthChars) {
                val chunk = text.substring(0, i + 1).trim()
                buffer.delete(0, i + 1)
                if (chunk.isNotEmpty()) {
                    return chunk
                }
            }
        }
        return null
    }

    /**
     * Flushes any remaining text in the buffer when streaming finishes.
     */
    fun flush(): String? {
        val remaining = buffer.toString().trim()
        buffer.clear()
        return if (remaining.isNotEmpty()) remaining else null
    }

    fun clear() {
        buffer.clear()
    }
}
