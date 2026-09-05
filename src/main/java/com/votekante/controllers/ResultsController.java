package com.votekante.controllers;

import com.votekante.entities.Election;
import com.votekante.services.ElectionService;
import com.votekante.services.ResultService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Live results. Every signed-in user (VOTER and ADMIN) can watch counts.
 * The data comes straight from the anonymous tally query in VoteRepository –
 * no user information is ever involved.
 */
@Controller
public class ResultsController {

    private final ElectionService electionService;
    private final ResultService resultService;

    public ResultsController(ElectionService electionService, ResultService resultService) {
        this.electionService = electionService;
        this.resultService = resultService;
    }

    /** Pick a specific election, defaulting to the most recently created. */
    @GetMapping("/results")
    public String resultsIndex(@RequestParam(required = false) Long electionId) {
        if (electionId != null) {
            return "redirect:/results/" + electionId;
        }
        List<Election> elections = electionService.listElections();
        if (elections.isEmpty()) {
            return "redirect:/results/none";
        }
        return "redirect:/results/" + elections.get(0).getId();
    }

    @GetMapping("/results/{electionId}")
    public String results(@PathVariable Long electionId, Model model) {
        Election election = electionService.listElections().stream()
                .filter(e -> e.getId().equals(electionId))
                .findFirst()
                .orElse(null);
        if (election == null) {
            return "redirect:/results";
        }
        model.addAttribute("election", election);
        List<ResultService.ResultRow> rows = resultService.resultsFor(electionId);
        model.addAttribute("rows", rows);
        model.addAttribute("partyNames", rows.stream().map(ResultService.ResultRow::getPartyName).toList());
        model.addAttribute("partyVotes", rows.stream().map(ResultService.ResultRow::getVotes).toList());
        model.addAttribute("totalVotes", resultService.totalVotes(electionId));
        model.addAttribute("elections", electionService.listElections());
        return "results";
    }

    @GetMapping("/results/none")
    public String none(Model model) {
        model.addAttribute("elections", electionService.listElections());
        return "results";
    }
}
