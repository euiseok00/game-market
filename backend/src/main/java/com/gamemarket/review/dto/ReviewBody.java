package com.gamemarket.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewBody(@NotBlank @Size(max = 2000) String content) {
}
