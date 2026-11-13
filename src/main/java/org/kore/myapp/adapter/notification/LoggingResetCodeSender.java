package org.kore.myapp.adapter.notification;

import lombok.extern.slf4j.Slf4j;
import org.kore.myapp.core.port.ResetCodeSender;
import org.kore.myapp.core.valueobject.Contact;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Development delivery: writes the code to the application log, so a developer can read it without an email or SMS
 * provider. Active only when {@code app.password-reset.log-codes=true}; it must not be enabled in production.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.password-reset.log-codes", havingValue = "true")
public class LoggingResetCodeSender implements ResetCodeSender {

    @Override
    public void send(Contact contact, String code) {
        log.info("password reset code for {} {}: {}", contact.channel(), contact.address(), code);
    }
}
