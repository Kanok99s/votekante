package com.votekante.repositories;

import com.votekante.entities.HasVoted;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Receipt store. The unique (user_id, election_id) constraint is the
 * database-level guarantee of "one person, one vote" and is what makes the
 * application safe under double-submits and concurrent requests.
 */
public interface HasVotedRepository extends JpaRepository<HasVoted, Long> {

    boolean existsByUserIdAndElectionId(Long userId, Long electionId);

    List<HasVoted> findByUserId(Long userId);
}
