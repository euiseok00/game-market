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

    @Column(nullable = false)
    private Instant createdAt;

    // Nullable: ddl-auto=update adds these columns to rows created before they existed.
    @Column(length = 10)
    private String reviewSource;

    private Integer reviewCount;

    protected ReviewSummary() {
    }

    public ReviewSummary(Game game, String prosJson, String consJson, String verdict, Instant createdAt,
            String reviewSource, Integer reviewCount) {
        this.game = game;
        this.prosJson = prosJson;
        this.consJson = consJson;
        this.verdict = verdict;
        this.createdAt = createdAt;
        this.reviewSource = reviewSource;
        this.reviewCount = reviewCount;
    }

    public Long getId() { return id; }
    public Game getGame() { return game; }
    public String getProsJson() { return prosJson; }
    public String getConsJson() { return consJson; }
    public String getVerdict() { return verdict; }
    public Instant getCreatedAt() { return createdAt; }
    public String getReviewSource() { return reviewSource; }
    public Integer getReviewCount() { return reviewCount; }
}
