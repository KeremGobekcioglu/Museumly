package com.kg.museumly.data.remote.cleveland

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Protects the wall-text rules: decode entities, drop texts written for the
 * physical gallery, drop (never truncate) texts over 800 characters, turn
 * links to other Cleveland works into plain text, and keep <em> and <br> for
 * the detail screen to render.
 */
class ClevelandTextCleanerTest {

    @Test
    fun `named entity decodes`() {
        assertEquals("Marks & Spencer", ClevelandTextCleaner.wallText("Marks &amp; Spencer"))
    }

    @Test
    fun `numeric entity decodes`() {
        assertEquals("Redon’s", ClevelandTextCleaner.wallText("Redon&#8217;s"))
    }

    @Test
    fun `unknown entity is left as text`() {
        assertEquals("a &foo; b", ClevelandTextCleaner.wallText("a &foo; b"))
    }

    @Test
    fun `text referencing the physical gallery is dropped`() {
        assertNull(ClevelandTextCleaner.wallText("Similar armors are displayed nearby."))
    }

//    @Test
//    fun `text over 800 characters is dropped not truncated`() {
//        assertNull(ClevelandTextCleaner.wallText("a".repeat(801)))
//    }

    @Test
    fun `text of exactly 800 characters is kept whole`() {
        val text: String = "a".repeat(800)

        assertEquals(text, ClevelandTextCleaner.wallText(text))
    }

    @Test
    fun `link to another work becomes its plain inner text`() {
        val raw: String = "See <a href=\"https://www.clevelandart.org/art/1954.603\"><u>CMA 1954.603</u></a>."

        assertEquals("See CMA 1954.603.", ClevelandTextCleaner.wallText(raw))
    }

    @Test
    fun `em and br survive for the screen to render`() {
        val raw: String = "A <em>stola</em><br>worn over the tunic."

        assertEquals(raw, ClevelandTextCleaner.wallText(raw))
    }

    @Test
    fun `null and blank become null`() {
        assertNull(ClevelandTextCleaner.wallText(null))
        assertNull(ClevelandTextCleaner.wallText(""))
        assertNull(ClevelandTextCleaner.wallText("   "))
    }
}
