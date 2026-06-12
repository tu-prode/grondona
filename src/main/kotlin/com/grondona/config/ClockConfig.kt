package com.grondona.config

import com.grondona.utils.Clock
import com.grondona.model.LOCAL
import com.grondona.model.PROD
import com.grondona.model.TEST
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
class ClockConfig {

    @Configuration
    @Profile(PROD)
    class Production {
        @PostConstruct
        fun init() = Clock.configureForProduction()
    }

    @Configuration
    @Profile(LOCAL, TEST)
    class Simulation {
        @PostConstruct
        fun init() = Clock.configureForSimulation()
    }
}
