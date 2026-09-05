package com.votekante.repositories;

import com.votekante.entities.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Access to the anonymised ballot table.
 *
 * <p>Notice what is <b>not</b> here: there is no method that joins Vote to
 * User, and there never can be, because Vote has no user reference. The only
 * aggregate we expose is a count grouped by party (option), which is exactly
 * what a public result page is allowed to see.</p>
 */
public interface VoteRepository extends JpaRepository<Vote, String> {

    long countByElectionId(Long electionId);

    boolean existsByPartyId(Long partyId);

    /**
     * Live tally for an election: for every party that has received at
     * least one ballot, count the votes. Grouped purely by partyId –
     * the query never touches User, HasVoted or any identity data.
     *
     * @return rows of [partyId, voteCount]
     */
    @Query("select v.party.id, count(v) from Vote v where v.election.id = :electionId group by v.party.id")
    List<Object[]> tallyByParty(@Param("electionId") Long electionId);
}
