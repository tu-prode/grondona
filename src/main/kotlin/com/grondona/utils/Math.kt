package com.grondona.utils

import kotlin.math.log

fun Float.round(decimals: Int = 2) = String.format("%.${decimals}f", this).toFloat()

fun Float.oddsToQuota(): Float {
    if (this <= 0f) return 0f
    return (0.1f + 2 * log(this.toDouble(), 6.0).toFloat()).round()
}
