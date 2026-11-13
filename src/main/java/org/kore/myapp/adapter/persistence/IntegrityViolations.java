package org.kore.myapp.adapter.persistence;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Reads which database constraint rejected a write, so persistence adapters can translate the failure before it
 * crosses a port (constitution Principle VII).
 */
final class IntegrityViolations {

    private IntegrityViolations() {
    }

    /**
     * @param e the integrity violation Spring reported.
     * @return the name of the violated constraint, or null if the driver did not report one.
     */
    static String constraintName(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException cve) {
                return cve.getConstraintName();
            }
        }
        return null;
    }

    /**
     * @param what what was being written, for example {@code Password reset code}.
     * @param e    the integrity violation Spring reported.
     * @return an exception for a violation that is a bug rather than a business outcome, naming the constraint.
     */
    static IllegalStateException unexpected(String what, DataIntegrityViolationException e) {
        return new IllegalStateException(what + " rejected by constraint " + constraintName(e), e);
    }
}
