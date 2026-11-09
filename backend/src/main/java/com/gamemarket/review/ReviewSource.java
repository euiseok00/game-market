package com.gamemarket.review;

import java.util.List;

public interface ReviewSource {

    List<String> fetch(long steamAppId);
}
