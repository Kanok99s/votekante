package com.votekante.services;

import com.votekante.entities.Election;
import com.votekante.entities.Party;
import com.votekante.repositories.ElectionRepository;
import com.votekante.repositories.PartyRepository;
import com.votekante.repositories.VoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-side aggregation for the public results page.
 *
 * <p>Results are built purely from {@code vote} rows grouped by partyId;
 * the query never joins to User or HasVoted. Parties that received zero
 * votes are still listed with a count of 0, so the tally is complete.</p>
 */
@Service
public class ResultService {

    /** One result row rendered on the results page. */
    public static class ResultRow {
        private final String partyName;
        private final long votes;
        private final long rank;

        public ResultRow(String partyName, long votes, long rank) {
            this.partyName = partyName;
            this.votes = votes;
            this.rank = rank;
        }

        public String getPartyName() { return partyName; }
        public long getVotes() { return votes; }
        public long getRank() { return rank; }
    }

    private final ElectionRepository electionRepository;
    private final PartyRepository partyRepository;
    private final VoteRepository voteRepository;

    public ResultService(ElectionRepository electionRepository,
                         PartyRepository partyRepository,
                         VoteRepository voteRepository) {
        this.electionRepository = electionRepository;
        this.partyRepository = partyRepository;
        this.voteRepository = voteRepository;
    }

    @Transactional(readOnly = true)
    public List<ResultRow> resultsFor(Long electionId) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new VoteException("Election not found."));

        List<Party> parties = partyRepository.findByElectionIdOrderByNameAsc(electionId);

        // [partyId, count] from the anonymous vote table.
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Object[] row : voteRepository.tallyByParty(electionId)) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }

        // Sort parties by votes desc (0-vote parties at the end) for a ranking.
        List<ResultRow> ranked = new ArrayList<>();
        parties.stream()
                .sorted((a, b) -> Long.compare(
                        counts.getOrDefault(b.getId(), 0L),
                        counts.getOrDefault(a.getId(), 0L)))
                .forEach(p -> ranked.add(new ResultRow(p.getName(),
                        counts.getOrDefault(p.getId(), 0L), 0L)));

        for (int i = 0; i < ranked.size(); i++) {
            ResultRow r = ranked.get(i);
            ranked.set(i, new ResultRow(r.getPartyName(), r.getVotes(), i + 1L));
        }
        return ranked;
    }

    @Transactional(readOnly = true)
    public long totalVotes(Long electionId) {
        return voteRepository.countByElectionId(electionId);
    }
}
