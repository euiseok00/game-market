package com.gamemarket.summary;

import java.util.List;

public record SummaryResult(List<String> pros, List<String> cons, String verdict) {
}
