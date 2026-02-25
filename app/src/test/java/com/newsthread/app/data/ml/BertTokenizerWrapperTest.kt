package com.newsthread.app.data.ml

import android.content.Context
import android.content.res.AssetManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

@RunWith(MockitoJUnitRunner::class)
class BertTokenizerWrapperTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockAssetManager: AssetManager

    private lateinit var tokenizer: BertTokenizerWrapper

    // Minimal vocabulary for testing
    // Must include special tokens: [PAD], [UNK], [CLS], [SEP]
    // Indices:
    // [PAD]: 0
    // [UNK]: 1
    // [CLS]: 2
    // [SEP]: 3
    // [MASK]: 4
    // hello: 5
    // world: 6
    // play: 7
    // ##ing: 8
    // test: 9
    // example: 10
    // token: 11
    // ##ization: 12
    // ,: 13
    // .: 14
    // !: 15
    // ?: 16
    private val vocabulary = """
        [PAD]
        [UNK]
        [CLS]
        [SEP]
        [MASK]
        hello
        world
        play
        ##ing
        test
        example
        token
        ##ization
        ,
        .
        !
        ?
    """.trimIndent()

    @Before
    fun setup() {
        // Mock AssetManager behavior
        `when`(mockContext.assets).thenReturn(mockAssetManager)

        // Mock open("vocab.txt") to return our vocabulary stream
        `when`(mockAssetManager.open(anyString())).thenAnswer {
            ByteArrayInputStream(vocabulary.toByteArray(StandardCharsets.UTF_8))
        }

        tokenizer = BertTokenizerWrapper(mockContext)
    }

    @Test
    fun `initialize_loadsVocabularySuccessfully`() {
        val result = tokenizer.initialize()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `tokenize_simpleText_returnsCorrectIds`() {
        // "hello world" -> [CLS], hello, world, [SEP], [PAD]...

        val result = tokenizer.tokenize("hello world")
        assertTrue(result.isSuccess)

        val (inputIds, attentionMask) = result.getOrNull()!!

        // Check start and end tokens
        assertEquals(2, inputIds[0]) // [CLS]
        assertEquals(5, inputIds[1]) // hello
        assertEquals(6, inputIds[2]) // world
        assertEquals(3, inputIds[3]) // [SEP]
        assertEquals(0, inputIds[4]) // [PAD]

        // Check attention mask
        assertEquals(1, attentionMask[0])
        assertEquals(1, attentionMask[1])
        assertEquals(1, attentionMask[2])
        assertEquals(1, attentionMask[3])
        assertEquals(0, attentionMask[4])

        // Check length
        assertEquals(128, inputIds.size)
        assertEquals(128, attentionMask.size)
    }

    @Test
    fun `tokenize_wordPiece_splitsTokens`() {
        // "playing" -> "play", "##ing"
        // play: 7, ##ing: 8

        val result = tokenizer.tokenize("playing")
        assertTrue(result.isSuccess)

        val (inputIds, _) = result.getOrNull()!!

        assertEquals(2, inputIds[0]) // [CLS]
        assertEquals(7, inputIds[1]) // play
        assertEquals(8, inputIds[2]) // ##ing
        assertEquals(3, inputIds[3]) // [SEP]
    }

    @Test
    fun `tokenize_unknownWords_usesUnkToken`() {
        // "unknownword" -> [UNK]
        // [UNK]: 1

        val result = tokenizer.tokenize("unknownword")
        assertTrue(result.isSuccess)

        val (inputIds, _) = result.getOrNull()!!

        assertEquals(2, inputIds[0]) // [CLS]
        assertEquals(1, inputIds[1]) // [UNK]
        assertEquals(3, inputIds[2]) // [SEP]
    }

    @Test
    fun `tokenize_handlesPunctuation`() {
        // "hello," -> "hello", ","
        // hello: 5, ,: 13

        val result = tokenizer.tokenize("hello,")
        assertTrue(result.isSuccess)

        val (inputIds, _) = result.getOrNull()!!

        assertEquals(2, inputIds[0]) // [CLS]
        assertEquals(5, inputIds[1]) // hello
        assertEquals(13, inputIds[2]) // ,
        assertEquals(3, inputIds[3]) // [SEP]
    }

    @Test
    fun `tokenize_truncatesLongText`() {
        // Construct a very long string
        val longText = "hello ".repeat(200)

        val result = tokenizer.tokenize(longText)
        assertTrue(result.isSuccess)

        val (inputIds, attentionMask) = result.getOrNull()!!

        assertEquals(128, inputIds.size)

        // Should end with [SEP] at index 127
        assertEquals(3, inputIds[127]) // [SEP]
        assertEquals(1, attentionMask[127])
    }

    @Test
    fun `tokenize_handlesEmptyString`() {
        val result = tokenizer.tokenize("")
        assertTrue(result.isSuccess)

        val (inputIds, _) = result.getOrNull()!!

        assertEquals(2, inputIds[0]) // [CLS]
        assertEquals(3, inputIds[1]) // [SEP]
        assertEquals(0, inputIds[2]) // [PAD]
    }
}
