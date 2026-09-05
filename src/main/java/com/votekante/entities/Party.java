package com.votekante.entities;

import jakarta.persistence.*;

/**
 * A candidate/option a voter can choose within an election.
 * Each party belongs to exactly one election, so a Vote always has a
 * well-defined (election, party) pair and results can be tallied
 * per election without ever touching user data.
 */
@Entity
@Table(name = "party",
        uniqueConstraints = @UniqueConstraint(name = "uk_party_name_in_election", columnNames = {"name", "election_id"}))
public class Party {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    protected Party() {
        // JPA requires a no-arg constructor
    }

    public Party(String name, String description, Election election) {
        this.name = name;
        this.description = description;
        this.election = election;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Election getElection() { return election; }
    public void setElection(Election election) { this.election = election; }
}
