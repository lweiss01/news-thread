package com.newsthread.app.domain.similarity

import org.junit.Test
import kotlin.system.measureTimeMillis

class EntityExtractorBenchmarkTest {

    @Test
    fun benchmarkExtractEntities() {
        val extractor = EntityExtractor()
        val text = "Trump sends second aircraft carrier to Gulf amid Iran threats - Axios and some other stuff happening in the world today with many words and symbols like & and ."

        // Warmup
        repeat(1000) {
            extractor.extractEntities(text)
        }

        val iterations = 50000
        val time = measureTimeMillis {
            repeat(iterations) {
                extractor.extractEntities(text)
            }
        }

        println("Benchmark: $iterations iterations took ${time}ms")
        println("Average time per call: ${time.toDouble() / iterations} ms")
    }
}
