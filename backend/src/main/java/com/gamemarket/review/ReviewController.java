package com.gamemarket.review;

import com.gamemarket.game.Game;
import com.gamemarket.game.GameRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.gamemarket.review.dto.ReviewsDto;

/** The reviews a summary is built from, so users can read them next to the AI summary. */
@RestController
public class ReviewController {

    private final GameRepository games;
    private final ReviewSource reviews;

    public ReviewController(GameRepository games, ReviewSource reviews) {
        this.games = games;
        this.reviews = reviews;
    }

    @GetMapping("/api/games/{id}/reviews")
    public ReviewsDto list(@PathVariable Long id) {
        Game game = games.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Reviews found = reviews.fetch(game.getSteamAppId());
        return new ReviewsDto(found.source(), found.texts());
    }
}
