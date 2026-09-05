package com.votekante.entities;

import jakarta.persistence.*;

/**
 * Registered account. Holds only identity and the hashed password –
 * never vote content.
 *
 * <p>"user" is a reserved word in MySQL, so the physical table is called
 * <b>app_user</b>.</p>
 */
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    /** BCrypt hash produced by Spring Security's PasswordEncoder. Never store a raw password. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role = Role.VOTER;

    protected User() {
        // JPA requires a no-arg constructor
    }

    public User(String username, String passwordHash, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
}
