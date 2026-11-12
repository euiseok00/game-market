package com.gamemarket.review.dto;

import com.gamemarket.review.Review;

/** userId is null for seed reviews; the client shows an edit button when it matches the logged-in user. */
public record ReviewDto(Long id, String content, Long userId) {

    public static ReviewDto of(Review r) {
        return new ReviewDto(r.getId(), r.getContent(), r.getUserId());
    }
}
