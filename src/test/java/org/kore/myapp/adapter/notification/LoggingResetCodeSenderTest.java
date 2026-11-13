package org.kore.myapp.adapter.notification;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kore.myapp.core.valueobject.Contact;
import org.kore.myapp.core.valueobject.ContactChannel;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingResetCodeSenderTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingResetCodeSender.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void theLogLineHasTheChannelTheAddressAndTheCode() {
        new LoggingResetCodeSender().send(new Contact(ContactChannel.EMAIL, "budi@example.com"), "004213");

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("EMAIL").contains("budi@example.com").contains("004213");
    }

    @Test
    void aPhoneContactIsLoggedWithItsChannel() {
        new LoggingResetCodeSender().send(new Contact(ContactChannel.PHONE, "+6281234567890"), "123456");

        assertThat(appender.list.get(0).getFormattedMessage()).contains("PHONE").contains("+6281234567890");
    }
}
