package com.grondona.service.mailing

import com.grondona.model.LOCAL
import com.grondona.model.TEST
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile(LOCAL, TEST)
class NoopEmailService : EmailService {

    companion object {
        private val logger = LoggerFactory.getLogger(NoopEmailService::class.java)
    }

    override fun sendPasswordResetEmail(to: String, token: String) {
        logger.info("Password reset email (not sent — logging only): to={}, resetToken={}", to, token)
    }
}
