package com.gamemarket.cart;

import com.gamemarket.auth.AuthUser;
import com.gamemarket.game.Game;
import com.gamemarket.game.GameDto;
import com.gamemarket.game.GameRepository;
import com.gamemarket.order.OrderService;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    public record CartDto(List<GameDto> items, int totalKrw) {
    }

    private final CartRepository cart;
    private final GameRepository games;
    private final OrderService orders;

    public CartController(CartRepository cart, GameRepository games, OrderService orders) {
        this.cart = cart;
        this.games = games;
        this.orders = orders;
    }

    @GetMapping
    public CartDto get(@AuthenticationPrincipal AuthUser user) {
        List<GameDto> items = cart.findByUserIdOrderById(user.id()).stream()
                .map(i -> GameDto.of(i.getGame())).toList();
        return new CartDto(items, items.stream().mapToInt(GameDto::priceKrw).sum());
    }

    @PostMapping("/{gameId}")
    @ResponseStatus(HttpStatus.CREATED)
    public CartDto add(@AuthenticationPrincipal AuthUser user, @PathVariable Long gameId) {
        Game game = games.findById(gameId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (cart.existsByUserIdAndGameId(user.id(), gameId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already in cart");
        }
        if (orders.owns(user.id(), gameId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already owned");
        }
        try {
            cart.saveAndFlush(new CartItem(user.id(), game));
        } catch (DataIntegrityViolationException race) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already in cart");
        }
        return get(user);
    }

    @DeleteMapping("/{gameId}")
    public CartDto remove(@AuthenticationPrincipal AuthUser user, @PathVariable Long gameId) {
        cart.deleteByUserIdAndGameId(user.id(), gameId);
        return get(user);
    }
}
