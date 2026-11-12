package com.gamemarket.game;

import com.gamemarket.review.Review;
import com.gamemarket.review.ReviewRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
@Order(0)
public class SeedLoader implements ApplicationRunner {

    record SeedGame(long steamAppId, String name, String genre, int priceKrw, String imageUrl, String description) {
    }

    private final GameRepository games;
    private final ReviewRepository reviews;
    private final JsonMapper json;

    public SeedLoader(GameRepository games, ReviewRepository reviews, JsonMapper json) {
        this.games = games;
        this.reviews = reviews;
        this.json = json;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        try (InputStream in = new ClassPathResource("seed/games.json").getInputStream()) {
            List<SeedGame> seed = json.readValue(in, new TypeReference<>() { });
            seed.stream()
                    .filter(s -> !games.existsBySteamAppId(s.steamAppId()))
                    .map(s -> new Game(s.steamAppId(), s.name(), s.genre(), s.priceKrw(), s.imageUrl(), s.description()))
                    .forEach(games::save);
        }
        // Seed reviews go into the DB once per game; after that the DB rows are the source of truth.
        try (InputStream in = new ClassPathResource("seed/reviews.json").getInputStream()) {
            Map<String, List<String>> seed = json.readValue(in, new TypeReference<>() { });
            for (Game g : games.findAll()) {
                if (!reviews.existsByGameId(g.getId())) {
                    reviews.saveAll(seed.getOrDefault(String.valueOf(g.getSteamAppId()), List.of()).stream()
                            .map(text -> new Review(g.getId(), text))
                            .toList());
                }
            }
        }
    }
}
