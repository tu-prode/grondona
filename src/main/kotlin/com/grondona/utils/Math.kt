package com.grondona.utils

import kotlin.math.log

fun Float.round(decimals: Int = 2) = String.format("%.${decimals}f", this).toFloat()

fun Float.oddsToQuota() = 0.5f + 2 * log(this.toDouble(), 10.0).toFloat().round()
