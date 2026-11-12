package com.gamemarket.bench;

import com.gamemarket.game.Game;
import com.gamemarket.game.GameRepository;
import com.gamemarket.summary.SummaryLog;
import com.gamemarket.summary.SummaryLogRepository;
import com.gamemarket.summary.SummaryRepository;
import com.gamemarket.summary.SummaryService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Profile "bench": calls SummaryService for every seed game without and with the DB cache, aggregates summary_log and
 * writes docs/benchmark.md, then exits.
 */
@Component
@Profile("bench")
@Order(10)
public class BenchRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BenchRunner.class);

    private final GameRepository games;
    private final SummaryService service;
    private final SummaryRepository summaries;
    private final SummaryLogRepository logs;
    private final ConfigurableApplicationContext context;
    private final int repeats;
    private final long llmIntervalMs;
    private final Path output;
    private final String model;
    private int failures;

    public BenchRunner(GameRepository games, SummaryService service, SummaryRepository summaries,
            SummaryLogRepository logs, ConfigurableApplicationContext context,
            @Value("${bench.repeats:10}") int repeats,
            @Value("${bench.llm-interval-ms:4500}") long llmIntervalMs,
            @Value("${bench.output:../docs/benchmark.md}") String output,
            @Value("${spring.ai.google.genai.chat.model:}") String model) {
        this.games = games;
        this.service = service;
        this.summaries = summaries;
        this.logs = logs;
        this.context = context;
        this.repeats = repeats;
        this.llmIntervalMs = llmIntervalMs;
        this.output = Path.of(output);
        this.model = model;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        List<Game> all = games.findAll();
        Instant since = Instant.now();

        // A: no cache, every call reads reviews and calls the LLM.
        for (Game g : all) {
            for (int i = 0; i < repeats; i++) {
                call(g, false, true);
            }
        }
        // B: DB cache. The first call per game fills the cache and is left out of the table; the rest read the DB.
        summaries.deleteAll();
        for (Game g : all) {
            call(g, true, true);
            for (int i = 0; i < repeats; i++) {
                call(g, true, false);
            }
        }

        write(logs.findByCreatedAtGreaterThanEqualOrderById(since), all.size());
        log.info("benchmark written to {}", output.toAbsolutePath());
        System.exit(SpringApplication.exit(context, () -> 0));
    }

    private void call(Game g, boolean cached, boolean hitsLlm) {
        try {
            if (cached) {
                service.get(g.getId());
            } else {
                service.getUncached(g.getId());
            }
        } catch (RuntimeException e) {
            failures++;
            log.warn("{} call failed for {}: {} / cause: {}", cached ? "cache" : "nocache", g.getId(), e.getMessage(),
                    e.getCause() == null ? "-" : e.getCause().toString());
        }
        if (hitsLlm) {
            pace();
        }
    }

    /** Stay under the free-tier requests-per-minute limit; the pause is outside the measured window. */
    private void pace() {
        try {
            Thread.sleep(llmIntervalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void write(List<SummaryLog> rows, int gameCount) throws IOException {
        Set<Long> filled = new HashSet<>();
        List<SummaryLog> cached = new ArrayList<>();
        for (SummaryLog r : rows) {
            if (r.getMode().equals("cache") && !filled.add(r.getGameId())) {
                cached.add(r); // skips each game's first, cache-filling call
            }
        }
        StringBuilder md = new StringBuilder("# 요약 응답 시간 벤치마크\n\n");
        md.append("- 실행: ").append(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
                .format(Instant.now())).append('\n');
        md.append("- 모델: `").append(model).append("`, 게임 ").append(gameCount).append("개, 게임당 반복 ")
                .append(repeats).append("회\n");
        md.append("- 리뷰 소스: DB `review` 테이블 (시드 리뷰)\n");
        md.append("- 실패한 호출: ").append(failures).append("건 (실패 호출은 summary_log에 기록되지 않음)\n");
        md.append("- 측정값: `summary_log.total_ms` (서비스 내부, HTTP 제외)\n");
        md.append("- 캐시 도입 후: 게임마다 요약을 한 번 만들어 DB에 저장해 둔 뒤의 요청만 집계 (저장용 첫 호출 제외)\n\n");
        md.append("| 단계 | 방식 | 호출 수 | 평균 (ms) | p95 (ms) | 평균 리뷰 수집 (ms) | 평균 LLM (ms) |\n");
        md.append("|---|---|---:|---:|---:|---:|---:|\n");
        row(md, "A", "캐시 도입 전: 요청마다 Gemini 호출", filter(rows, r -> r.getMode().equals("nocache")));
        row(md, "B", "캐시 도입 후: DB에 저장된 요약 조회", cached);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, md.toString());
    }

    private static List<SummaryLog> filter(List<SummaryLog> rows, Predicate<SummaryLog> p) {
        return rows.stream().filter(p).toList();
    }

    private static void row(StringBuilder md, String stage, String label, List<SummaryLog> rows) {
        BenchStats total = BenchStats.of(rows.stream().map(SummaryLog::getTotalMs).toList());
        double fetch = rows.stream().mapToLong(SummaryLog::getFetchMs).average().orElse(0);
        double llm = rows.stream().mapToLong(SummaryLog::getLlmMs).average().orElse(0);
        md.append(String.format(Locale.ROOT, "| %s | %s | %d | %.1f | %d | %.1f | %.1f |%n",
                stage, label, total.count(), total.avg(), total.p95(), fetch, llm));
    }
}
