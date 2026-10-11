package com.mehedi.waylo.repository;

import com.mehedi.waylo.entity.PostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {
    long countByPostIdAndHiddenFalseAndDeletedFalse(Long postId);

    @EntityGraph(attributePaths = {"user"})
    Page<PostComment> findByPostIdAndHiddenFalseAndDeletedFalseOrderByCreatedAtAscIdAsc(Long postId, Pageable pageable);
}
