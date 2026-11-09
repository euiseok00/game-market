package com.gamemarket.review;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Fake reviews from seed/reviews.json, keyed by Steam app id. */
@Component
public class SeedReviewSource implements ReviewSource {

    private final Map<String, List<String>> reviews;

    public SeedReviewSource(JsonMapper json) {
        try (InputStream in = new ClassPathResource("seed/reviews.json").getInputStream()) {
            this.reviews = json.readValue(in, new TypeReference<>() { });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<String> fetch(long steamAppId) {
        return reviews.getOrDefault(String.valueOf(steamAppId), List.of());
    }
}
