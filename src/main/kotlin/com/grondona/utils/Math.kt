package com.grondona.utils

fun Float.round(decimals: Int = 2) = String.format("%.${decimals}f", this).toFloat()