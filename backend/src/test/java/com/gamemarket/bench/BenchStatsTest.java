package com.gamemarket.bench;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class BenchStatsTest {

    @Test
    void p95_of100Values() {
        List<Long> values = new ArrayList<>(LongStream.rangeClosed(1, 100).boxed().toList());
        Collections.shuffle(values);
        assertThat(BenchStats.of(values).p95()).isEqualTo(95);
    }

    @Test
    void avg_ofKnownValues() {
        BenchStats stats = BenchStats.of(List.of(10L, 20L, 30L, 60L));
        assertThat(stats.avg()).isEqualTo(30.0);
        assertThat(stats.count()).isEqualTo(4);
    }

    @Test
    void p95_ofSingleValue() {
        assertThat(BenchStats.of(List.of(7L)).p95()).isEqualTo(7);
    }
}
