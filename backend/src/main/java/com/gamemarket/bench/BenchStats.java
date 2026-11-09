package com.gamemarket.bench;

import java.util.List;

/** Average and nearest-rank p95 of response times in milliseconds. */
public record BenchStats(int count, double avg, long p95) {

    public static BenchStats of(List<Long> millis) {
        if (millis.isEmpty()) {
            return new BenchStats(0, 0, 0);
        }
        List<Long> sorted = millis.stream().sorted().toList();
        double avg = sorted.stream().mapToLong(Long::longValue).average().orElse(0);
        int rank = (int) Math.ceil(0.95 * sorted.size());
        return new BenchStats(sorted.size(), avg, sorted.get(rank - 1));
    }
}
