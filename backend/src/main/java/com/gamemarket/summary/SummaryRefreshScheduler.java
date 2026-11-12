package com.gamemarket.summary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every 6 hours (KST 00/06/12/18 by default), regenerates the stored summaries whose reviews changed. */
@Component
public class SummaryRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(SummaryRefreshScheduler.class);

    private final SummaryRepository summaries;
    private final SummaryService service;

    public SummaryRefreshScheduler(SummaryRepository summaries, SummaryService service) {
        this.summaries = summaries;
        this.service = service;
    }

    @Scheduled(cron = "${summary.refresh-cron}", zone = "Asia/Seoul")
    public void refreshChanged() {
        int refreshed = 0;
        for (ReviewSummary s : summaries.findAll()) {
            Long gameId = s.getGame().getId();
            try {
                if (service.refreshIfChanged(gameId)) {
                    refreshed++;
                }
            } catch (RuntimeException e) {
                // The old summary stays in place; the next run tries again.
                log.warn("summary refresh failed for game {}: {}", gameId, e.getMessage());
            }
        }
        log.info("summary refresh: {} of {} summaries regenerated", refreshed, summaries.count());
    }
}
