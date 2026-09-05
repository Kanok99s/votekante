package com.votekante.services;

import com.votekante.entities.*;
import com.votekante.repositories.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The heart of the voting flow.
 *
 * <p>{@code castVote} runs inside a single transaction: either both the
 * anonymous {@code vote} row and the {@code has_voted} receipt are written,
 * or neither is. A crash or exception in the middle can never leave the
 * system in the inconsistent state "vote stored but voter not marked" or
 * "marked as voted but no ballot recorded".</p>
 */
@Service
public class VoteService {

    private final UserRepository userRepository;
    private final PartyRepository partyRepository;
    private final ElectionRepository electionRepository;
    private final HasVotedRepository hasVotedRepository;
    private final VoteRepository voteRepository;

    public VoteService(UserRepository userRepository,
                       PartyRepository partyRepository,
                       ElectionRepository electionRepository,
                       HasVotedRepository hasVotedRepository,
                       VoteRepository voteRepository) {
        this.userRepository = userRepository;
        this.partyRepository = partyRepository;
        this.electionRepository = electionRepository;
        this.hasVotedRepository = hasVotedRepository;
        this.voteRepository = voteRepository;
    }

    /**
     * Casts one ballot.
     *
     * <p>Order of checks: election open? party belongs to the election?
     * voter already marked in HasVoted? Only then do we write. The unique
     * (user_id, election_id) constraint on has_voted is the backstop that
     * defeats double-submits even if two requests pass the {@code exists}
     * check simultaneously.</p>
     */
    @Transactional
    public void castVote(Long userId, Long electionId, Long partyId) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new VoteException("That election does not exist."));
        if (!election.isOpen()) {
            throw new VoteException("This election is closed — no further ballots are accepted.");
        }

        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new VoteException("That party does not exist."));
        // A ballot must reference a party that actually runs in this election.
        if (!party.getElection().getId().equals(electionId)) {
            throw new VoteException("This party is not running in the selected election.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new VoteException("User not found."));

        // Fast-path check against the receipt table.
        if (hasVotedRepository.existsByUserIdAndElectionId(userId, electionId)) {
            throw new VoteException("You have already voted in this election.");
        }

        // Single transaction: insert anonymous ballot + receipt.
        try {
            voteRepository.save(new Vote(party, election));
            hasVotedRepository.saveAndFlush(new HasVoted(user, election));
        } catch (DataIntegrityViolationException e) {
            // Unique constraint on (user_id, election_id) caught a concurrent
            // double-submit that slipped between the check above and the flush.
            throw new VoteException("You have already voted in this election.");
        }
    }

    /** Whether a user has already voted in the given election. */
    @Transactional(readOnly = true)
    public boolean hasVoted(Long userId, Long electionId) {
        return hasVotedRepository.existsByUserIdAndElectionId(userId, electionId);
    }
}
