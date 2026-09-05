package com.votekante.services;

import com.votekante.entities.Election;
import com.votekante.entities.Party;
import com.votekante.entities.Role;
import com.votekante.entities.User;
import com.votekante.repositories.ElectionRepository;
import com.votekante.repositories.PartyRepository;
import com.votekante.repositories.VoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Community polls: any signed-in user can create a lightweight, anonymous
 * poll (a question plus answer options) that is open for voting immediately
 * and reachable through a short share code - a "Kahoot-like" companion to
 * the admin-run official elections.
 *
 * <p>Polls reuse the exact same Election/Party/Vote machinery as official
 * elections, so anonymity ("nobody can link a ballot to a voter") and the
 * one-account-one-vote guarantee hold for them automatically.</p>
 */
@Service
public class PollService {

    /** Character set avoids look-alikes (0/O, 1/I) so codes are easy to read aloud. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final int MAX_OPTIONS = 12;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ElectionRepository electionRepository;
    private final PartyRepository partyRepository;
    private final VoteRepository voteRepository;

    public PollService(ElectionRepository electionRepository,
                       PartyRepository partyRepository,
                       VoteRepository voteRepository) {
        this.electionRepository = electionRepository;
        this.partyRepository = partyRepository;
        this.voteRepository = voteRepository;
    }

    /**
     * Creates a community poll that is immediately open for voting, with the
     * given answer options stored as parties. The poll is assigned a unique
     * share code that never contains ambiguous characters.
     */
    @Transactional
    public Election createPoll(User creator, String question, String description, List<String> options) {
        if (creator == null) {
            throw new VoteException("You must be signed in to create a poll.");
        }
        String title = trimToNull(question);
        if (title == null) {
            throw new VoteException("Give your poll a question.");
        }
        if (title.length() > 120) {
            throw new VoteException("Keep the question to 120 characters or fewer.");
        }
        String desc = trimToNull(description);
        if (desc != null && desc.length() > 500) {
            throw new VoteException("Keep the description to 500 characters or fewer.");
        }

        List<String> names = normalizeOptions(options);
        if (names.size() < 2) {
            throw new VoteException("Add at least two answer options.");
        }
        if (names.size() > MAX_OPTIONS) {
            throw new VoteException("A poll can have at most " + MAX_OPTIONS + " answer options.");
        }
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                throw new VoteException("Each option must be unique - \"" + name + "\" appears twice.");
            }
        }

        Election election = new Election(title, desc == null ? "" : desc, creator);
        election.setOpen(true);
        election.setJoinCode(generateUniqueCode());
        for (String name : names) {
            election.addParty(new Party(name, "", election));
        }
        return electionRepository.save(election);
    }

    /** Finds a poll by its share code (case-insensitive), or throws. */
    @Transactional(readOnly = true)
    public Election findByCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new VoteException("Enter a poll code.");
        }
        return electionRepository.findByJoinCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new VoteException(
                        "No poll has the code \"" + code.trim().toUpperCase(Locale.ROOT) + "\"."));
    }

    /** All community polls created by the given user. */
    @Transactional(readOnly = true)
    public List<Election> listCreatedBy(User creator) {
        if (creator == null) {
            return List.of();
        }
        return electionRepository.findCreatedBy(creator.getId());
    }

    /** Opens or closes a poll. Only its creator (or an admin, as moderator) may do this. */
    @Transactional
    public Election toggle(User actor, Long pollId) {
        Election election = requireOwnerOrModerator(actor, pollId);
        if (election.isOpen()) {
            election.setOpen(false);
        } else {
            if (partyRepository.countByElectionId(election.getId()) == 0) {
                throw new VoteException("Add answer options before opening the poll.");
            }
            election.setOpen(true);
        }
        return electionRepository.save(election);
    }

    /**
     * Deletes a community poll. Allowed only while closed and only when no
     * ballot has been cast - deleting an election that received votes would
     * erase people's votes or violate the foreign keys that protect them.
     */
    @Transactional
    public void delete(User actor, Long pollId) {
        Election election = requireOwnerOrModerator(actor, pollId);
        if (election.isOpen()) {
            throw new VoteException("Close the poll before deleting it.");
        }
        if (voteRepository.countByElectionId(election.getId()) > 0) {
            throw new VoteException("This poll already received ballots and cannot be deleted.");
        }
        electionRepository.delete(election);
    }

    /** Number of ballots cast in a poll (used on the creator's dashboard). */
    @Transactional(readOnly = true)
    public long totalVotes(Long pollId) {
        return voteRepository.countByElectionId(pollId);
    }

    /** Number of answer options in a poll (used on the creator's dashboard). */
    @Transactional(readOnly = true)
    public long optionCount(Long pollId) {
        return partyRepository.countByElectionId(pollId);
    }

    private Election requireOwnerOrModerator(User actor, Long pollId) {
        Election election = electionRepository.findById(pollId)
                .orElseThrow(() -> new VoteException("Poll not found."));
        if (election.getCreator() == null) {
            throw new VoteException("That is an official election - manage it from the admin area.");
        }
        boolean isCreator = actor != null && election.getCreator().getId().equals(actor.getId());
        boolean isAdmin = actor != null && actor.getRole() == Role.ADMIN;
        if (!isCreator && !isAdmin) {
            throw new VoteException("Only the creator of a poll can manage it.");
        }
        return election;
    }

    private List<String> normalizeOptions(List<String> options) {
        List<String> names = new ArrayList<>();
        if (options == null) {
            return names;
        }
        for (String option : options) {
            String trimmed = trimToNull(option);
            if (trimmed == null) {
                continue; // silently drop empty rows left by the form
            }
            if (trimmed.length() > 120) {
                throw new VoteException("Keep each answer option to 120 characters or fewer.");
            }
            names.add(trimmed);
        }
        return names;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 50; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (electionRepository.findByJoinCodeIgnoreCase(code).isEmpty()) {
                return code;
            }
        }
        throw new VoteException("Could not allocate a unique poll code. Please try again.");
    }
}
