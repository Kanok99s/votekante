package com.votekante.controllers;

import com.votekante.entities.Election;
import com.votekante.entities.Party;
import com.votekante.entities.User;
import com.votekante.repositories.UserRepository;
import com.votekante.services.ElectionService;
import com.votekante.services.VoteException;
import com.votekante.services.VoteService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The voter experience: a dashboard listing the open elections as radio
 * groups, a strict one-ballot submit, and an opaque "thank you" page that
 * never reveals the recorded choice.
 */
@Controller
public class VoterController {

    private final VoteService voteService;
    private final ElectionService electionService;
    private final UserRepository userRepository;

    public VoterController(VoteService voteService,
                           ElectionService electionService,
                           UserRepository userRepository) {
        this.voteService = voteService;
        this.electionService = electionService;
        this.userRepository = userRepository;
    }

    /** View-model for one election shown on the ballot dashboard. */
    public static class BallotItem {
        private final Election election;
        private final List<Party> parties;
        private final boolean alreadyVoted;

        public BallotItem(Election election, List<Party> parties, boolean alreadyVoted) {
            this.election = election;
            this.parties = parties;
            this.alreadyVoted = alreadyVoted;
        }

        public Election getElection() { return election; }
        public List<Party> getParties() { return parties; }
        public boolean isAlreadyVoted() { return alreadyVoted; }
    }

    private User currentUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new VoteException("Account not found."));
    }

    @GetMapping("/voter")
    public String voterHome() {
        return "redirect:/voter/dashboard";
    }

    @GetMapping("/voter/dashboard")
    public String dashboard(Authentication auth, Model model) {
        User user = currentUser(auth);

        List<Election> openElections = electionService.listOpenElections();
        Set<Long> votedIds = new LinkedHashSet<>();
        for (Election election : openElections) {
            if (voteService.hasVoted(user.getId(), election.getId())) {
                votedIds.add(election.getId());
            }
        }

        List<BallotItem> items = openElections.stream()
                .map(e -> new BallotItem(e, e.getParties(), votedIds.contains(e.getId())))
                .toList();

        model.addAttribute("ballots", items);
        model.addAttribute("signedIn", true);
        return "voter/dashboard";
    }

    @PostMapping("/voter/vote")
    public String castVote(Authentication auth,
                           @RequestParam Long electionId,
                           @RequestParam Long partyId,
                           RedirectAttributes ra) {
        User user = currentUser(auth);
        try {
            voteService.castVote(user.getId(), electionId, partyId);
            // Intentionally no party/choice data on the confirmation page.
            return "redirect:/voter/confirmed";
        } catch (VoteException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/voter/dashboard";
        }
    }

    @GetMapping("/voter/confirmed")
    public String confirmed() {
        // No request data reaches this page, so there is nothing that could
        // echo which party was selected — reinforcing the anonymity guarantee.
        return "voter/confirmed";
    }
}
