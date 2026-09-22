package com.kg.museumly.testutil

/**
 * Loads trimmed, real-shaped JSON payloads from src/test/resources for
 * MockWebServer tests, instead of inlining large response bodies in test code.
 */
object Fixtures {

    fun read(path: String): String {
        val stream = Fixtures::class.java.classLoader?.getResourceAsStream(path)
        requireNotNull(stream) { "Fixture not found on test classpath: $path" }
        val text: String = stream.bufferedReader().use { reader -> reader.readText() }
        return text
    }
}
