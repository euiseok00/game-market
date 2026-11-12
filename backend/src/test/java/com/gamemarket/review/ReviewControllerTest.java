package com.gamemarket.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gamemarket.ApiTestBase;
import com.gamemarket.game.Game;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.json.JsonMapper;

class ReviewControllerTest extends ApiTestBase {

    @Autowired
    JsonMapper json;

    @Autowired
    ReviewRepository reviews;

    Game game(long steamAppId) {
        return games.findAll().stream().filter(g -> g.getSteamAppId() == steamAppId).findFirst().orElseThrow();
    }

    static String body(String content) {
        return "{\"content\":\"" + content + "\"}";
    }

    long addReview(MockHttpSession session, Long gameId, String content) throws Exception {
        String res = mvc.perform(post("/api/games/{id}/reviews", gameId).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body(content)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).path("id").asLong();
    }

    @Test
    void anonymous_canReadSeedReviews() throws Exception {
        Game portal = game(620);
        mvc.perform(get("/api/games/{id}/reviews", portal.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("seed"))
                .andExpect(jsonPath("$.reviews", hasSize(8)))
                .andExpect(jsonPath("$.reviews[0].content").value(org.hamcrest.Matchers.containsString("포탈")))
                .andExpect(jsonPath("$.reviews[0].userId").doesNotExist());
    }

    @Test
    void unknownGame_returns404() throws Exception {
        mvc.perform(get("/api/games/{id}/reviews", 999_999)).andExpect(status().isNotFound());
    }

    @Test
    void anonymous_cannotAdd() throws Exception {
        mvc.perform(post("/api/games/{id}/reviews", game(620).getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body("hi")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void user_addsReview_thenEditsIt_keepingTheSameId() throws Exception {
        MockHttpSession session = newUserSession();
        Long gameId = game(730).getId();
        long id = addReview(session, gameId, "  처음 리뷰  ");
        assertThat(reviews.findById(id).orElseThrow().getContent()).isEqualTo("처음 리뷰");

        mvc.perform(put("/api/games/{id}/reviews/{rid}", gameId, id).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body("수정한 리뷰")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.content").value("수정한 리뷰"));
        assertThat(reviews.findById(id).orElseThrow().getContent()).isEqualTo("수정한 리뷰");
    }

    @Test
    void otherUser_cannotEdit_andNobodyCanEditSeedReviews() throws Exception {
        Long gameId = game(730).getId();
        long id = addReview(newUserSession(), gameId, "내 리뷰");
        MockHttpSession other = newUserSession();
        mvc.perform(put("/api/games/{id}/reviews/{rid}", gameId, id).session(other)
                        .contentType(MediaType.APPLICATION_JSON).content(body("탈취")))
                .andExpect(status().isForbidden());

        Long seedId = reviews.findByGameIdOrderById(gameId).get(0).getId();
        mvc.perform(put("/api/games/{id}/reviews/{rid}", gameId, seedId).session(other)
                        .contentType(MediaType.APPLICATION_JSON).content(body("탈취")))
                .andExpect(status().isForbidden());
        assertThat(reviews.findById(id).orElseThrow().getContent()).isEqualTo("내 리뷰");
    }

    @Test
    void edit_throughAnotherGamesUrl_returns404() throws Exception {
        MockHttpSession session = newUserSession();
        long id = addReview(session, game(730).getId(), "내 리뷰");
        mvc.perform(put("/api/games/{id}/reviews/{rid}", game(620).getId(), id).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body("x")))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankContent_returns400() throws Exception {
        mvc.perform(post("/api/games/{id}/reviews", game(620).getId()).session(newUserSession())
                        .contentType(MediaType.APPLICATION_JSON).content(body("   ")))
                .andExpect(status().isBadRequest());
    }
}
