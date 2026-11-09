package com.gamemarket.review;

import java.util.List;

/** Review texts plus where they came from, so the UI can say whether a summary rests on real or seed data. */
public record Reviews(String source, List<String> texts) {

    public static final String STEAM = "steam";
    public static final String SEED = "seed";

    public static Reviews steam(List<String> texts) {
        return new Reviews(STEAM, texts);
    }

    public static Reviews seed(List<String> texts) {
        return new Reviews(SEED, texts);
    }
}
