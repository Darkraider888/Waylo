package com.mehedi.waylo.repository;

import com.mehedi.waylo.entity.PostSave;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostSaveRepository extends JpaRepository<PostSave, Long> {
    boolean existsByPostIdAndUserId(Long postId, Long userId);
    long deleteByPostIdAndUserId(Long postId, Long userId);

    @EntityGraph(attributePaths = {"post", "post.user", "post.destination", "post.trip"})
    Page<PostSave> findByUserIdAndPostHiddenFalseAndPostDeletedFalseOrderByIdDesc(Long userId, Pageable pageable);
}
