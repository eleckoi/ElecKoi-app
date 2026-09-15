package com.eleckoi.android.feature.modelconfig.ui.modelpicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatModelSamplingParametersTest {
    @Test
    fun `sampling controls start from neutral one`() {
        assertEquals("1", 1.0.samplingParameterText())
    }

    @Test
    fun `sampling input accepts decimal comma and removes extra separators`() {
        assertEquals("0.75", "0,75".samplingInput())
        assertEquals("0.812", "0..8129".samplingInput())
    }

    @Test
    fun `blank sampling input omits the parameter while zero remains a value`() {
        assertNull("".toSamplingValue())
        assertNull("   ".toSamplingValue())
        assertEquals(0.0, "0".toSamplingValue()!!, 0.0)
        assertEquals(0.7, "0.7".toSamplingValue()!!, 0.0)
    }
}
