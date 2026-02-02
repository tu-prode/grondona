package com.grondona

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GrondonaApplication

fun main(args: Array<String>) {
    runApplication<GrondonaApplication>(*args)
}
