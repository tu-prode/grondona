package com.grondona.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MathTest {

    @Nested
    inner class OddsToQuotasTests {

        @Test
        fun `oddsToQuotas returns the proper values`() {
            val odds = 2.88F
            val quota = odds.oddsToQuota()
            assertEquals(1.3F, quota)
        }
    }

}
