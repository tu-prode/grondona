package com.grondona

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class GrondonaApplication

fun main(args: Array<String>) {
    runApplication<GrondonaApplication>(*args)
}
