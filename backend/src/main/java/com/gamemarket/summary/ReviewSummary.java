package com.gamemarket.summary;

import com.gamemarket.game.Game;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "review_summary")
public class ReviewSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // unique game_id: last line of defense against duplicate summaries.
    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "game_id", unique = true)
    private Game game;

    @Column(nullable = false, columnDefinition = "text")
    private String prosJson;

    @Column(nullable = false, columnDefinition = "text")
    private String consJson;

    @Column(nullable = false, columnDefinition = "text")
    private String verdict;

    // Time of the latest generation; a refresh moves it forward.
    @Column(nullable = false)
    private Instant createdAt;

    // Nullable: ddl-auto=update adds these columns to rows created before they existed.
    @Column(length = 10)
    private String reviewSource;

    private Integer reviewCount;

    // Reviews.fingerprint() of the reviews this summary was built from; null on rows from before this column.
    @Column(length = 64)
    private String reviewFingerprint;

    protected ReviewSummary() {
    }

    public ReviewSummary(Game game, String prosJson, String consJson, String verdict, Instant createdAt,
            String reviewSource, Integer reviewCount, String reviewFingerprint) {
        this.game = game;
        replace(prosJson, consJson, verdict, createdAt, reviewSource, reviewCount, reviewFingerprint);
    }

    /** Swaps in a newly generated summary; called only after generation succeeded. */
    public void replace(String prosJson, String consJson, String verdict, Instant createdAt, String reviewSource,
            Integer reviewCount, String reviewFingerprint) {
        this.prosJson = prosJson;
        this.consJson = consJson;
        this.verdict = verdict;
        this.createdAt = createdAt;
        this.reviewSource = reviewSource;
        this.reviewCount = reviewCount;
        this.reviewFingerprint = reviewFingerprint;
    }

    public Long getId() { return id; }
    public Game getGame() { return game; }
    public String getProsJson() { return prosJson; }
    public String getConsJson() { return consJson; }
    public String getVerdict() { return verdict; }
    public Instant getCreatedAt() { return createdAt; }
    public String getReviewSource() { return reviewSource; }
    public Integer getReviewCount() { return reviewCount; }
    public String getReviewFingerprint() { return reviewFingerprint; }
}
