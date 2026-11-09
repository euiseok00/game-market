package com.gamemarket.order;

import com.gamemarket.game.Game;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** One purchased game; the (user_id, game_id) unique key is the ownership record behind the library. */
@Entity
@Table(name = "order_item", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "game_id"}))
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "game_id")
    private Game game;

    private int priceKrw;

    protected OrderItem() {
    }

    OrderItem(Order order, Game game) {
        this.order = order;
        this.userId = order.getUserId();
        this.game = game;
        this.priceKrw = game.getPriceKrw();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Game getGame() { return game; }
    public int getPriceKrw() { return priceKrw; }
}
