package com.gamemarket.cart;

import com.gamemarket.cart.dto.CartDto;
import com.gamemarket.game.Game;
import com.gamemarket.game.GameRepository;
import com.gamemarket.game.dto.GameDto;
import com.gamemarket.order.OrderService;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CartService {

    private final CartRepository cart;
    private final GameRepository games;
    private final OrderService orders;

    public CartService(CartRepository cart, GameRepository games, OrderService orders) {
        this.cart = cart;
        this.games = games;
        this.orders = orders;
    }

    public CartDto get(Long userId) {
        List<GameDto> items = cart.findByUserIdOrderById(userId).stream()
                .map(i -> GameDto.of(i.getGame())).toList();
        return new CartDto(items, items.stream().mapToInt(GameDto::priceKrw).sum());
    }

    /** 404 for an unknown game, 409 when it is already in the cart or already owned. */
    public CartDto add(Long userId, Long gameId) {
        Game game = games.findById(gameId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (cart.existsByUserIdAndGameId(userId, gameId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already in cart");
        }
        if (orders.owns(userId, gameId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already owned");
        }
        try {
            cart.saveAndFlush(new CartItem(userId, game));
        } catch (DataIntegrityViolationException race) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already in cart");
        }
        return get(userId);
    }

    public CartDto remove(Long userId, Long gameId) {
        cart.deleteByUserIdAndGameId(userId, gameId);
        return get(userId);
    }
}
