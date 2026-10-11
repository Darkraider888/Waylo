package com.mehedi.waylo.dto;

import com.mehedi.waylo.entity.Post;

public record PostCard(
        Post post,
        long likeCount,
        long commentCount,
        boolean liked,
        boolean saved
) { }
