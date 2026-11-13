package org.kore.myapp.adapter.notification;

import org.kore.myapp.core.exception.CodeDeliveryException;
import org.kore.myapp.core.port.ResetCodeSender;
import org.kore.myapp.core.valueobject.Contact;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default delivery until a real email or SMS provider is connected: delivers nothing and fails, so a code never
 * leaks anywhere by accident. The caller records the failure.
 */
@Component
@ConditionalOnProperty(name = "app.password-reset.log-codes", havingValue = "false", matchIfMissing = true)
public class UnavailableResetCodeSender implements ResetCodeSender {

    @Override
    public void send(Contact contact, String code) {
        throw new CodeDeliveryException("No delivery channel is configured for " + contact.channel());
    }
}
