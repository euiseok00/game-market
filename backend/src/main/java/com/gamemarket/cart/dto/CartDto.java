package com.gamemarket.cart.dto;

import com.gamemarket.game.dto.GameDto;
import java.util.List;

public record CartDto(List<GameDto> items, int totalKrw) {
}
