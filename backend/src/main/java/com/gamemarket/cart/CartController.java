package com.gamemarket.cart;

import com.gamemarket.auth.AuthUser;
import com.gamemarket.cart.dto.CartDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping
    public CartDto get(@AuthenticationPrincipal AuthUser user) {
        return cart.get(user.id());
    }

    @PostMapping("/{gameId}")
    @ResponseStatus(HttpStatus.CREATED)
    public CartDto add(@AuthenticationPrincipal AuthUser user, @PathVariable Long gameId) {
        return cart.add(user.id(), gameId);
    }

    @DeleteMapping("/{gameId}")
    public CartDto remove(@AuthenticationPrincipal AuthUser user, @PathVariable Long gameId) {
        return cart.remove(user.id(), gameId);
    }
}
