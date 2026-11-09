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
        Reviews got = new FallbackReviewSource(steam, seed).fetch(PORTAL_2);
        assertThat(got).isEqualTo(seed.fetch(PORTAL_2));
        assertThat(got.source()).isEqualTo(Reviews.SEED);
    }

    @Test
    void steamReturnsEmpty_usesSeed() {
        ReviewSource steam = appId -> Reviews.steam(List.of());
        Reviews got = new FallbackReviewSource(steam, seed).fetch(PORTAL_2);
        assertThat(got.texts()).isEqualTo(seed.fetch(PORTAL_2).texts());
        assertThat(got.source()).isEqualTo(Reviews.SEED);
    }

    @Test
    void steamReturnsReviews_usesSteam() {
        ReviewSource steam = appId -> Reviews.steam(List.of("real review"));
        Reviews got = new FallbackReviewSource(steam, seed).fetch(PORTAL_2);
        assertThat(got.texts()).containsExactly("real review");
        assertThat(got.source()).isEqualTo(Reviews.STEAM);
    }

    @Test
    void seed_hasReviewsForEverySeedGame() throws Exception {
        List<Map<String, Object>> games;
        try (InputStream in = new ClassPathResource("seed/games.json").getInputStream()) {
            games = json.readValue(in, new TypeReference<>() { });
        }
        assertThat(games).isNotEmpty().allSatisfy(g ->
                assertThat(seed.fetch(((Number) g.get("steamAppId")).longValue()).texts()).isNotEmpty());
    }
}
