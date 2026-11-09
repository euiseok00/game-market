package com.gamemarket.summary;

import java.util.List;

public interface Summarizer {

    SummaryResult summarize(String gameName, List<String> reviews);
}
