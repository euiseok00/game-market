package com.gamemarket.game;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, Long> {

    boolean existsBySteamAppId(long steamAppId);
}
