package com.gamemarket.summary;

import java.util.Locale;

public enum SummaryMode {
    NONE, LAZY, WARM;

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
