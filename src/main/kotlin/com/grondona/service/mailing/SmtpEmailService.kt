package com.grondona.service.mailing

import com.grondona.model.PROD
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

private const val joke = "Resulta que en un hospital hay 3 parejas a punto de tener familia: unos argentinos, unos uruguayos y unos senegaleses. " +
    "Mientras los hombres esperan afuera, sale el médico y les informa que hubo una confusión y que se les mezclaron los 3 bebés, así que decidieron " +
    "que los padres pasen uno a uno a identificar a sus respectivos hijos. Pasa primero el uruguayo, y a los 5 minutos sale con un niño negro como el carbón. " +
    "El senegalés lo frena y le dice \"¿por qué agarraste ese bebé? claramente es mío\", y el uruguayo le responde \"de los otros dos uno es argentino y no voy a correr el riesgo de llevármelo."

@Component
@Profile(PROD)
class SmtpEmailService(
    private val sender: JavaMailSender,
    @Value("\${app.mail.from}") private val fromAddress: String,
) : EmailService {

    companion object {
        private const val SUBJECT = "¡PELOTUDO!"
        private fun body(token: String): String =
            """
            ¿Qué pasó máquina? ¿Se te escapó la tortuga?
            Olvidarse una contraseña hoy en día es realmente de boludo, se guardan solas campeón.
            Te dejo acá una contraseña temporal. Es de un solo uso, así que cuando entres vas a tener que cambiarla de vuelta:
            
            $token
            
            También te dejo un chiste de regalo. $joke".
            """.trimIndent()
    }

    override fun sendPasswordResetEmail(to: String, token: String) {
        val message = SimpleMailMessage().apply {
            from = fromAddress
            subject = SUBJECT
            setTo(to)
            text = body(token)
        }
        sender.send(message)
    }
}