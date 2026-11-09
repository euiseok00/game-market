package com.gamemarket.cart;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gamemarket.ApiTestBase;
import com.gamemarket.game.Game;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class CartControllerTest extends ApiTestBase {

    @Test
    void add_twice_returns409() throws Exception {
        MockHttpSession s = newUserSession();
        Game g = paidGames().get(0);
        mvc.perform(post("/api/cart/{id}", g.getId()).session(s)).andExpect(status().isCreated());
        mvc.perform(post("/api/cart/{id}", g.getId()).session(s)).andExpect(status().isConflict());
    }

    @Test
    void total_sumsPrices() throws Exception {
        MockHttpSession s = newUserSession();
        Game a = paidGames().get(0);
        Game b = paidGames().get(1);
        mvc.perform(post("/api/cart/{id}", a.getId()).session(s)).andExpect(status().isCreated());
        mvc.perform(post("/api/cart/{id}", b.getId()).session(s)).andExpect(status().isCreated());
        mvc.perform(get("/api/cart").session(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalKrw", is(a.getPriceKrw() + b.getPriceKrw())));
    }

    @Test
    void remove_updatesTotal() throws Exception {
        MockHttpSession s = newUserSession();
        Game a = paidGames().get(0);
        Game b = paidGames().get(1);
        mvc.perform(post("/api/cart/{id}", a.getId()).session(s)).andExpect(status().isCreated());
        mvc.perform(post("/api/cart/{id}", b.getId()).session(s)).andExpect(status().isCreated());
        mvc.perform(delete("/api/cart/{id}", a.getId()).session(s)).andExpect(status().isOk());
        mvc.perform(get("/api/cart").session(s))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.totalKrw", is(b.getPriceKrw())));
    }

    @Test
    void add_unknownGame_returns404() throws Exception {
        mvc.perform(post("/api/cart/{id}", 999_999).session(newUserSession())).andExpect(status().isNotFound());
    }
}
