package com.grondona.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class DateTest {

    @Nested
    inner class SimilarTests {

        @Test
        fun `similar returns true when two dates are separated by less than 10 minutes`() {
            val date1 = ZonedDateTime.of(2020, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC)
            val date2 = ZonedDateTime.of(2020, 10, 1, 10, 5, 0, 0, ZoneOffset.UTC)
            assertTrue { date1.similar(date2) }
        }

        @Test
        fun `similar returns false when two dates are separated by more than 10 minutes`() {
            val date1 = ZonedDateTime.of(2020, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC)
            val date2 = ZonedDateTime.of(2020, 10, 1, 10, 15, 0, 0, ZoneOffset.UTC)
            assertFalse { date1.similar(date2) }
        }

        @Test
        fun `similar returns true when two dates are separated by less than 10 minutes, even with different timezones`() {
            val date1 = ZonedDateTime.of(2020, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC)
            val date2 = ZonedDateTime.of(2020, 10, 1, 11, 5, 0, 0, ZoneOffset.ofHours(-1))
            assertFalse { date1.similar(date2) }
        }
    }

}
