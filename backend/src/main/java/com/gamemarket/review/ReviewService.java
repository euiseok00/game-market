package com.gamemarket.review;

import com.gamemarket.game.GameRepository;
import com.gamemarket.review.dto.ReviewDto;
import com.gamemarket.review.dto.ReviewsDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Lists, adds and edits reviews. Summaries pick up the changes on the next scheduled refresh. */
@Service
public class ReviewService {

    private final GameRepository games;
    private final ReviewRepository reviews;

    public ReviewService(GameRepository games, ReviewRepository reviews) {
        this.games = games;
        this.reviews = reviews;
    }

    public ReviewsDto list(Long gameId) {
        requireGame(gameId);
        return new ReviewsDto(Reviews.SEED, reviews.findByGameIdOrderById(gameId).stream().map(ReviewDto::of).toList());
    }

    public ReviewDto add(Long userId, Long gameId, String content) {
        requireGame(gameId);
        return ReviewDto.of(reviews.save(new Review(gameId, userId, content.strip())));
    }

    /** Only the author may edit; the review keeps its id so change detection sees an edit, not a new review. */
    @Transactional
    public ReviewDto edit(Long userId, Long gameId, Long reviewId, String content) {
        Review review = reviews.findById(reviewId)
                .filter(r -> r.getGameId().equals(gameId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!userId.equals(review.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        review.setContent(content.strip());
        return ReviewDto.of(review);
    }

    private void requireGame(Long gameId) {
        if (!games.existsById(gameId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
