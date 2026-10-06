package net.enthusia.staff.persistence;

/** Signals an optimistic reconciliation write that lost its expected-revision race. */
public final class ReconciliationRevisionConflictException extends ModerationPersistenceException {
    private static final long serialVersionUID = 1L;

    public ReconciliationRevisionConflictException(String message) {
        super(message);
    }

    public ReconciliationRevisionConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
