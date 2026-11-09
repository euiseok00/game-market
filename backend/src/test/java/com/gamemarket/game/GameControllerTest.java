package com.gamemarket.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gamemarket.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class GameControllerTest extends IntegrationTestBase {

    @Autowired
    MockMvc mvc;

    @Autowired
    GameRepository games;

    @Test
    void searchByName_isCaseInsensitive() throws Exception {
        mvc.perform(get("/api/games").param("q", "PORTAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Portal 2")))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void filterByGenre() throws Exception {
        mvc.perform(get("/api/games").param("genre", "RPG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].genre", everyItem(is("RPG"))));
    }

    @Test
    void unknownId_returns404() throws Exception {
        mvc.perform(get("/api/games/{id}", 999_999)).andExpect(status().isNotFound());
    }

    @Test
    void seed_loadsBetween20And30Games() {
        assertThat(games.count()).isBetween(20L, 30L);
    }
}
