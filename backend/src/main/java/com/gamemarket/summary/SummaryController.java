package com.gamemarket.summary;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import com.gamemarket.summary.dto.SummaryDto;

@RestController
public class SummaryController {

    private final SummaryService summaries;

    public SummaryController(SummaryService summaries) {
        this.summaries = summaries;
    }

    @GetMapping("/api/games/{id}/summary")
    public SummaryDto get(@PathVariable Long id) {
        return summaries.get(id);
    }
}
