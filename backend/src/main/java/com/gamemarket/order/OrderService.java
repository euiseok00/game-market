package com.gamemarket.order;

import com.gamemarket.cart.CartItem;
import com.gamemarket.cart.CartRepository;
import com.gamemarket.game.GameDto;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {

    public record OrderDto(Long id, List<GameDto> items, int totalKrw, Instant createdAt) {

        static OrderDto of(Order o) {
            return new OrderDto(o.getId(), o.getItems().stream().map(i -> GameDto.of(i.getGame())).toList(),
                    o.getTotalKrw(), o.getCreatedAt());
        }
    }

    private final CartRepository cart;
    private final OrderRepository orders;
    private final OwnershipRepository ownership;

    public OrderService(CartRepository cart, OrderRepository orders, OwnershipRepository ownership) {
        this.cart = cart;
        this.orders = orders;
        this.ownership = ownership;
    }

    @Transactional
    public OrderDto checkout(Long userId) {
        List<CartItem> items = cart.findByUserIdOrderById(userId);
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cart is empty");
        }
        if (items.stream().anyMatch(i -> ownership.existsByUserIdAndGameId(userId, i.getGame().getId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already owned");
        }
        Order order = new Order(userId);
        items.forEach(i -> order.addItem(new OrderItem(order, i.getGame())));
        try {
            orders.saveAndFlush(order);
        } catch (DataIntegrityViolationException race) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already owned");
        }
        cart.deleteByUserId(userId);
        return OrderDto.of(order);
    }

    @Transactional(readOnly = true)
    public List<GameDto> library(Long userId) {
        return ownership.findByUserIdOrderByIdDesc(userId).stream().map(i -> GameDto.of(i.getGame())).toList();
    }

    public boolean owns(Long userId, Long gameId) {
        return ownership.existsByUserIdAndGameId(userId, gameId);
    }
}
