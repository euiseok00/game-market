package com.gamemarket.summary;

import java.time.Instant;
import java.util.List;

public record SummaryDto(List<String> pros, List<String> cons, String verdict, Instant createdAt) {
}
