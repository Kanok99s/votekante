package com.votekante.entities;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * An anonymised ballot. It records ONLY <em>what</em> was chosen
 * (party + election) and deliberately carries NO reference back to the
 * voter: there is no userId column and no relationship to {@link User}.
 *
 * <h3>Why are Vote and HasVoted separate tables?</h3>
 * <ol>
 *   <li><b>Content vs. receipt separation.</b> {@code vote} holds the choice
 *       ("Party A, election 3"). {@code has_voted} holds the receipt
 *       ("user 42 voted in election 3"). Neither table alone can link a
 *       voter to their choice, and no SQL join can either, because the two
 *       tables share no key and {@code vote} has no user reference.</li>
 *   <li><b>One-vote enforcement without leaking the vote.</b> The unique
 *       constraint enforcing "one vote per user per election" must live on
 *       the table that knows the user, i.e. {@code has_voted}. If that
 *       constraint lived on {@code vote} it would require a userId column
 *       there, which would break anonymity.</li>
 *   <li><b>Ballot secrecy is structural, not conventional.</b> Nothing in the
 *       code "promises" not to look up who voted for whom – the schema simply
 *       makes it impossible.</li>
 * </ol>
 *
 * <h3>Why a random UUID primary key?</h3>
 * An auto-increment id would leak information: the Nth ballot inserted maps
 * directly to the Nth voter who pressed submit, letting an attacker who can
 * watch the DB re-identify voters by ordering. A random UUID removes that
 * ordering correlation.
 */
@Entity
@Table(name = "vote")
public class Vote {

    @Id
    @Column(name = "vote_id", length = 36)
    private String voteId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    protected Vote() {
        // JPA requires a no-arg constructor
    }

    public Vote(Party party, Election election) {
        this.party = party;
        this.election = election;
    }

    @PrePersist
    void assignIdIfMissing() {
        if (voteId == null) {
            voteId = UUID.randomUUID().toString();
        }
    }

    public String getVoteId() { return voteId; }
    public Party getParty() { return party; }
    public Election getElection() { return election; }
}
