package com.mehedi.waylo.repository;

import com.mehedi.waylo.entity.Destination;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DestinationRepository
        extends JpaRepository<Destination, Long> {

    @Query("""
        SELECT d
        FROM Destination d
        WHERE LOWER(d.name)
              LIKE LOWER(CONCAT('%', :term, '%'))
           OR LOWER(COALESCE(d.district, ''))
              LIKE LOWER(CONCAT('%', :term, '%'))
           OR LOWER(COALESCE(d.country, ''))
              LIKE LOWER(CONCAT('%', :term, '%'))
        ORDER BY d.name
        """)
    List<Destination> search(@Param("term") String term);
}