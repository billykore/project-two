package org.kore.myapp.core.valueobject;

/**
 * A field of a registration request, named as clients send it.
 */
public enum RegistrationField {
    USERNAME("username"),
    PASSWORD("password"),
    NAME("name"),
    EMAIL("email"),
    PHONE("phone");

    private final String jsonName;

    RegistrationField(String jsonName) {
        this.jsonName = jsonName;
    }

    /**
     * @return the property name clients use for this field.
     */
    public String jsonName() {
        return jsonName;
    }
}
