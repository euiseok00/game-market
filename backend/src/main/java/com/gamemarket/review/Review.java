package com.gamemarket.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** One review. The id is the review id that change detection keys on; content may be edited in place. */
@Entity
@Table(name = "review", indexes = @Index(columnList = "game_id"))
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    // Author; null for seed reviews, which nobody can edit.
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    protected Review() {
    }

    public Review(Long gameId, String content) {
        this(gameId, null, content);
    }

    public Review(Long gameId, Long userId, String content) {
        this.gameId = gameId;
        this.userId = userId;
        this.content = content;
    }

    public Long getId() { return id; }
    public Long getGameId() { return gameId; }
    public Long getUserId() { return userId; }
    public String getContent() { return content; }

    public void setContent(String content) {
        this.content = content;
    }
}
