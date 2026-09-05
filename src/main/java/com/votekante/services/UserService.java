package com.votekante.services;

import com.votekante.entities.Role;
import com.votekante.entities.User;
import com.votekante.repositories.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account management: registration of new voters plus admin bootstrap.
 * Passwords are encoded with BCrypt before anything reaches the DB.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registration endpoint logic. New accounts are always {@code VOTER};
     * admin accounts are only created by the bootstrap seeder so the
     * "register" page can never be abused to mint an admin.
     */
    @Transactional
    public User registerVoter(String username, String rawPassword) {
        String name = username == null ? "" : username.trim();
        if (name.length() < 3) {
            throw new VoteException("Username must be at least 3 characters long.");
        }
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new VoteException("Password must be at least 6 characters long.");
        }
        if (userRepository.existsByUsername(name)) {
            throw new VoteException("That username is already taken.");
        }
        return userRepository.save(new User(name, passwordEncoder.encode(rawPassword), Role.VOTER));
    }

    /** Creates or returns the configured admin used during first boot. */
    @Transactional
    public User ensureAdmin(String username, String rawPassword) {
        return userRepository.findByUsername(username).orElseGet(() ->
                userRepository.save(new User(username, passwordEncoder.encode(rawPassword), Role.ADMIN)));
    }
}
