package com.gamemarket.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private long steamAppId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String genre;

    private int priceKrw;

    @Column(length = 1000)
    private String imageUrl;

    @Column(length = 2000)
    private String description;

    protected Game() {
    }

    public Game(long steamAppId, String name, String genre, int priceKrw, String imageUrl, String description) {
        this.steamAppId = steamAppId;
        this.name = name;
        this.genre = genre;
        this.priceKrw = priceKrw;
        this.imageUrl = imageUrl;
        this.description = description;
    }

    public Long getId() { return id; }
    public long getSteamAppId() { return steamAppId; }
    public String getName() { return name; }
    public String getGenre() { return genre; }
    public int getPriceKrw() { return priceKrw; }
    public String getImageUrl() { return imageUrl; }
    public String getDescription() { return description; }
}
