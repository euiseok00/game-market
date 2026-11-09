package com.gamemarket.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gamemarket.IntegrationTestBase;
import com.gamemarket.game.Game;
import com.gamemarket.game.GameRepository;
import com.gamemarket.review.ReviewSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class SummaryServiceTest {

    static final SummaryResult RESULT = new SummaryResult(List.of("재미있다"), List.of("버그"), "추천");

    abstract static class Base extends IntegrationTestBase {

        @MockitoBean
        Summarizer summarizer;

        @MockitoBean
        ReviewSource reviews;

        @Autowired
        SummaryService service;

        @Autowired
        SummaryRepository summaries;

        @Autowired
        SummaryLogRepository logs;

        @Autowired
        GameRepository games;

        @BeforeEach
        void stubs() {
            when(reviews.fetch(anyLong())).thenReturn(List.of("good game", "bad netcode"));
            when(summarizer.summarize(anyString(), anyList())).thenReturn(RESULT);
        }

        Game anyGame() {
            return games.findAll().get(0);
        }
    }

    @Nested
    class Modes extends Base {

        @Test
        void none_callsSummarizerEveryTime() {
            Game g = anyGame();
            service.get(g.getId(), SummaryMode.NONE);
            service.get(g.getId(), SummaryMode.NONE);
            verify(summarizer, times(2)).summarize(anyString(), anyList());
            verify(reviews, times(2)).fetch(g.getSteamAppId());
        }

        @Test
        void lazy_secondCall_doesNotCallSummarizerOrReviewSource() {
            Game g = anyGame();
            summaries.deleteAll();
            SummaryDto first = service.get(g.getId(), SummaryMode.LAZY);
            SummaryDto second = service.get(g.getId(), SummaryMode.LAZY);
            verify(summarizer, times(1)).summarize(anyString(), anyList());
            verify(reviews, times(1)).fetch(anyLong());
            assertThat(second.pros()).isEqualTo(first.pros()).containsExactly("재미있다");
            assertThat(summaries.count()).isEqualTo(1);
        }

        @Test
        void everyCall_writesSummaryLog() {
            Game g = anyGame();
            long before = logs.count();
            service.get(g.getId(), SummaryMode.NONE);
            service.get(g.getId(), SummaryMode.LAZY);
            service.get(g.getId(), SummaryMode.LAZY);
            List<SummaryLog> all = logs.findAll();
            assertThat(all).hasSize((int) before + 3);
            assertThat(all.subList((int) before, all.size()))
                    .extracting(SummaryLog::getMode)
                    .containsExactly("none", "lazy", "lazy");
        }
    }

    @Nested
    @TestPropertySource(properties = "summary.mode=warm")
    @Import(Warm.Stubs.class)
    class Warm extends IntegrationTestBase {

        static final AtomicInteger CALLS = new AtomicInteger();

        @TestConfiguration
        static class Stubs {

            // Plain beans, not @MockitoBean: the warmup runs during context startup, before any @BeforeEach stubbing.
            @Bean
            @Primary
            Summarizer stubSummarizer() {
                return (name, reviews) -> {
                    CALLS.incrementAndGet();
                    return RESULT;
                };
            }
        }

        @MockitoBean
        ReviewSource reviews; // unstubbed mock returns an empty list; the stub summarizer ignores it

        @Autowired
        SummaryService service;

        @Autowired
        SummaryRepository summaries;

        @Autowired
        GameRepository games;

        @Test
        void warm_precomputesAllGamesOnStartup() {
            assertThat(summaries.findAll())
                    .extracting(s -> s.getGame().getId())
                    .containsAll(games.findAll().stream().map(Game::getId).toList());
            int afterStartup = CALLS.get();
            SummaryDto dto = service.get(games.findAll().get(0).getId());
            assertThat(dto.verdict()).isEqualTo("추천");
            assertThat(CALLS.get()).isEqualTo(afterStartup);
        }
    }
}
