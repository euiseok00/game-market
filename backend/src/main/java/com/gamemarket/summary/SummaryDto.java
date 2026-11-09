package com.gamemarket.summary;

import java.time.Instant;
import java.util.List;

/** reviewSource is "steam" or "seed"; null for summaries stored before the column existed. */
public record SummaryDto(List<String> pros, List<String> cons, String verdict, Instant createdAt,
        String reviewSource, Integer reviewCount) {
}
