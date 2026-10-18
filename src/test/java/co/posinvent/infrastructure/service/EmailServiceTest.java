package co.posinvent.infrastructure.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final String RAW_TOKEN = "raw-secret-token";

    @Mock private JavaMailSender mailSender;

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(EmailService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void failureLogsNeverLeakTheTokenOrTheResetLink() {
        doThrow(new MailSendException("smtp down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        var service = new EmailService(mailSender, "http://localhost:4200");
        var userId = UUID.randomUUID();

        service.sendPasswordReset(userId, "user@example.com", "User", RAW_TOKEN);

        List<String> messages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        assertThat(messages).noneMatch(message -> message.contains(RAW_TOKEN));
        assertThat(messages).noneMatch(message -> message.contains("/auth/set-password?token="));
        assertThat(messages).anyMatch(message -> message.contains(userId.toString()));
    }

    @Test
    void setPasswordInvitationFailureAlsoNeverLeaksTheTokenOrLink() {
        doThrow(new MailSendException("smtp down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        var service = new EmailService(mailSender, "http://localhost:4200");
        var userId = UUID.randomUUID();

        service.sendSetPasswordInvitation(userId, "user@example.com", "User", RAW_TOKEN);

        List<String> messages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        assertThat(messages).noneMatch(message -> message.contains(RAW_TOKEN));
        assertThat(messages).noneMatch(message -> message.contains("/auth/set-password?token="));
        assertThat(messages).anyMatch(message -> message.contains(userId.toString()));
    }
}
