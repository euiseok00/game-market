package com.gamemarket.game;

import com.gamemarket.game.dto.GameDto;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameRepository games;

    public GameController(GameRepository games) {
        this.games = games;
    }

    // shortcut: filters in memory over the 20-30 seed games, move to a DB query if the catalog grows.
    @GetMapping
    public List<GameDto> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) String genre) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return games.findAll(Sort.by("name")).stream()
                .filter(g -> needle.isEmpty() || g.getName().toLowerCase(Locale.ROOT).contains(needle))
                .filter(g -> genre == null || genre.isBlank() || g.getGenre().equals(genre))
                .map(GameDto::of)
                .toList();
    }

    @GetMapping("/{id}")
    public GameDto get(@PathVariable Long id) {
        return games.findById(id).map(GameDto::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
