package com.gamemarket.review;

import com.gamemarket.auth.AuthUser;
import com.gamemarket.review.dto.ReviewBody;
import com.gamemarket.review.dto.ReviewDto;
import com.gamemarket.review.dto.ReviewsDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The reviews a summary is built from, so users can read them next to the AI summary, and add or edit their own. */
@RestController
public class ReviewController {

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/api/games/{id}/reviews")
    public ReviewsDto list(@PathVariable Long id) {
        return reviews.list(id);
    }

    @PostMapping("/api/games/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewDto add(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
            @Valid @RequestBody ReviewBody body) {
        return reviews.add(user.id(), id, body.content());
    }

    @PutMapping("/api/games/{id}/reviews/{reviewId}")
    public ReviewDto edit(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @PathVariable Long reviewId,
            @Valid @RequestBody ReviewBody body) {
        return reviews.edit(user.id(), id, reviewId, body.content());
    }
}
