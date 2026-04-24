package com.grondona

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.LocalDateTime

@SpringBootApplication
@EnableScheduling
class GrondonaApplication

var now: LocalDateTime = LocalDateTime.now()

fun main(args: Array<String>) {
    runApplication<GrondonaApplication>(*args)
}
