package com.gamemarket.summary;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SummaryRepository extends JpaRepository<ReviewSummary, Long> {

    Optional<ReviewSummary> findByGameId(Long gameId);
}
