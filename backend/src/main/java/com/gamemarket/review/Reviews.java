package com.gamemarket.review;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Reviews plus where they came from, so the UI can say whether a summary rests on real or seed data. */
public record Reviews(String source, List<Item> items) {

    public static final String SEED = "seed";

    public record Item(long id, String text) {
    }

    public static Reviews seed(List<Item> items) {
        return new Reviews(SEED, items);
    }

    public List<String> texts() {
        return items.stream().map(Item::text).toList();
    }

    /**
     * SHA-256 over every (id, text) pair sorted by id. A new id, an edited text or a removed review all change
     * it, unlike a review count.
     */
    public String fingerprint() {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            items.stream().sorted(Comparator.comparingLong(Item::id)).forEach(i -> {
                // Length prefix keeps "1"+"2x" and "12"+"x" apart.
                sha.update((i.id() + ":" + i.text().length() + ":").getBytes(StandardCharsets.UTF_8));
                sha.update(i.text().getBytes(StandardCharsets.UTF_8));
            });
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
