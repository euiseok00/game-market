package com.gamemarket.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.gamemarket.IntegrationTestBase;
import com.gamemarket.game.Game;
import com.gamemarket.game.GameRepository;
import com.gamemarket.review.ReviewSource;
import com.gamemarket.review.Reviews;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.gamemarket.summary.dto.SummaryDto;

@TestPropertySource(properties = "summary.wait-timeout-ms=200")
class SummaryWaitTimeoutTest extends IntegrationTestBase {

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
    void waiter_givesUpAfterWaitTimeout_whileFirstCallIsStuck() throws Exception {
        Game game = games.findAll().get(3);
        summaries.findByGameId(game.getId()).ifPresent(summaries::delete);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(reviews.fetch(anyLong())).thenReturn(Reviews.seed(List.of("fun")));
        when(summarizer.summarize(anyString(), anyList())).thenAnswer(inv -> {
            entered.countDown();
            release.await(10, TimeUnit.SECONDS);
            return new SummaryResult(List.of("장점"), List.of("단점"), "총평");
        });

        CompletableFuture<SummaryDto> first = CompletableFuture.supplyAsync(
                () -> service.get(game.getId(), SummaryMode.LAZY));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        long start = System.nanoTime();
        assertThatThrownBy(() -> service.get(game.getId(), SummaryMode.LAZY))
                .isInstanceOf(SummaryUnavailableException.class);
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2_000);

        release.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS).verdict()).isEqualTo("총평");
    }
}
