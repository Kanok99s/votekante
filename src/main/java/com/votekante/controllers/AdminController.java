package com.votekante.controllers;

import com.votekante.entities.Election;
import com.votekante.entities.Party;
import com.votekante.repositories.PartyRepository;
import com.votekante.services.ElectionService;
import com.votekante.services.VoteException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Admin area (ROLE_ADMIN only): elections dashboard, opening/closing
 * elections, and the CRUD for the candidate parties of each election.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final ElectionService electionService;
    private final PartyRepository partyRepository;

    public AdminController(ElectionService electionService, PartyRepository partyRepository) {
        this.electionService = electionService;
        this.partyRepository = partyRepository;
    }

    @GetMapping
    public String adminHome() {
        return "redirect:/admin/elections";
    }

    // ---------------------------------------------------------------- elections

    @GetMapping("/elections")
    public String elections(Model model) {
        model.addAttribute("elections", electionService.listElections());
        return "admin/elections";
    }

    @PostMapping("/elections")
    public String createElection(@RequestParam String name,
                                 @RequestParam(required = false) String description,
                                 RedirectAttributes ra) {
        try {
            electionService.createElection(name, description);
            ra.addFlashAttribute("ok", "Election created. Add parties, then open it.");
        } catch (VoteException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/elections";
    }

    @PostMapping("/elections/{id}/toggle")
    public String toggleElection(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Election election = electionService.toggleOpen(id);
            ra.addFlashAttribute("ok", election.isOpen()
                    ? "Election \"" + election.getName() + "\" is now OPEN — voters can cast ballots."
                    : "Election \"" + election.getName() + "\" is now CLOSED. Results are final.");
        } catch (VoteException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/elections";
    }

    // ---------------------------------------------------------------- parties

    @GetMapping("/elections/{electionId}/parties")
    public String manageParties(@PathVariable Long electionId, Model model) {
        Election election = electionService.listElections().stream()
                .filter(e -> e.getId().equals(electionId))
                .findFirst()
                .orElse(null);
        if (election == null) {
            return "redirect:/admin/elections";
        }
        model.addAttribute("election", election);
        model.addAttribute("parties", electionService.partiesFor(electionId));
        return "admin/parties";
    }

    @PostMapping("/elections/{electionId}/parties")
    public String addParty(@PathVariable Long electionId,
                           @RequestParam String name,
                           @RequestParam(required = false) String description,
                           RedirectAttributes ra) {
        try {
            electionService.addParty(electionId, name, description);
            ra.addFlashAttribute("ok", "Party added to the ballot.");
        } catch (VoteException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/elections/" + electionId + "/parties";
    }

    @PostMapping("/parties/{partyId}/delete")
    public String deleteParty(@PathVariable Long partyId, RedirectAttributes ra) {
        Party party = partyRepository.findByIdWithElection(partyId).orElse(null);
        if (party == null) {
            return "redirect:/admin/elections";
        }
        Long electionId = party.getElection().getId();
        try {
            electionService.deleteParty(partyId);
            ra.addFlashAttribute("ok", "Party deleted.");
        } catch (VoteException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/elections/" + electionId + "/parties";
    }

    /** Edit form: pick the party and the election it belongs to. */
    @GetMapping("/parties/{partyId}/edit")
    public String editPartyForm(@PathVariable Long partyId, Model model) {
        Party party = partyRepository.findByIdWithElection(partyId).orElse(null);
        if (party == null) {
            return "redirect:/admin/elections";
        }
        List<Election> elections = electionService.listElections();
        model.addAttribute("party", party);
        model.addAttribute("elections", elections);
        return "admin/party-form";
    }

    @PostMapping("/parties/{partyId}/edit")
    public String editParty(@PathVariable Long partyId,
                            @RequestParam Long electionId,
                            @RequestParam String name,
                            @RequestParam(required = false) String description,
                            RedirectAttributes ra) {
        Long originalElectionId = partyRepository.findByIdWithElection(partyId)
                .map(p -> p.getElection().getId())
                .orElse(electionId);
        try {
            electionService.updateParty(partyId, electionId, name, description);
            ra.addFlashAttribute("ok", "Party updated.");
        } catch (VoteException e) {
            ra.addFlashAttribute("error", e.getMessage());
            // Stay on the election the party actually belongs to.
            return "redirect:/admin/elections/" + originalElectionId + "/parties";
        }
        return "redirect:/admin/elections/" + electionId + "/parties";
    }
}
