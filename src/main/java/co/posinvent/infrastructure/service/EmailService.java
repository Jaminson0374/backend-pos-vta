package co.posinvent.infrastructure.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String frontendUrl;

    public EmailService(
            @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
            JavaMailSender mailSender,
            @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl) {
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl;
    }

    /**
     * Sends a set-password invitation to a newly created user.
     */
    public void sendSetPasswordInvitation(String toEmail, String fullName, String token) {
        var link = frontendUrl + "/auth/set-password?token=" + token;

        var msg = new SimpleMailMessage();
        msg.setTo(toEmail);
        msg.setSubject("posinvent — Configurá tu contraseña");
        msg.setText(String.format("""
                Hola %s,

                Se creó tu cuenta en posinvent. Para configurar tu contraseña, ingresá al siguiente enlace:

                %s

                Este enlace expira en 24 horas.

                Saludos,
                Equipo posinvent
                """, fullName, link));

        try {
            mailSender.send(msg);
            log.info("Set-password invitation sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send set-password email to {}: {}", toEmail, e.getMessage());
            log.info("Set-password link for {} (DEV fallback): {}", toEmail, link);
            // Don't throw — email is non-critical, user creation must succeed
        }
    }

    /**
     * Sends a password reset link for "forgot password" flow.
     */
    public void sendPasswordReset(String toEmail, String fullName, String token) {
        var link = frontendUrl + "/auth/set-password?token=" + token;

        var msg = new SimpleMailMessage();
        msg.setTo(toEmail);
        msg.setSubject("posinvent — Restablecer contraseña");
        msg.setText(String.format("""
                Hola %s,

                Recibimos una solicitud para restablecer tu contraseña en posinvent.

                %s

                Si no solicitaste este cambio, ignorá este mensaje. El enlace expira en 1 hora.

                Saludos,
                Equipo posinvent
                """, fullName, link));

        try {
            mailSender.send(msg);
            log.info("Password reset email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
            log.info("Password reset link for {} (DEV fallback): {}", toEmail, link);
            // Don't throw — non-critical
        }
    }
}
