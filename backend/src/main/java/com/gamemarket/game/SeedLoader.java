package com.gamemarket.game;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
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
    private final JsonMapper json;

    public SeedLoader(GameRepository games, JsonMapper json) {
        this.games = games;
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
    }
}
