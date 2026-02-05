package com.newsthread.app.data.ml

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WordPiece tokenizer for BERT-based models.
 *
 * Phase 3: Tokenizes text for all-MiniLM-L6-v2 model
 * Vocabulary loaded from vocab.txt (~230KB)
 *
 * Implements basic WordPiece tokenization:
 * - Lowercasing and punctuation handling
 * - WordPiece token splitting with "##" prefix
 * - [CLS] and [SEP] special tokens
 * - Padding to max sequence length
 */
@Singleton
class BertTokenizerWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var vocabMap: Map<String, Int>? = null
    private val lock = Any()
    private var isInitialized = false

    companion object {
        private const val TAG = "BertTokenizer"
        private const val VOCAB_FILE = "vocab.txt"
        private const val PAD_TOKEN = "[PAD]"
        private const val CLS_TOKEN = "[CLS]"
        private const val SEP_TOKEN = "[SEP]"
        private const val UNK_TOKEN = "[UNK]"
        const val MAX_LENGTH = 128
    }

    /**
     * Load vocabulary from assets.
     * Maps tokens to their integer IDs.
     */
    fun initialize(): Result<Unit> {
        synchronized(lock) {
            if (isInitialized) {
                return Result.success(Unit)
            }

            return try {
                val vocab = mutableMapOf<String, Int>()
                context.assets.open(VOCAB_FILE).use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        var index = 0
                        reader.forEachLine { line ->
                            vocab[line.trim()] = index++
                        }
                    }
                }

                vocabMap = vocab
                isInitialized = true
                Log.d(TAG, "Vocabulary loaded: ${vocab.size} tokens")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load vocabulary", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Tokenize text into token IDs and attention mask.
     *
     * @param text Input text to tokenize
     * @return Pair of (inputIds, attentionMask) padded to MAX_LENGTH
     */
    fun tokenize(text: String): Result<Pair<IntArray, IntArray>> {
        synchronized(lock) {
            if (!isInitialized) {
                val initResult = initialize()
                if (initResult.isFailure) {
                    return Result.failure(initResult.exceptionOrNull()!!)
                }
            }

            val vocab = vocabMap ?: return Result.failure(IllegalStateException("Vocab not loaded"))

            return try {
                // Basic text preprocessing
                val cleanedText = text.lowercase().take(1000)  // Limit length per 03-CONTEXT.md

                // Tokenize into words
                val words = basicTokenize(cleanedText)

                // WordPiece tokenization
                val tokens = mutableListOf<String>()
                tokens.add(CLS_TOKEN)

                for (word in words) {
                    val wordTokens = wordpieceTokenize(word, vocab)
                    tokens.addAll(wordTokens)
                    if (tokens.size >= MAX_LENGTH - 1) break
                }

                tokens.add(SEP_TOKEN)

                // Convert to IDs
                val inputIds = IntArray(MAX_LENGTH)
                val attentionMask = IntArray(MAX_LENGTH)

                val padId = vocab[PAD_TOKEN] ?: 0

                for (i in 0 until MAX_LENGTH) {
                    if (i < tokens.size) {
                        inputIds[i] = vocab[tokens[i]] ?: vocab[UNK_TOKEN] ?: 0
                        attentionMask[i] = 1
                    } else {
                        inputIds[i] = padId
                        attentionMask[i] = 0
                    }
                }

                Result.success(Pair(inputIds, attentionMask))
            } catch (e: Exception) {
                Log.e(TAG, "Tokenization failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Basic whitespace tokenization with punctuation handling.
     */
    private fun basicTokenize(text: String): List<String> {
        return text
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .flatMap { word ->
                // Split punctuation
                word.replace(Regex("([.,!?;:])"), " $1 ").split(Regex("\\s+"))
            }
            .filter { it.isNotBlank() }
    }

    /**
     * WordPiece tokenization for a single word.
     * Uses greedy longest-match-first algorithm.
     */
    private fun wordpieceTokenize(word: String, vocab: Map<String, Int>): List<String> {
        val tokens = mutableListOf<String>()
        var remaining = word

        while (remaining.isNotEmpty()) {
            var found = false
            for (length in remaining.length downTo 1) {
                val subToken = if (tokens.isEmpty()) {
                    remaining.substring(0, length)
                } else {
                    "##${remaining.substring(0, length)}"
                }

                if (vocab.containsKey(subToken)) {
                    tokens.add(subToken)
                    remaining = remaining.substring(length)
                    found = true
                    break
                }
            }

            if (!found) {
                tokens.add(UNK_TOKEN)
                break
            }
        }

        return tokens
    }
}
