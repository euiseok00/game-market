package com.gamemarket.review.dto;

import java.util.List;

public record ReviewsDto(String source, List<ReviewDto> reviews) {
}
