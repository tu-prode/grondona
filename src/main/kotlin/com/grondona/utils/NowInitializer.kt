package com.grondona.utils

import com.grondona.model.LOCAL
import com.grondona.now
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
@Profile(LOCAL)
class NowInitializer {

    @PostConstruct
    fun init() {
        now = LocalDateTime.now()
    }

}