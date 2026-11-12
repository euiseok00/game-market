package com.gamemarket.summary;

import com.gamemarket.game.Game;
import com.gamemarket.game.GameRepository;
import com.gamemarket.review.ReviewSource;
import com.gamemarket.review.Reviews;
import com.gamemarket.summary.dto.SummaryDto;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Serves stored summaries, generates missing ones and refreshes ones whose reviews changed. */
@Service
public class SummaryService {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() { };

    private final GameRepository games;
    private final ReviewSource reviews;
    private final Summarizer summarizer;
    private final SummaryRepository summaries;
    private final SummaryLogRepository logs;
    private final JsonMapper json;
    private final long waitTimeoutMs;
    private final ConcurrentHashMap<Long, CompletableFuture<SummaryDto>> inFlight = new ConcurrentHashMap<>();

    public SummaryService(GameRepository games, ReviewSource reviews, Summarizer summarizer,
            SummaryRepository summaries, SummaryLogRepository logs, JsonMapper json,
            @Value("${summary.wait-timeout-ms:60000}") long waitTimeoutMs) {
        this.games = games;
        this.reviews = reviews;
        this.summarizer = summarizer;
        this.summaries = summaries;
        this.logs = logs;
        this.json = json;
        this.waitTimeoutMs = waitTimeoutMs;
    }

    /** Stored summary from the DB; generated and stored on the first request for a game. */
    public SummaryDto get(Long gameId) {
        return logged(gameId, "cache", this::storedOrGenerate);
    }

    /** Reads reviews and calls the LLM on every call, storing nothing; the benchmark's no-cache baseline. */
    public SummaryDto getUncached(Long gameId) {
        return logged(gameId, "nocache", (game, timing) -> toDto(generate(game, fetch(gameId, timing), timing),
                Instant.now()));
    }

    private SummaryDto logged(Long gameId, String label, BiFunction<Game, Timing, SummaryDto> body) {
        long start = System.nanoTime();
        Game game = games.findById(gameId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Timing timing = new Timing();
        SummaryDto dto = body.apply(game, timing);
        logs.save(new SummaryLog(gameId, label, timing.fetchMs, timing.llmMs, millisSince(start)));
        return dto;
    }

    /**
     * Regenerates a stored summary when its reviews changed (new review id or edited text). Readers keep getting
     * the old row until the new summary is generated; a failed generation throws and leaves the old row as is.
     *
     * @return true if the stored summary was replaced
     */
    public boolean refreshIfChanged(Long gameId) {
        if (summaries.findByGameId(gameId).isEmpty()) {
            return false; // never requested yet; the first request generates it
        }
        CompletableFuture<SummaryDto> mine = new CompletableFuture<>();
        if (inFlight.putIfAbsent(gameId, mine) != null) {
            return false; // already being generated for this game
        }
        try {
            // Re-read after taking the slot so a refresh that just finished is not repeated.
            ReviewSummary stored = summaries.findByGameId(gameId)
                    .orElseThrow(() -> new SummaryUnavailableException("summary of game " + gameId + " was deleted"));
            Timing timing = new Timing();
            Reviews current = fetch(gameId, timing);
            String fingerprint = current.fingerprint();
            if (fingerprint.equals(stored.getReviewFingerprint())) {
                mine.complete(toDto(stored));
                return false;
            }
            SummaryResult result = generate(stored.getGame(), current, timing).result();
            stored.replace(json.writeValueAsString(result.pros()), json.writeValueAsString(result.cons()),
                    result.verdict(), Instant.now(), current.source(), current.items().size(), fingerprint);
            mine.complete(toDto(summaries.saveAndFlush(stored)));
            return true;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(gameId, mine);
        }
    }

    /**
     * Single-flight per game: concurrent callers for the same game share one generation. The unique game_id
     * constraint is the last line of defense (e.g. a second app instance).
     */
    private SummaryDto storedOrGenerate(Game game, Timing timing) {
        Long gameId = game.getId();
        Optional<ReviewSummary> stored = summaries.findByGameId(gameId);
        if (stored.isPresent()) {
            return toDto(stored.get());
        }
        CompletableFuture<SummaryDto> mine = new CompletableFuture<>();
        CompletableFuture<SummaryDto> running = inFlight.putIfAbsent(gameId, mine);
        if (running != null) {
            return await(running);
        }
        try {
            // Re-check: a flight may have finished between our lookup and putIfAbsent.
            SummaryDto dto = summaries.findByGameId(gameId).map(this::toDto)
                    .orElseGet(() -> generateAndStore(game, timing));
            mine.complete(dto);
            return dto;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(gameId, mine);
        }
    }

    private SummaryDto await(CompletableFuture<SummaryDto> running) {
        try {
            return running.get(waitTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new SummaryUnavailableException("summary still being generated", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SummaryUnavailableException("interrupted while waiting for summary", e);
        }
    }

    private SummaryDto generateAndStore(Game game, Timing timing) {
        Reviews fetched = fetch(game.getId(), timing);
        Generated generated = generate(game, fetched, timing);
        SummaryResult result = generated.result();
        try {
            ReviewSummary saved = summaries.saveAndFlush(new ReviewSummary(game,
                    json.writeValueAsString(result.pros()), json.writeValueAsString(result.cons()),
                    result.verdict(), Instant.now(), fetched.source(), fetched.items().size(), fetched.fingerprint()));
            return toDto(saved);
        } catch (DataIntegrityViolationException duplicate) {
            return summaries.findByGameId(game.getId()).map(this::toDto).orElseThrow(() -> duplicate);
        }
    }

    private record Generated(SummaryResult result, Reviews reviews) {
    }

    private Reviews fetch(Long gameId, Timing timing) {
        long t0 = System.nanoTime();
        Reviews fetched = reviews.fetch(gameId);
        timing.fetchMs = millisSince(t0);
        return fetched;
    }

    private Generated generate(Game game, Reviews fetched, Timing timing) {
        if (fetched.texts().isEmpty()) {
            throw new SummaryUnavailableException("no reviews for game " + game.getId());
        }
        long t1 = System.nanoTime();
        SummaryResult result = summarizer.summarize(game.getName(), fetched.texts());
        timing.llmMs = millisSince(t1);
        return new Generated(result, fetched);
    }

    private SummaryDto toDto(ReviewSummary s) {
        return new SummaryDto(json.readValue(s.getProsJson(), STRINGS), json.readValue(s.getConsJson(), STRINGS),
                s.getVerdict(), s.getCreatedAt(), s.getReviewSource(), s.getReviewCount());
    }

    private static SummaryDto toDto(Generated g, Instant createdAt) {
        SummaryResult r = g.result();
        return new SummaryDto(r.pros(), r.cons(), r.verdict(), createdAt, g.reviews().source(),
                g.reviews().texts().size());
    }

    private static long millisSince(long nanoStart) {
        return (System.nanoTime() - nanoStart) / 1_000_000;
    }

    private static final class Timing {
        long fetchMs;
        long llmMs;
    }
}
