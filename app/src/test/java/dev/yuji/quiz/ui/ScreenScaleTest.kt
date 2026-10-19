package dev.yuji.quiz.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenScaleTest {
    @Test fun referenceHeightIsUnscaled() {
        val s = ScreenScale.forHeight(ScreenScale.REFERENCE_HEIGHT_DP)
        assertEquals(1f, s.layout, 0.0001f)
        assertEquals(1f, s.text, 0.0001f)
    }

    @Test fun scaleIsProportionalInsideTheCaps() {
        assertEquals(0.9f, ScreenScale.forHeight(792f).layout, 0.0001f)
        assertEquals(1.1f, ScreenScale.forHeight(968f).layout, 0.0001f)
    }

    @Test fun tinyAndHugeScreensHitTheCaps() {
        val tiny = ScreenScale.forHeight(300f)
        assertEquals(ScreenScale.MIN_LAYOUT, tiny.layout, 0f)
        assertEquals(ScreenScale.MIN_TEXT, tiny.text, 0f)
        val huge = ScreenScale.forHeight(3000f)
        assertEquals(ScreenScale.MAX_LAYOUT, huge.layout, 0f)
        assertEquals(ScreenScale.MAX_TEXT, huge.text, 0f)
    }

    @Test fun textNeverShrinksBelowItsNormalSize() {
        listOf(300f, 500f, 592f, 740f, 880f, 1200f).forEach { assertTrue("$it", ScreenScale.forHeight(it).text >= 1f) }
    }

    @Test fun textScalesLessThanLayoutOnBigScreens() {
        val s = ScreenScale.forHeight(1100f)
        assertTrue(s.text <= s.layout)
    }
}
