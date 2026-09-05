package com.votekante.repositories;

import com.votekante.entities.Party;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PartyRepository extends JpaRepository<Party, Long> {

    List<Party> findByElectionIdOrderByNameAsc(Long electionId);

    long countByElectionId(Long electionId);

    /** Loads a party together with its election to avoid lazy access in views. */
    @Query("select p from Party p join fetch p.election where p.id = :partyId")
    Optional<Party> findByIdWithElection(long partyId);
}
