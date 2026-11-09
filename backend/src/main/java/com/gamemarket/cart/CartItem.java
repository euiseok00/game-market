package com.gamemarket.cart;

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

@Entity
@Table(name = "cart_item", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "game_id"}))
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "game_id")
    private Game game;

    protected CartItem() {
    }

    public CartItem(Long userId, Game game) {
        this.userId = userId;
        this.game = game;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Game getGame() { return game; }
}
