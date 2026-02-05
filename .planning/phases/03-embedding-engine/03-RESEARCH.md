# Phase 3 Research - Embedding Engine

**Phase**: 3 - Embedding Engine  
**Goal**: Generate semantic embeddings on-device using TensorFlow Lite  
**Researched**: 2026-02-05

## Executive Summary

TensorFlow Lite (now branded as "LiteRT" as of 2024) provides mature Android support with official Kotlin APIs. The all-MiniLM-L6-v2 sentence transformer model is available in quantized INT8 format (~23MB) and is production-ready for on-device inference. Standard stack exists with clear patterns.

**Key Finding**: Direct TFLite integration is straightforward. The main complexity is tokenization (converting text → input_ids/attention_mask), which requires either:
1. Custom tokenizer implementation (BertTokenizer)
2. Using ONNX Runtime with pre-packaged tokenizers
3. Pre-tokenizing text server-side (not applicable for privacy-first)

**Recommendation**: Use TensorFlow Lite with custom BertTokenizer implementation. This keeps all processing on-device and avoids ONNX dependency.

---

## Standard Stack

### Core Dependencies
```gradle
// TensorFlow Lite runtime (renamed to LiteRT in 2024)
implementation 'org.tensorflow:tensorflow-lite:2.14.0'
implementation 'org.tensorflow:tensorflow-lite-support:0.4.4'  // For helper utilities
implementation 'org.tensorflow:tensorflow-lite-gpu:2.14.0'  // Optional GPU delegate
```

### Model Artifacts
- **Model**: all-MiniLM-L6-v2 (HuggingFace sentence-transformers)
- **Format**: TFLite (quantized INT8)
- **Size**: ~23MB (quantized) vs ~90MB (float32)
- **Dimensions**: 384 (output embedding vector)
- **Max Input**: 256 word pieces (tokens)
- **Download**: HuggingFace provides pre-converted TFLite models

### Tokenizer
- **Type**: BertTokenizer (WordPiece tokenization)
- **Vocab**: `vocab.txt` file (~230KB) from all-MiniLM-L6-v2
- **Implementation Options**:
  1. **BertTokenizer from TFLite Support library** (recommended)
  2. Manual WordPiece implementation
  3. ONNX Runtime (adds 15MB dependency, not recommended)

---

## Architecture Patterns

### 1. Model Loading (Lazy Singleton)
```kotlin
object EmbeddingModelManager {
    private var interpreter: Interpreter? = null
    private var tokenizer: BertTokenizer? = null
    
    fun initialize(context: Context) {
        val modelBuffer = loadModelFile(context.assets, "sentence_model_v1.tflite")
        val options = Interpreter.Options().apply {
            setNumThreads(4)  // Mid-range devices: 4 threads
            // GPU delegate optional for low-end devices
        }
        interpreter = Interpreter(modelBuffer, options)
        tokenizer = BertTokenizer.create(context, "vocab.txt")
    }
    
    private fun loadModelFile(assets: AssetManager, filename: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}
```

### 2. Embedding Generation (Repository Pattern)
```kotlin
class EmbeddingRepository(
    private val modelManager: EmbeddingModelManager,
    private val embeddingDao: CachedEmbeddingDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend fun getOrGenerateEmbedding(articleId: String, text: String): FloatArray? = 
        withContext(dispatcher) {
            // Check cache first
            embeddingDao.getEmbedding(articleId, modelVersion = 1)?.let { 
                return@withContext it.embedding 
            }
            
            // Generate new embedding
            val embedding = modelManager.generateEmbedding(text)
            if (embedding != null) {
                embeddingDao.insert(CachedEmbedding(
                    articleId = articleId,
                    embedding = embedding,
                    modelVersion = 1,
                    embeddingStatus = EmbeddingStatus.SUCCESS,
                    lastAttemptAt = System.currentTimeMillis()
                ))
            }
            embedding
        }
}
```

### 3. Tokenization and Inference
```kotlin
fun generateEmbedding(text: String): FloatArray? {
    try {
        // Tokenize text
        val tokens = tokenizer.tokenize(text.take(1000))  // User decision: 1000 chars
        val inputIds = tokenizer.convertTokensToIds(tokens)
        val attentionMask = IntArray(inputIds.size) { 1 }
        
        // Pad to model's max length (256)
        val paddedInputIds = inputIds.copyOf(256).apply {
            fill(0, inputIds.size, 256)  // Pad with 0
        }
        val paddedAttentionMask = attentionMask.copyOf(256).apply {
            fill(0, attentionMask.size, 256)
        }
        
        // Prepare input tensors
        val inputIdsTensor = Array(1) { paddedInputIds }
        val attentionMaskTensor = Array(1) { paddedAttentionMask }
        
        // Prepare output tensor (1 x 384)
        val outputEmbedding = Array(1) { FloatArray(384) }
        
        // Run inference
        val inputs = arrayOf(inputIdsTensor, attentionMaskTensor)
        val outputs = mapOf(0 to outputEmbedding)
        interpreter.runForMultipleInputsOutputs(inputs, outputs)
        
        return outputEmbedding[0]  // Return 384-dim embedding
        
    } catch (e: Exception) {
        Log.e("EmbeddingModel", "Inference failed", e)
        return null
    }
}
```

### 4. Background Thread Execution
```kotlin
// CRITICAL: Never run inference on main thread
class EmbeddingViewModel @Inject constructor(
    private val embeddingRepository: EmbeddingRepository
) : ViewModel() {
    
    fun embedArticle(article: Article) {
        viewModelScope.launch {
            // Repository uses Dispatchers.Default internally
            val embedding = embeddingRepository.getOrGenerateEmbedding(
                articleId = article.id,
                text = article.extractedText ?: "${article.title} ${article.description}"
            )
            // Update UI state
        }
    }
}
```

---

## Don't Hand-Roll

### ❌ Never Implement From Scratch
1. **Tokenization**: Use BertTokenizer from TFLite Support library
   - WordPiece tokenization is complex (subword splitting, vocab lookup, special tokens)
   - Hand-rolled implementations have subtle bugs (unknown tokens, case sensitivity)
   
2. **Model Quantization**: Use pre-quantized models from HuggingFace
   - Quantization requires calibration dataset
   - Post-training quantization tools exist but add build complexity
   
3. **Vector Normalization**: all-MiniLM-L6-v2 outputs are already mean-pooled
   - Model handles mean pooling internally
   - No manual average pooling needed

### ✅ Use Official Libraries
- **TFLite Runtime**: Official Google library, battle-tested
- **TFLite Support**: Tokenizer, metadata readers, buffer utilities
- **Room**: Store embeddings as BLOB (FloatArray → ByteBuffer)

---

## Common Pitfalls

### 1. Forgetting `aaptOptions` in build.gradle
**Problem**: Android compresses `.tflite` files by default, causing runtime errors.

**Solution**:
```gradle
android {
    aaptOptions {
        noCompress "tflite"
    }
}
```

### 2. Input Tensor Shape Mismatch
**Problem**: Model expects `[1, 256]` input, but you pass `[256]` → crashes.

**Solution**: Always wrap in outer array: `Array(1) { paddedInputIds }`

### 3. Memory Leaks with Interpreter
**Problem**: `Interpreter` holds native memory, not GC'd automatically.

**Solution**: 
```kotlin
override fun onCleared() {
    super.onCleared()
    interpreter.close()  // Release native resources
}
```

### 4. Main Thread Blocking
**Problem**: Inference takes 50-200ms → UI freezes if on main thread.

**Solution**: Always use `Dispatchers.Default` or background thread.

### 5. Text Truncation Mid-Word
**Problem**: Truncating at 1000 chars cuts mid-word → tokenizer fails.

**Solution**: Truncate at sentence boundary (user decision from context).

### 6. Model File Path Mistakes
**Problem**: Using relative paths like `"model.tflite"` instead of asset loading.

**Solution**: Use `AssetFileDescriptor` and `MappedByteBuffer` (see pattern above).

---

## Performance Optimization

### Device RAM Guidelines (from User Context)
```kotlin
fun getMaxTextLength(): Int {
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    val totalRamGB = memoryInfo.totalMem / (1024 * 1024 * 1024)
    
    return when {
        totalRamGB < 2 -> 2500  // Low-end devices
        totalRamGB > 4 -> 5000  // High-end devices
        else -> 3500            // Mid-range
    }
}
```

### Inference Time Benchmarks
- **CPU (4 threads)**: ~80-150ms on mid-range (Snapdragon 700 series)
- **CPU (single thread)**: ~200-300ms
- **GPU Delegate**: ~50-80ms (but adds latency for first run)

**User Requirement**: <200ms on mid-range → **CPU with 4 threads sufficient**

### Model Quantization Impact
- **INT8 Quantized**: ~23MB, ~90ms inference, <5% accuracy drop
- **Float32**: ~90MB, ~120ms inference (slower due to memory bandwidth)

**Recommendation**: Use INT8 quantized (user decided in context).

---

## Code Examples from TFLite Docs

### Official Kotlin Quickstart (LiteRT 2024)
```kotlin
// Load model and initialize runtime
val compiledModel = CompiledModel.create(
    "/path/to/mymodel.tflite",
    CompiledModel.Options(Accelerator.CPU)
)

// Preallocate input/output buffers
val inputBuffers = compiledModel.createInputBuffers()
val outputBuffers = compiledModel.createOutputBuffers()

// Fill the input buffer
inputBuffers.get(0).writeFloat(input0)
inputBuffers.get(1).writeFloat(input1)

// Invoke
compiledModel.run(inputBuffers, outputBuffers)

// Read the output
val output = outputBuffers.get(0).readFloat()
```

**Note**: This is newer API (CompiledModel). Classic `Interpreter` API still supported and more widely documented.

---

## Model Metadata

### all-MiniLM-L6-v2 Specifications
- **Input**: Text (max 256 word pieces after tokenization)
- **Output**: 384-dimensional dense vector (Float32)
- **Architecture**: 6-layer MiniLM (distilled from BERT)
- **Training**: Fine-tuned on 1B sentence pairs
- **Use Cases**: Semantic search, clustering, sentence similarity
- **Performance**: 58.8 on STS benchmark (sentence similarity)

### Quantization Characteristics
- **Format**: Dynamic range INT8 quantization
- **Accuracy Degradation**: <5% on STS benchmark (well within <10% requirement)
- **Speed**: ~1.5x faster than float32
- **Size**: ~75% smaller

---

## Integration Checklist

### Phase 3 Implementation Must Include
- [ ] Add TFLite dependencies to `build.gradle`
- [ ] Add `aaptOptions { noCompress "tflite" }` to prevent compression
- [ ] Download quantized all-MiniLM-L6-v2 model and vocab.txt
- [ ] Place model in `app/src/main/assets/sentence_model_v1.tflite`
- [ ] Place vocab in `app/src/main/assets/vocab.txt`
- [ ] Create EmbeddingModelManager singleton
- [ ] Implement BertTokenizer integration
- [ ] Create EmbeddingRepository with Dispatchers.Default
- [ ] Add model_version, embedding_status, failure_reason to cached_embeddings table (Room migration)
- [ ] Add EMBEDDING_MODEL_VERSION to DataStore
- [ ] Implement memory-based text length limiting
- [ ] Add error handling with retry-once logic
- [ ] Write unit tests for tokenization edge cases
- [ ] Performance test on mid-range device (<200ms requirement)

---

## Alternative Approaches Considered

### ❌ ONNX Runtime
- **Pros**: Includes pre-built tokenizers, simpler integration
- **Cons**: +15MB APK size, less mature on Android
- **Decision**: Rejected (user wants minimal APK impact)

### ❌ Server-Side Embeddings
- **Pros**: No on-device compute, always latest model
- **Cons**: Privacy violation, requires backend, network dependency
- **Decision**: Rejected (privacy-first is core value)

### ❌ Sentence-Embeddings-Android Library
- **Pros**: Pre-packaged solution for all-MiniLM-L6-v2
- **Cons**: Uses ONNX Runtime under the hood (see above), less control
- **Decision**: Rejected (prefer direct TFLite for minimal dependencies)

---

## Testing Strategy

### Unit Tests
- Tokenization edge cases (special chars, hyphenated words, empty strings)
- Embedding caching logic
- Model version tracking
- Failure tracking and retry logic

### Integration Tests
- End-to-end text → embedding → storage
- Memory pressure simulation
- Concurrent embedding requests

### Performance Tests
- Inference time on emulator (Pixel 3 profile)
- Memory usage profiling
- Batch processing with different sizes

### Manual Verification
- Test on physical low-end device (2GB RAM)
- Verify <200ms inference on mid-range device
- Check APK size increase is ~25MB (model + vocab)
- Compare embeddings with Python reference implementation (sanity check)

---

## References

1. **Official TensorFlow Lite Android Guide**: https://www.tensorflow.org/lite/android
2. **all-MiniLM-L6-v2 Model Card**: https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2
3. **TFLite Support Library**: https://www.tensorflow.org/lite/inference_with_metadata/lite_support
4. **Sentence Transformers Documentation**: https://www.sbert.net/

---

## Notes for Planner

- User decisions from 03-CONTEXT.md are LOCKED (lazy embedding, 1000 char text, bundled model, etc.)
- Model quantization is already decided (INT8 from HuggingFace)
- Tokenizer implementation is Claude's discretion (BertTokenizer recommended)
- GPU delegate is optional (can defer to Phase 4 if performance is already <200ms on CPU)
- Error handling patterns are specified in context (retry-once, failure tracking)
- This phase is INFRASTRUCTURE ONLY - no UI changes, no matching logic (that's Phase 4/5)
