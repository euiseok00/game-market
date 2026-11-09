package com.gamemarket.review;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Primary ReviewSource: Steam first, seed reviews when Steam throws, answers non-JSON, or has no reviews. */
@Primary
@Component
public class FallbackReviewSource implements ReviewSource {

    private static final Logger log = LoggerFactory.getLogger(FallbackReviewSource.class);

    private final ReviewSource steam;
    private final ReviewSource seed;

    @Autowired
    public FallbackReviewSource(SteamReviewSource steam, SeedReviewSource seed) {
        this((ReviewSource) steam, seed);
    }

    FallbackReviewSource(ReviewSource steam, ReviewSource seed) {
        this.steam = steam;
        this.seed = seed;
    }

    @Override
    public List<String> fetch(long steamAppId) {
        try {
            List<String> reviews = steam.fetch(steamAppId);
            if (!reviews.isEmpty()) {
                return reviews;
            }
            log.info("Steam returned no reviews for {}, using seed", steamAppId);
        } catch (RuntimeException e) {
            log.info("Steam reviews unavailable for {} ({}), using seed", steamAppId, e.getMessage());
        }
        return seed.fetch(steamAppId);
    }
}
