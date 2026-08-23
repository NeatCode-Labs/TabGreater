package com.neatcode.tabgreater.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SparklinePathTest {

    @Test
    fun `fewer than two closes draw nothing`() {
        assertEquals(0, SparklinePath.points(floatArrayOf(), 100f, 40f, 2f).size)
        assertEquals(0, SparklinePath.points(floatArrayOf(1f), 100f, 40f, 2f).size)
    }

    @Test
    fun `a zero sized slot draws nothing`() {
        assertEquals(0, SparklinePath.points(floatArrayOf(1f, 2f), 0f, 40f, 2f).size)
        assertEquals(0, SparklinePath.points(floatArrayOf(1f, 2f), 100f, 0f, 2f).size)
    }

    @Test
    fun `x steps span the full width`() {
        val points = SparklinePath.points(floatArrayOf(1f, 2f, 3f, 4f, 5f), 100f, 40f, 0f)

        assertEquals(10, points.size)
        assertEquals(0f, points[0], EPS)
        assertEquals(25f, points[2], EPS)
        assertEquals(50f, points[4], EPS)
        assertEquals(100f, points[8], EPS)
    }

    @Test
    fun `min and max map to the box edges, inset by half the stroke`() {
        val points = SparklinePath.points(floatArrayOf(10f, 30f, 20f), 60f, 40f, 4f)

        // usable height = 40 - 4 = 36, offset = 2
        assertEquals(38f, points[1], EPS) // min -> bottom
        assertEquals(2f, points[3], EPS) // max -> top
        assertEquals(20f, points[5], EPS) // midpoint
    }

    @Test
    fun `a flat window is drawn through the middle, not along the bottom edge`() {
        val points = SparklinePath.points(floatArrayOf(7f, 7f, 7f), 60f, 40f, 0f)

        assertEquals(20f, points[1], EPS)
        assertEquals(20f, points[3], EPS)
        assertEquals(20f, points[5], EPS)
    }

    @Test
    fun `every point stays inside the box`() {
        val values = FloatArray(96) { (it * 37 % 100).toFloat() }
        val points = SparklinePath.points(values, 200f, 80f, 3f)

        assertEquals(192, points.size)
        for (i in points.indices step 2) {
            assertTrue("x=${points[i]}", points[i] in 0f..200f)
            assertTrue("y=${points[i + 1]}", points[i + 1] in 0f..80f)
        }
    }

    private companion object {
        const val EPS = 0.001f
    }
}
