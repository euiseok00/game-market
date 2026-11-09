package com.gamemarket.summary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "summary_log")
public class SummaryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(nullable = false, length = 10)
    private String mode;

    private long fetchMs;
    private long llmMs;
    private long totalMs;

    @Column(nullable = false)
    private Instant createdAt;

    protected SummaryLog() {
    }

    public SummaryLog(Long gameId, String mode, long fetchMs, long llmMs, long totalMs) {
        this.gameId = gameId;
        this.mode = mode;
        this.fetchMs = fetchMs;
        this.llmMs = llmMs;
        this.totalMs = totalMs;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getGameId() { return gameId; }
    public String getMode() { return mode; }
    public long getFetchMs() { return fetchMs; }
    public long getLlmMs() { return llmMs; }
    public long getTotalMs() { return totalMs; }
    public Instant getCreatedAt() { return createdAt; }
}
