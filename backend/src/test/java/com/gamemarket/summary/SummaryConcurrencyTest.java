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
import com.gamemarket.review.Reviews;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class SummaryConcurrencyTest extends IntegrationTestBase {

    @MockitoBean
    Summarizer summarizer;

    @MockitoBean
    ReviewSource reviews;

    @Autowired
    SummaryService service;

    @Autowired
    SummaryRepository summaries;

    @Autowired
    GameRepository games;

    @Test
    void concurrent20Requests_callSummarizerOnce_andStoreOneRow() throws Exception {
        Game game = games.findAll().get(1);
        summaries.findByGameId(game.getId()).ifPresent(summaries::delete);
        when(reviews.fetch(anyLong())).thenReturn(Reviews.seed(List.of("fun")));
        when(summarizer.summarize(anyString(), anyList())).thenAnswer(inv -> {
            Thread.sleep(300); // keep the first call in flight while the others arrive
            return new SummaryResult(List.of("장점"), List.of("단점"), "총평");
        });

        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<SummaryDto>> results = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return service.get(game.getId(), SummaryMode.LAZY);
            }));
        }
        start.countDown();
        for (Future<SummaryDto> f : results) {
            assertThat(f.get().verdict()).isEqualTo("총평");
        }
        pool.shutdown();

        verify(summarizer, times(1)).summarize(anyString(), anyList());
        assertThat(summaries.findAll()).filteredOn(s -> s.getGame().getId().equals(game.getId())).hasSize(1);
    }
}
