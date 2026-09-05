package com.votekante.entities;

/**
 * Application roles.
 *
 * <p>VOTER  – may cast exactly one anonymous ballot per election and view results.</p>
 * <p>ADMIN  – manages parties and elections (open/close), may view results.</p>
 */
public enum Role {
    VOTER,
    ADMIN
}
