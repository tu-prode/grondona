package com.grondona.service.mailing

interface EmailService {
    fun sendPasswordResetEmail(to: String, token: String)
}
