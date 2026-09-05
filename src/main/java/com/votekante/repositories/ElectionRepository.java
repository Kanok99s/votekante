package com.votekante.repositories;

import com.votekante.entities.Election;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ElectionRepository extends JpaRepository<Election, Long> {

    /** Elections with their party lists loaded (avoids lazy loading in views). */
    @Query("select distinct e from Election e left join fetch e.parties order by e.createdAt desc, e.id desc")
    List<Election> findAllWithPartiesOrderByCreatedAtDesc();

    @Query("select distinct e from Election e left join fetch e.parties where e.open = true order by e.createdAt desc, e.id desc")
    List<Election> findOpenWithPartiesOrderByCreatedAtDesc();
}
