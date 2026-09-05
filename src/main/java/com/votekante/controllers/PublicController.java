package com.votekante.controllers;

import com.votekante.entities.Election;
import com.votekante.entities.User;
import com.votekante.repositories.UserRepository;
import com.votekante.services.ElectionService;
import com.votekante.services.PollService;
import com.votekante.services.ResultService;
import com.votekante.services.VoteException;
import com.votekante.services.VoteService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Pages any visitor can reach without an account - the public "CV showcase"
 * part of the app. Anyone can browse the open polls and view a poll shared
 * with them via code; only casting a vote or creating/managing a poll
 * requires an account (enforced in the other controllers / security).
 */
@Controller
public class PublicController {

    private final ElectionService electionService;
    private final PollService pollService;
    private final ResultService resultService;
    private final VoteService voteService;
    private final UserRepository userRepository;

    public PublicController(ElectionService electionService,
                            PollService pollService,
                            ResultService resultService,
                            VoteService voteService,
                            UserRepository userRepository) {
        this.electionService = electionService;
        this.pollService = pollService;
        this.resultService = resultService;
        this.voteService = voteService;
        this.userRepository = userRepository;
    }

    /** Public dashboard: the same ballot page, rendered read-only for guests. */
    @GetMapping("/polls/browse")
    public String browse(Authentication auth, Model model) {
        if (hasRole(auth, "ROLE_VOTER")) {
            return "redirect:/voter/dashboard";
        }
        if (hasRole(auth, "ROLE_ADMIN")) {
            return "redirect:/admin";
        }
        List<Election> open = electionService.listOpenElections();
        List<VoterController.BallotItem> items = open.stream()
                .map(e -> new VoterController.BallotItem(e, e.getParties(), false))
                .toList();
        model.addAttribute("ballots", items);
        model.addAttribute("signedIn", false);
        return "voter/dashboard";
    }

    /**
     * A shared poll's public landing page (Kahoot-style). Open polls show the
     * question, live tally and a "sign in to vote" prompt; closed polls send
     * visitors to the final results instead.
     */
    @GetMapping("/poll/{code}")
    public String pollView(@PathVariable String code,
                           Authentication auth,
                           RedirectAttributes ra,
                           Model model) {
        Election poll;
        try {
            poll = pollService.findByCode(code);
        } catch (VoteException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/polls/browse";
        }
        if (!poll.isOpen()) {
            return "redirect:/results/" + poll.getId();
        }

        boolean voterRole = hasRole(auth, "ROLE_VOTER");
        boolean signedIn = hasRole(auth, "ROLE_VOTER") || hasRole(auth, "ROLE_ADMIN");
        boolean alreadyVoted = false;
        if (voterRole && auth != null) {
            User user = userRepository.findByUsername(auth.getName()).orElse(null);
            alreadyVoted = user != null && voteService.hasVoted(user.getId(), poll.getId());
        }

        model.addAttribute("election", poll);
        model.addAttribute("rows", resultService.resultsFor(poll.getId()));
        model.addAttribute("totalVotes", resultService.totalVotes(poll.getId()));
        model.addAttribute("signedIn", signedIn);
        model.addAttribute("canVote", voterRole);
        model.addAttribute("alreadyVoted", alreadyVoted);
        return "polls/view";
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals(role));
    }
}
