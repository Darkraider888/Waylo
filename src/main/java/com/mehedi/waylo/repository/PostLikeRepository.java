package com.mehedi.waylo.repository;

import com.mehedi.waylo.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    boolean existsByPostIdAndUserId(Long postId, Long userId);
    long countByPostId(Long postId);
    long deleteByPostIdAndUserId(Long postId, Long userId);
}
