package org.kore.myapp.adapter.notification;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.kore.myapp.core.exception.CodeDeliveryException;
import org.kore.myapp.core.valueobject.Contact;
import org.kore.myapp.core.valueobject.ContactChannel;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnavailableResetCodeSenderTest {

    private final UnavailableResetCodeSender sender = new UnavailableResetCodeSender();

    @Test
    void failsWithoutRevealingTheCode() {
        Contact contact = new Contact(ContactChannel.EMAIL, "budi@example.com");

        assertThatThrownBy(() -> sender.send(contact, "004213"))
                .isInstanceOf(CodeDeliveryException.class)
                .hasMessageNotContaining("004213");
    }

    @Test
    void logsNothingItself() {
        Logger logger = (Logger) LoggerFactory.getLogger(UnavailableResetCodeSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> sender.send(new Contact(ContactChannel.PHONE, "+6281234567890"), "123456"))
                    .isInstanceOf(CodeDeliveryException.class);
            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
        }
    }
}
