package com.gamemarket.game;

public record GameDto(Long id, long steamAppId, String name, String genre, int priceKrw, String imageUrl,
        String description) {

    public static GameDto of(Game g) {
        return new GameDto(g.getId(), g.getSteamAppId(), g.getName(), g.getGenre(), g.getPriceKrw(),
                g.getImageUrl(), g.getDescription());
    }
}
