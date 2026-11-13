package org.kore.myapp.core.valueobject;

/**
 * A normalized email address or phone number that identifies an account and receives a one-time code.
 *
 * @param channel whether {@code address} is an email address or a phone number.
 * @param address the lowercase email address, or the phone number in international form.
 */
public record Contact(ContactChannel channel, String address) {
}
