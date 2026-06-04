package com.grondona.utils

import java.time.Duration
import java.time.ZonedDateTime

fun ZonedDateTime.similar(other: ZonedDateTime): Boolean =
    Duration.between(this.toInstant(), other.toInstant()).abs() <= Duration.ofMinutes(10)
