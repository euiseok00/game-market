package com.gamemarket.order;

import com.gamemarket.auth.AuthUser;
import com.gamemarket.game.GameDto;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LibraryController {

    private final OrderService orders;

    public LibraryController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/api/library")
    public List<GameDto> library(@AuthenticationPrincipal AuthUser user) {
        return orders.library(user.id());
    }
}
