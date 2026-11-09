package com.gamemarket.cart;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface CartRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUserIdOrderById(Long userId);

    boolean existsByUserIdAndGameId(Long userId, Long gameId);

    @Transactional
    long deleteByUserIdAndGameId(Long userId, Long gameId);

    @Transactional
    long deleteByUserId(Long userId);
}
