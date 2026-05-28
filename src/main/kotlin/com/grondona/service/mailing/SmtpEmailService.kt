package com.grondona.service.mailing

import com.grondona.model.PROD
import org.springframework.context.annotation.Profile
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

@Component
@Profile(PROD)
class SmtpEmailService(
    private val sender: JavaMailSender,
) : EmailService {

    companion object {
        private const val SUBJECT = "¡PELOTUDO!"
        private const val FROM = "roberto.pettinato@elprodedelmundial.com"
        private fun buildPasswordResetBody(token: String): String =
            """
            ¿Qué pasó máquina? ¿Se te escapó la tortuga?
            Te la haría parir un poquito más, pero la verdad que me da paja.
            Te dejo acá una contraseña temporal. Es de un solo uso, así que cuando entres vas a tener que cambiarla de vuelta:
            
            $token
            
            Te dejo un chiste de regalo. ¿Cómo se dice "detective" en guaraní? Averí-Guaré.
            """.trimIndent()
    }

    override fun sendPasswordResetEmail(to: String, token: String) {
        val message = SimpleMailMessage().apply {
            from = FROM
            subject = SUBJECT
            setTo(to)
            text = buildPasswordResetBody(token)
        }
        sender.send(message)
    }
}