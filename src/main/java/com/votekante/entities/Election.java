package com.votekante.entities;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One round of voting. An election is <em>open</em> while ballots are
 * accepted and <em>closed</em> once the result is final. Admins create,
 * open and close the official elections; any signed-in user can create a
 * community poll (also an Election) that they open/close themselves.
 *
 * <p>A community poll has a non-null {@link #creator} plus a short
 * {@link #joinCode} so the creator can share it (Kahoot-style). Official
 * elections have neither and are managed solely from the admin area.</p>
 */
@Entity
@Table(name = "election")
public class Election {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    /** The signed-in user who created this community poll; null for official elections. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    /** Short shareable code for community polls (e.g. "K7X2Q4"); null for official elections. */
    @Column(name = "join_code", unique = true, length = 8)
    private String joinCode;

    /** true while voting is being accepted. */
    @Column(nullable = false)
    private boolean open = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "election", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name ASC")
    private List<Party> parties = new ArrayList<>();

    protected Election() {
        // JPA requires a no-arg constructor
    }

    public Election(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /** Constructor for a community poll created by a signed-in user. */
    public Election(String name, String description, User creator) {
        this(name, description);
        this.creator = creator;
    }

    public void addParty(Party party) {
        parties.add(party);
        party.setElection(this);
    }

    public void removeParty(Party party) {
        parties.remove(party);
        party.setElection(null);
    }

    /** True when this election is a user-created community poll rather than an official one. */
    public boolean isCommunity() {
        return creator != null;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public User getCreator() { return creator; }
    public void setCreator(User creator) { this.creator = creator; }
    public String getJoinCode() { return joinCode; }
    public void setJoinCode(String joinCode) { this.joinCode = joinCode; }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Party> getParties() { return parties; }
}
