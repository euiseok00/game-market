package com.gamemarket.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class FallbackReviewSourceTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final SeedReviewSource seed = new SeedReviewSource(json);
    private static final long PORTAL_2 = 620;

    @Test
    void steamThrows_usesSeed() {
        ReviewSource steam = appId -> { throw new IllegalStateException("not json"); };
        assertThat(new FallbackReviewSource(steam, seed).fetch(PORTAL_2)).isEqualTo(seed.fetch(PORTAL_2));
    }

    @Test
    void steamReturnsEmpty_usesSeed() {
        ReviewSource steam = appId -> List.of();
        assertThat(new FallbackReviewSource(steam, seed).fetch(PORTAL_2)).isEqualTo(seed.fetch(PORTAL_2));
    }

    @Test
    void steamReturnsReviews_usesSteam() {
        ReviewSource steam = appId -> List.of("real review");
        assertThat(new FallbackReviewSource(steam, seed).fetch(PORTAL_2)).containsExactly("real review");
    }

    @Test
    void seed_hasReviewsForEverySeedGame() throws Exception {
        List<Map<String, Object>> games;
        try (InputStream in = new ClassPathResource("seed/games.json").getInputStream()) {
            games = json.readValue(in, new TypeReference<>() { });
        }
        assertThat(games).isNotEmpty().allSatisfy(g ->
                assertThat(seed.fetch(((Number) g.get("steamAppId")).longValue())).isNotEmpty());
    }
}
