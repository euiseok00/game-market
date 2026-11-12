package com.gamemarket.review;

import org.springframework.stereotype.Component;

/** Reviews stored in the review table (seeded from seed/reviews.json by SeedLoader). */
@Component
public class DbReviewSource implements ReviewSource {

    private final ReviewRepository reviews;

    public DbReviewSource(ReviewRepository reviews) {
        this.reviews = reviews;
    }

    @Override
    public Reviews fetch(Long gameId) {
        return Reviews.seed(reviews.findByGameIdOrderById(gameId).stream()
                .map(r -> new Reviews.Item(r.getId(), r.getContent()))
                .toList());
    }
}
