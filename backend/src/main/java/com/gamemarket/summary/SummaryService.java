package com.gamemarket.summary;

import com.gamemarket.game.Game;
import com.gamemarket.game.GameRepository;
import com.gamemarket.review.ReviewSource;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** The only place that branches on summary.mode. */
@Service
public class SummaryService {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() { };

    private final GameRepository games;
    private final ReviewSource reviews;
    private final Summarizer summarizer;
    private final SummaryRepository summaries;
    private final SummaryLogRepository logs;
    private final JsonMapper json;
    private final SummaryMode mode;

    public SummaryService(GameRepository games, ReviewSource reviews, Summarizer summarizer,
            SummaryRepository summaries, SummaryLogRepository logs, JsonMapper json,
            @Value("${summary.mode:lazy}") SummaryMode mode) {
        this.games = games;
        this.reviews = reviews;
        this.summarizer = summarizer;
        this.summaries = summaries;
        this.logs = logs;
        this.json = json;
        this.mode = mode;
    }

    public SummaryMode mode() {
        return mode;
    }

    public SummaryDto get(Long gameId) {
        return get(gameId, mode);
    }

    /** Explicit mode so the benchmark can compare all modes in one run. */
    public SummaryDto get(Long gameId, SummaryMode requestMode) {
        long start = System.nanoTime();
        Game game = games.findById(gameId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Timing timing = new Timing();
        SummaryDto dto = requestMode == SummaryMode.NONE
                ? toDto(generate(game, timing), Instant.now())
                : summaries.findByGameId(gameId).map(this::toDto).orElseGet(() -> generateAndStore(game, timing));
        logs.save(new SummaryLog(gameId, requestMode.label(), timing.fetchMs, timing.llmMs, millisSince(start)));
        return dto;
    }

    /** Precompute without logging a request; used by the warm-up at startup. */
    public void ensureStored(Long gameId) {
        if (summaries.findByGameId(gameId).isEmpty()) {
            generateAndStore(games.findById(gameId).orElseThrow(), new Timing());
        }
    }

    private SummaryDto generateAndStore(Game game, Timing timing) {
        SummaryResult result = generate(game, timing);
        ReviewSummary saved = summaries.saveAndFlush(new ReviewSummary(game, json.writeValueAsString(result.pros()),
                json.writeValueAsString(result.cons()), result.verdict(), Instant.now()));
        return toDto(saved);
    }

    private SummaryResult generate(Game game, Timing timing) {
        long t0 = System.nanoTime();
        List<String> texts = reviews.fetch(game.getSteamAppId());
        timing.fetchMs = millisSince(t0);
        long t1 = System.nanoTime();
        SummaryResult result = summarizer.summarize(game.getName(), texts);
        timing.llmMs = millisSince(t1);
        return result;
    }

    private SummaryDto toDto(ReviewSummary s) {
        return new SummaryDto(json.readValue(s.getProsJson(), STRINGS), json.readValue(s.getConsJson(), STRINGS),
                s.getVerdict(), s.getCreatedAt());
    }

    private static SummaryDto toDto(SummaryResult r, Instant createdAt) {
        return new SummaryDto(r.pros(), r.cons(), r.verdict(), createdAt);
    }

    private static long millisSince(long nanoStart) {
        return (System.nanoTime() - nanoStart) / 1_000_000;
    }

    private static final class Timing {
        long fetchMs;
        long llmMs;
    }
}
