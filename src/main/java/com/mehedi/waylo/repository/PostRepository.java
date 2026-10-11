package com.mehedi.waylo.repository;

import com.mehedi.waylo.entity.Post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    @EntityGraph(attributePaths = {"user", "destination", "trip"})
    @org.springframework.data.jpa.repository.Query("SELECT p FROM Post p WHERE p.id = :id AND p.hidden = false AND p.deleted = false")
    Optional<Post> findWithAuthorAndTagsById(@org.springframework.data.repository.query.Param("id") Long id);

    @EntityGraph(attributePaths = {
            "user",
            "destination",
            "trip"
    })
    Page<Post> findByHiddenFalseAndDeletedFalseOrderByCreatedAtDescIdDesc(
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "user",
            "destination",
            "trip"
    })
    Page<Post> findByUserIdAndHiddenFalseAndDeletedFalseOrderByCreatedAtDescIdDesc(
            Long userId,
            Pageable pageable
    );

    long countByUserIdAndHiddenFalseAndDeletedFalse(Long userId);
}