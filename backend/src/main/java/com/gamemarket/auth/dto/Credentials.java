package com.gamemarket.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Credentials(@NotBlank @Size(max = 50) String username, @NotBlank @Size(max = 100) String password) {
}
