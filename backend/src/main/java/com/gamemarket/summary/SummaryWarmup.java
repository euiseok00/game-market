package com.gamemarket.summary;

import com.gamemarket.game.Game;
import com.gamemarket.game.GameRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** summary.mode=warm: precompute every seed game's summary at startup (after SeedLoader). */
@Component
@Order(1)
@ConditionalOnProperty(name = "summary.mode", havingValue = "warm")
public class SummaryWarmup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SummaryWarmup.class);

    private final GameRepository games;
    private final SummaryService summaries;

    public SummaryWarmup(GameRepository games, SummaryService summaries) {
        this.games = games;
        this.summaries = summaries;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Game g : games.findAll()) {
            try {
                summaries.ensureStored(g.getId());
            } catch (RuntimeException e) {
                // One failing game must not stop startup; it falls back to lazy generation on request.
                log.warn("warm-up failed for game {}: {}", g.getId(), e.getMessage());
            }
        }
    }
}
