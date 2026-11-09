package com.gamemarket.summary;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SummaryLogRepository extends JpaRepository<SummaryLog, Long> {

    List<SummaryLog> findByCreatedAtGreaterThanEqualOrderById(Instant since);
}
