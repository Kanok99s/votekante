package com.votekante.controllers;

import com.votekante.entities.Election;
import com.votekante.entities.User;
import com.votekante.repositories.UserRepository;
import com.votekante.services.PollService;
import com.votekante.services.VoteException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * Community polls for every signed-in user: a small creation wizard, a
 * "my polls" management page, and Kahoot-style join-by-code entry points
 * ({@code /join/XXXXXX} links and the code box on the ballot page).
 */
@Controller
public class PollController {

    private final PollService pollService;
    private final UserRepository userRepository;

    public PollController(PollService pollService, UserRepository userRepository) {
        this.pollService = pollService;
        this.userRepository = userRepository;
    }

    /** One row of the creator's dashboard: the poll plus live stats. */
    public static class PollRow {
        private final Election election;
        private final long options;
        private final long votes;

        public PollRow(Election election, long options, long votes) {
            this.election = election;
            this.options = options;
            this.votes = votes;
        }

        public Election getElection() { return election; }
        public long getOptions() { return options; }
        public long getVotes() { return votes; }
    }

    private User currentUser(Authentication auth) {
        if (auth == null) {
            throw new VoteException("You must be signed in.");
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new VoteException("Account not found."));
    }

    private boolean isVoter(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_VOTER"));
    }

    // ------------------------------------------------------------ creation

    @GetMapping("/polls/new")
    public String newPollForm(Model model) {
        model.addAttribute("optionRows", List.of("", ""));
        return "polls/new";
    }

    @PostMapping("/polls")
    public String createPoll(Authentication auth,
                             @RequestParam String question,
                             @RequestParam(required = false) String description,
                             @RequestParam(required = false) List<String> options,
                             RedirectAttributes ra,
                             Model model) {
        User user = currentUser(auth);
        try {
            Election poll = pollService.createPoll(user, question, description, options);
            ra.addFlashAttribute("ok", "Poll created - it is open for voting now. "
                    + "Share code " + poll.getJoinCode() + " with anyone who wants to vote.");
            return "redirect:/polls/mine";
        } catch (VoteException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("question", question == null ? "" : question);
            model.addAttribute("description", description == null ? "" : description);
            model.addAttribute("optionRows", options == null ? List.of("", "") : options);
            return "polls/new";
        }
    }

    // ---------------------------------------------------------- management

    @GetMapping("/polls/mine")
    public String myPolls(Authentication auth, Model model) {
        User user = currentUser(auth);
        List<Election> polls = pollService.listCreatedBy(user);
        List<PollRow> rows = new ArrayList<>();
        for (Election poll : polls) {
            rows.add(new PollRow(poll, pollService.optionCount(poll.getId()),
                    pollService.totalVotes(poll.getId())));
        }
        model.addAttribute("rows", rows);
        return "polls/mine";
    }

    @PostMapping("/polls/{id}/toggle")
    public String toggle(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            Election poll = pollService.toggle(currentUser(auth), id);
            ra.addFlashAttribute("ok", poll.isOpen()
                    ? "Poll \"" + poll.getName() + "\" is now OPEN - everyone can vote."
                    : "Poll \"" + poll.getName() + "\" is now CLOSED. Results are final.");
        } catch (VoteException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/polls/mine";
    }

    @PostMapping("/polls/{id}/delete")
    public String delete(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            pollService.delete(currentUser(auth), id);
            ra.addFlashAttribute("ok", "Poll deleted.");
        } catch (VoteException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/polls/mine";
    }

    // ------------------------------------------------------------ joining

    /** Code box on the ballot page. */
    @PostMapping("/polls/join")
    public String joinByCode(@RequestParam String code,
                             Authentication auth,
                             RedirectAttributes ra) {
        return resolveJoin(code, auth, ra);
    }

    /** Shareable link: /join/XXXXXX */
    @GetMapping("/join/{code}")
    public String joinByLink(@PathVariable String code,
                             Authentication auth,
                             RedirectAttributes ra) {
        return resolveJoin(code, auth, ra);
    }

    private String resolveJoin(String code, Authentication auth, RedirectAttributes ra) {
        Election poll;
        try {
            poll = pollService.findByCode(code);
        } catch (VoteException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/dashboard";
        }
        // Closed polls (or finished voting) land straight on the live results.
        if (!poll.isOpen() || !isVoter(auth)) {
            return "redirect:/results/" + poll.getId();
        }
        ra.addFlashAttribute("ok", "Joined \"" + poll.getName() + "\" - cast your vote below.");
        // Fragment anchors the ballot card for that poll on the dashboard.
        return "redirect:/voter/dashboard#poll-" + poll.getId();
    }
}
