package com.votekante.services;

/**
 * Domain error carrying a user-friendly message, e.g. "you already voted"
 * or "this election is closed". Thrown by service methods and translated
 * into a page message by controllers.
 */
public class VoteException extends RuntimeException {

    public VoteException(String message) {
        super(message);
    }
}
