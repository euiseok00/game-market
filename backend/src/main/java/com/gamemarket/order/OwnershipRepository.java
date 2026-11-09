package com.gamemarket.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnershipRepository extends JpaRepository<OrderItem, Long> {

    boolean existsByUserIdAndGameId(Long userId, Long gameId);

    List<OrderItem> findByUserIdOrderByIdDesc(Long userId);
}
