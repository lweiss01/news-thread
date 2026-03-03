package com.newsthread.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlUtilsTest {

    @Test
    fun `decodeHtmlEntities should replace nbsp with spaces`() {
        val input = "Headline&nbsp;&nbsp;Axios"
        val expected = "Headline  Axios"
        val actual = HtmlUtils.decodeHtmlEntities(input)
        assertEquals(expected, actual)
    }

    @Test
    fun `decodeHtmlEntities should handle malformed nbsp without semicolon`() {
        val input = "Headline&nbsp;&nbspAxios"
        val expected = "Headline  Axios"
        val actual = HtmlUtils.decodeHtmlEntities(input)
        assertEquals(expected, actual)
    }

    @Test
    fun `decodeHtmlEntities should handle other common entities`() {
        val input = "Tom &amp; Jerry &quot;Show&quot;"
        val expected = "Tom & Jerry \"Show\""
        val actual = HtmlUtils.decodeHtmlEntities(input)
        assertEquals(expected, actual)
    }

    @Test
    fun `decodeHtmlEntities should return original string if no entities`() {
        val input = "Plain text"
        val expected = "Plain text"
        val actual = HtmlUtils.decodeHtmlEntities(input)
        assertEquals(expected, actual)
    }
}
