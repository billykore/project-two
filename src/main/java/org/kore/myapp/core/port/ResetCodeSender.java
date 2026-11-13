package org.kore.myapp.core.port;

import org.kore.myapp.core.exception.CodeDeliveryException;
import org.kore.myapp.core.valueobject.Contact;

/**
 * Delivers a one-time password-reset code to the user's email address or phone number.
 */
public interface ResetCodeSender {

    /**
     * Delivers a password-reset code to a contact.
     *
     * @param contact where to deliver the code; its channel says whether it is an email or a text message.
     * @param code    the plain 6-digit code.
     * @throws CodeDeliveryException if the code could not be delivered; the message never contains the code.
     */
    void send(Contact contact, String code);
}
