package com.gamemarket.order.dto;

import com.gamemarket.game.dto.GameDto;
import com.gamemarket.order.Order;
import java.time.Instant;
import java.util.List;

public record OrderDto(Long id, List<GameDto> items, int totalKrw, Instant createdAt) {

    public static OrderDto of(Order o) {
        return new OrderDto(o.getId(), o.getItems().stream().map(i -> GameDto.of(i.getGame())).toList(),
                o.getTotalKrw(), o.getCreatedAt());
    }
}
