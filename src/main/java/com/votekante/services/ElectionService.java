package com.votekante.services;

import com.votekante.entities.Election;
import com.votekante.entities.Party;
import com.votekante.repositories.ElectionRepository;
import com.votekante.repositories.PartyRepository;
import com.votekante.repositories.VoteRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Admin-facing management of elections and their party options.
 */
@Service
public class ElectionService {

    private final ElectionRepository electionRepository;
    private final PartyRepository partyRepository;
    private final VoteRepository voteRepository;

    public ElectionService(ElectionRepository electionRepository,
                           PartyRepository partyRepository,
                           VoteRepository voteRepository) {
        this.electionRepository = electionRepository;
        this.partyRepository = partyRepository;
        this.voteRepository = voteRepository;
    }

    @Transactional(readOnly = true)
    public List<Election> listElections() {
        return electionRepository.findAllWithPartiesOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Election> listOpenElections() {
        return electionRepository.findOpenWithPartiesOrderByCreatedAtDesc();
    }

    @Transactional
    public Election createElection(String name, String description) {
        if (name == null || name.trim().isEmpty()) {
            throw new VoteException("An election needs a name.");
        }
        return electionRepository.save(new Election(name.trim(), description == null ? "" : description.trim()));
    }

    /** Flips an election between open (accepting ballots) and closed. */
    @Transactional
    public Election toggleOpen(Long electionId) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new VoteException("Election not found."));
        if (election.isOpen()) {
            election.setOpen(false);
        } else {
            if (partyRepository.countByElectionId(electionId) == 0) {
                throw new VoteException("Add at least one party before opening the election.");
            }
            election.setOpen(true);
        }
        return electionRepository.save(election);
    }

    @Transactional
    public Party addParty(Long electionId, String name, String description) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new VoteException("Election not found."));
        if (election.isOpen()) {
            throw new VoteException("Close the election before changing the candidate list.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new VoteException("A party needs a name.");
        }
        try {
            return partyRepository.save(new Party(name.trim(), description == null ? "" : description.trim(), election));
        } catch (DataIntegrityViolationException e) {
            throw new VoteException("A party with that name already exists in this election.");
        }
    }

    @Transactional
    public Party updateParty(Long partyId, Long electionId, String name, String description) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new VoteException("Party not found."));
        if (party.getElection().isOpen()) {
            throw new VoteException("Close the election before editing its candidates.");
        }
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new VoteException("Election not found."));
        if (election.isOpen()) {
            throw new VoteException("Close the target election before moving candidates into it.");
        }
        if (voteRepository.existsByPartyId(partyId)) {
            throw new VoteException("This party already received ballots — editing or moving it would falsify results.");
        }
        try {
            party.setName(name.trim());
            party.setDescription(description == null ? "" : description.trim());
            party.setElection(election);
            return partyRepository.save(party);
        } catch (DataIntegrityViolationException e) {
            throw new VoteException("A party with that name already exists in the target election.");
        }
    }

    @Transactional
    public void deleteParty(Long partyId) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new VoteException("Party not found."));
        if (party.getElection().isOpen()) {
            throw new VoteException("Close the election before deleting candidates.");
        }
        if (voteRepository.existsByPartyId(partyId)) {
            throw new VoteException("This party already received ballots and cannot be deleted.");
        }
        partyRepository.delete(party);
    }

    @Transactional(readOnly = true)
    public List<Party> partiesFor(Long electionId) {
        return partyRepository.findByElectionIdOrderByNameAsc(electionId);
    }
}
