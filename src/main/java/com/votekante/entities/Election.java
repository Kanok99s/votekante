package com.votekante.entities;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One round of voting. An election is <em>open</em> while ballots are
 * accepted and <em>closed</em> once the result is final. Admins create,
 * open and close elections; voters can only cast ballots in open ones.
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

    public void addParty(Party party) {
        parties.add(party);
        party.setElection(this);
    }

    public void removeParty(Party party) {
        parties.remove(party);
        party.setElection(null);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Party> getParties() { return parties; }
}
