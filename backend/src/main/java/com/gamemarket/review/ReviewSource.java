package com.gamemarket.review;

public interface ReviewSource {

    Reviews fetch(Long gameId);
}
