package com.xauusd.news

import com.xauusd.news.ui.impactLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class ImpactLabelTest {
    @Test
    fun usesPlainIndonesianImpactLabels() {
        assertEquals("Dampak tinggi", impactLabel("HIGH"))
        assertEquals("Dampak sedang", impactLabel("MEDIUM"))
        assertEquals("Rutin / dampak rendah", impactLabel("NORMAL"))
        assertEquals("Dampak tinggi", impactLabel("high"))
    }
}

