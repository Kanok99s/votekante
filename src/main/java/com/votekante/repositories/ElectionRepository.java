package com.votekante.repositories;

import com.votekante.entities.Election;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ElectionRepository extends JpaRepository<Election, Long> {

    /** Elections with their party lists and (optional) creator loaded – avoids lazy access in views. */
    @Query("select distinct e from Election e " +
            "left join fetch e.parties left join fetch e.creator " +
            "order by e.createdAt desc, e.id desc")
    List<Election> findAllWithPartiesOrderByCreatedAtDesc();

    @Query("select distinct e from Election e " +
            "left join fetch e.parties left join fetch e.creator " +
            "where e.open = true order by e.createdAt desc, e.id desc")
    List<Election> findOpenWithPartiesOrderByCreatedAtDesc();

    /** Community polls created by one user (newest first), creator eagerly loaded. */
    @Query("select e from Election e left join fetch e.creator where e.creator.id = :creatorId " +
            "order by e.createdAt desc, e.id desc")
    List<Election> findCreatedBy(@Param("creatorId") Long creatorId);

    /** Case-insensitive lookup of a poll by its share code. */
    Optional<Election> findByJoinCodeIgnoreCase(String joinCode);
}
