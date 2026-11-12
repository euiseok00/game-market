package com.gamemarket.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gamemarket.IntegrationTestBase;
import com.gamemarket.game.Game;
import com.gamemarket.game.GameRepository;
import com.gamemarket.review.ReviewSource;
import com.gamemarket.review.Reviews;
import com.gamemarket.review.Reviews.Item;
import com.gamemarket.summary.dto.SummaryDto;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class SummaryServiceTest extends IntegrationTestBase {

    static final SummaryResult RESULT = new SummaryResult(List.of("재미있다"), List.of("버그"), "추천");
    static final SummaryResult NEW_RESULT = new SummaryResult(List.of("새 장점"), List.of("새 단점"), "새 총평");
    static final List<Item> TWO = List.of(new Item(1, "good game"), new Item(2, "bad netcode"));

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

    Game g;

    @BeforeEach
    void stubs() {
        when(reviews.fetch(anyLong())).thenReturn(Reviews.seed(TWO));
        when(summarizer.summarize(anyString(), anyList())).thenReturn(RESULT);
        summaries.deleteAll();
        g = games.findAll().get(0);
    }

    @Test
    void uncached_callsSummarizerEveryTime_andStoresNothing() {
        service.getUncached(g.getId());
        service.getUncached(g.getId());
        verify(summarizer, times(2)).summarize(anyString(), anyList());
        verify(reviews, times(2)).fetch(g.getId());
        assertThat(summaries.count()).isZero();
    }

    @Test
    void cached_secondCall_doesNotCallSummarizerOrReviewSource() {
        SummaryDto first = service.get(g.getId());
        SummaryDto second = service.get(g.getId());
        verify(summarizer, times(1)).summarize(anyString(), anyList());
        verify(reviews, times(1)).fetch(anyLong());
        assertThat(second.pros()).isEqualTo(first.pros()).containsExactly("재미있다");
        assertThat(summaries.count()).isEqualTo(1);
    }

    @Test
    void summary_reportsReviewSourceAndCount_alsoWhenReadFromDb() {
        SummaryDto generated = service.get(g.getId());
        SummaryDto cached = service.get(g.getId());
        assertThat(generated.reviewSource()).isEqualTo("seed");
        assertThat(generated.reviewCount()).isEqualTo(2);
        assertThat(cached.reviewSource()).isEqualTo("seed");
        assertThat(cached.reviewCount()).isEqualTo(2);
        assertThat(service.getUncached(g.getId()).reviewCount()).isEqualTo(2);
    }

    @Test
    void everyCall_writesSummaryLog() {
        long lastIdBefore = logs.findAll().stream().mapToLong(SummaryLog::getId).max().orElse(0);
        service.getUncached(g.getId());
        service.get(g.getId());
        service.get(g.getId());
        // findAll() has no guaranteed order; compare only the rows this test wrote, by id.
        assertThat(logs.findAll(Sort.by("id")))
                .filteredOn(l -> l.getId() > lastIdBefore)
                .extracting(SummaryLog::getMode)
                .containsExactly("nocache", "cache", "cache");
    }

    @Test
    void refresh_unchangedReviews_doesNotCallSummarizer() {
        service.get(g.getId());
        assertThat(service.refreshIfChanged(g.getId())).isFalse();
        verify(summarizer, times(1)).summarize(anyString(), anyList());
    }

    @Test
    void refresh_newReviewId_regeneratesAndReplaces() {
        service.get(g.getId());
        when(reviews.fetch(anyLong())).thenReturn(Reviews.seed(List.of(TWO.get(0), TWO.get(1), new Item(3, "new"))));
        when(summarizer.summarize(anyString(), anyList())).thenReturn(NEW_RESULT);

        assertThat(service.refreshIfChanged(g.getId())).isTrue();
        SummaryDto now = service.get(g.getId());
        assertThat(now.verdict()).isEqualTo("새 총평");
        assertThat(now.reviewCount()).isEqualTo(3);
        assertThat(summaries.count()).isEqualTo(1);
    }

    @Test
    void refresh_editedReviewText_sameId_regenerates() {
        service.get(g.getId());
        when(reviews.fetch(anyLong())).thenReturn(Reviews.seed(List.of(TWO.get(0), new Item(2, "netcode fixed"))));
        when(summarizer.summarize(anyString(), anyList())).thenReturn(NEW_RESULT);

        assertThat(service.refreshIfChanged(g.getId())).isTrue();
        assertThat(service.get(g.getId()).verdict()).isEqualTo("새 총평");
    }

    @Test
    void refresh_generationFails_keepsOldSummary_andRetriesNextTime() {
        service.get(g.getId());
        String oldFingerprint = summaries.findByGameId(g.getId()).orElseThrow().getReviewFingerprint();
        when(reviews.fetch(anyLong())).thenReturn(Reviews.seed(List.of(new Item(9, "changed"))));
        when(summarizer.summarize(anyString(), anyList())).thenThrow(new SummaryUnavailableException("LLM down"));

        assertThatThrownBy(() -> service.refreshIfChanged(g.getId())).isInstanceOf(SummaryUnavailableException.class);
        ReviewSummary kept = summaries.findByGameId(g.getId()).orElseThrow();
        assertThat(kept.getVerdict()).isEqualTo("추천");
        assertThat(kept.getReviewFingerprint()).isEqualTo(oldFingerprint);
    }

    @Test
    void refresh_neverRequestedGame_doesNothing() {
        assertThat(service.refreshIfChanged(g.getId())).isFalse();
        verify(summarizer, never()).summarize(anyString(), anyList());
        assertThat(summaries.count()).isZero();
    }

    @Test
    void duringRefresh_readersGetOldSummaryAtOnce_andSecondRefreshIsSkipped() throws Exception {
        service.get(g.getId());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(reviews.fetch(anyLong())).thenReturn(Reviews.seed(List.of(new Item(9, "changed"))));
        when(summarizer.summarize(anyString(), anyList())).thenAnswer(inv -> {
            entered.countDown();
            release.await(10, TimeUnit.SECONDS);
            return NEW_RESULT;
        });

        CompletableFuture<Boolean> refresh = CompletableFuture.supplyAsync(() -> service.refreshIfChanged(g.getId()));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        long start = System.nanoTime();
        assertThat(service.get(g.getId()).verdict()).isEqualTo("추천");
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1_000);
        assertThat(service.refreshIfChanged(g.getId())).isFalse();

        release.countDown();
        assertThat(refresh.get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(service.get(g.getId()).verdict()).isEqualTo("새 총평");
        verify(summarizer, times(2)).summarize(anyString(), anyList()); // initial + one refresh
    }

    @Test
    void fingerprint_changesOnNewIdOrEditedText_notOnOrder() {
        String base = Reviews.seed(TWO).fingerprint();
        assertThat(Reviews.seed(List.of(TWO.get(1), TWO.get(0))).fingerprint()).isEqualTo(base);
        assertThat(Reviews.seed(List.of(TWO.get(0), new Item(2, "bad netcode!"))).fingerprint()).isNotEqualTo(base);
        assertThat(Reviews.seed(List.of(TWO.get(0), new Item(3, "bad netcode"))).fingerprint()).isNotEqualTo(base);
    }
}
