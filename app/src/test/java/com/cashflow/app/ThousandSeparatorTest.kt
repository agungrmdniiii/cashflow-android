package com.cashflow.app

import androidx.compose.ui.text.AnnotatedString
import com.cashflow.app.ui.components.ThousandSeparatorVisualTransformation
import org.junit.Assert.assertEquals
import org.junit.Test

class ThousandSeparatorTest {

    private val transformation = ThousandSeparatorVisualTransformation()

    @Test
    fun testEmptyString() {
        val transformed = transformation.filter(AnnotatedString(""))
        assertEquals("", transformed.text.text)
    }

    @Test
    fun testLessThanThreeDigits() {
        val res1 = transformation.filter(AnnotatedString("5"))
        assertEquals("5", res1.text.text)

        val res2 = transformation.filter(AnnotatedString("50"))
        assertEquals("50", res2.text.text)

        val res3 = transformation.filter(AnnotatedString("500"))
        assertEquals("500", res3.text.text)
    }

    @Test
    fun testThousands() {
        val res1 = transformation.filter(AnnotatedString("1000"))
        assertEquals("1.000", res1.text.text)

        val res2 = transformation.filter(AnnotatedString("10000"))
        assertEquals("10.000", res2.text.text)

        val res3 = transformation.filter(AnnotatedString("100000"))
        assertEquals("100.000", res3.text.text)
    }

    @Test
    fun testMillionsAndBillions() {
        val res1 = transformation.filter(AnnotatedString("1500000"))
        assertEquals("1.500.000", res1.text.text)

        val res2 = transformation.filter(AnnotatedString("25000000"))
        assertEquals("25.000.000", res2.text.text)

        val res3 = transformation.filter(AnnotatedString("1000000000"))
        assertEquals("1.000.000.000", res3.text.text)
    }

    @Test
    fun testOffsetMapping() {
        // "1500000" (len 7) -> "1.500.000" (len 9)
        val transformed = transformation.filter(AnnotatedString("1500000"))
        val mapping = transformed.offsetMapping

        // Cursor at start
        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(0, mapping.transformedToOriginal(0))

        // Cursor at end
        assertEquals(9, mapping.originalToTransformed(7))
        assertEquals(7, mapping.transformedToOriginal(9))

        // After first digit '1', dot is at index 1, so cursor is at index 2 (before '5')
        assertEquals(2, mapping.originalToTransformed(1))
        assertEquals(1, mapping.transformedToOriginal(2))
    }
}
