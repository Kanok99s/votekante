package com.votekante.entities;

import jakarta.persistence.*;

/**
 * Receipt table: records the <em>fact</em> that a user cast a ballot in an
 * election, but never <em>what</em> they chose.
 *
 * <p>The (user_id, election_id) pair is unique, which is what enforces
 * "one person, one vote". Because this table is the only thing that knows
 * a user took part, it is deliberately kept separate from {@link Vote} –
 * see the rationale documented there.</p>
 */
@Entity
@Table(name = "has_voted",
        uniqueConstraints = @UniqueConstraint(name = "uk_has_voted_user_election", columnNames = {"user_id", "election_id"}))
public class HasVoted {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    /** Always true once the row exists; kept explicit so the intent is obvious. */
    @Column(nullable = false)
    private boolean voted = true;

    protected HasVoted() {
        // JPA requires a no-arg constructor
    }

    public HasVoted(User user, Election election) {
        this.user = user;
        this.election = election;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Election getElection() { return election; }
    public boolean isVoted() { return voted; }
}
