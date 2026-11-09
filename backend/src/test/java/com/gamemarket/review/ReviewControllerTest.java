package com.gamemarket.review;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gamemarket.ApiTestBase;
import com.gamemarket.game.Game;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class ReviewControllerTest extends ApiTestBase {

    @MockitoBean
    SteamReviewSource steam; // keep tests off the network; empty Steam result falls back to seed

    @BeforeEach
    void steamHasNothing() {
        when(steam.fetch(anyLong())).thenReturn(Reviews.steam(List.of()));
    }

    @Test
    void anonymous_canReadSeedReviews() throws Exception {
        Game portal = games.findAll().stream().filter(g -> g.getSteamAppId() == 620).findFirst().orElseThrow();
        mvc.perform(get("/api/games/{id}/reviews", portal.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("seed"))
                .andExpect(jsonPath("$.reviews", hasSize(8)))
                .andExpect(jsonPath("$.reviews[0]").value(org.hamcrest.Matchers.containsString("포탈")));
    }

    @Test
    void unknownGame_returns404() throws Exception {
        mvc.perform(get("/api/games/{id}/reviews", 999_999)).andExpect(status().isNotFound());
    }
}
